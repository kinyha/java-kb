#!/usr/bin/env bash
# Сколько вставок операций в секунду выдерживает один узел PostgreSQL 17.
# Нужен контейнер kb-pg (см. README). Результат зависит от машины и диска.
set -euo pipefail
cd "$(dirname "$0")"
docker exec kb-pg psql -U postgres -qc "drop table if exists op; create table op(card_id bigint, at timestamptz, op_id text, amount bigint, status text, merchant text, primary key (card_id, at, op_id))"
docker cp one.sql kb-pg:/tmp/one.sql
docker cp batch.sql kb-pg:/tmp/batch.sql
for f in one batch; do
  docker exec kb-pg pgbench -U postgres -n -c 8 -j 4 -T 15 -f /tmp/$f.sql postgres 2>&1 | grep -E "tps|latency average"
done
