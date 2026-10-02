#!/usr/bin/env bash
# Primary и потоковая реплика PostgreSQL 17. Primary — localhost:55441, реплика — localhost:55442.
set -eu
IMG=postgres:17-alpine
docker rm -f pg-primary pg-replica >/dev/null 2>&1 || true
docker network rm pg-repl >/dev/null 2>&1 || true
docker network create pg-repl >/dev/null
docker run -d --name pg-primary -p 55441:5432 -e POSTGRES_PASSWORD=pw $IMG \
  -c wal_level=replica -c max_wal_senders=5 -c synchronous_standby_names='replica1' -c synchronous_commit=local >/dev/null
# Порты публикуются через сеть по умолчанию, репликация идёт по pg-repl: её можно разорвать, не теряя портов.
docker network connect pg-repl pg-primary
until docker exec pg-primary pg_isready -U postgres >/dev/null 2>&1; do sleep 0.5; done
sleep 1
docker exec pg-primary psql -U postgres -qc "create role repl with replication login password 'pw'"
docker exec pg-primary sh -c "echo 'host replication repl all md5' >> /var/lib/postgresql/data/pg_hba.conf"
docker exec pg-primary psql -U postgres -qc "select pg_reload_conf()" >/dev/null
# Реплика: копия данных primary через pg_basebackup; -R пишет настройки подключения к primary.
docker create --name pg-replica -p 55442:5432 -e PGPASSWORD=pw --user postgres --entrypoint sh $IMG -c "
  pg_basebackup -h pg-primary -U repl -D /var/lib/postgresql/data -R -X stream &&
  echo \"primary_conninfo = 'host=pg-primary user=repl password=pw application_name=replica1'\" >> /var/lib/postgresql/data/postgresql.auto.conf &&
  chmod 700 /var/lib/postgresql/data &&
  exec postgres -c hot_standby=on" >/dev/null
docker network connect pg-repl pg-replica
docker start pg-replica >/dev/null
for i in $(seq 1 60); do docker exec pg-replica pg_isready -U postgres >/dev/null 2>&1 && break; sleep 0.5; done
sleep 2
docker exec pg-primary psql -U postgres -Atc "select application_name, state, sync_state from pg_stat_replication"
