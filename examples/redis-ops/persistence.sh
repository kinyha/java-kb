#!/usr/bin/env bash
# Персистентность и репликация Redis 8 в Docker. Запуск: ./persistence.sh
set -u
IMG=redis:8-alpine
cli() { docker exec "$1" redis-cli "${@:2}"; }
cleanup() { docker rm -f r-none r-aof r-master r-replica >/dev/null 2>&1; docker network rm r-net >/dev/null 2>&1; }
cleanup

echo "== persistence: restart after 3 writes"
docker run -d --name r-none $IMG redis-server --save "" --appendonly no >/dev/null
docker run -d --name r-aof  $IMG redis-server --save "" --appendonly yes --appendfsync everysec >/dev/null
sleep 1
for c in r-none r-aof; do for i in 1 2 3; do cli $c SET payment:p-$i CAPTURED >/dev/null; done; done
sleep 1.5                                  # appendfsync everysec: дать fsync случиться.
docker restart r-none r-aof >/dev/null; sleep 1.5
echo "no persistence: dbsize=$(cli r-none DBSIZE)"
echo "AOF everysec:   dbsize=$(cli r-aof DBSIZE)"

echo "== async replication and failover"
docker network create r-net >/dev/null
docker run -d --name r-master  --network r-net $IMG redis-server --save "" >/dev/null
docker run -d --name r-replica --network r-net $IMG redis-server --save "" --replicaof r-master 6379 >/dev/null
until cli r-replica INFO replication 2>/dev/null | grep -q master_link_status:up; do sleep 0.5; done
# WAIT ждёт реплик для записей своего соединения, поэтому SET и WAIT — в одном сеансе redis-cli.
echo "SET 100 + WAIT 1 replica 1000 ms -> $(printf 'SET balance:acc-1 100\nWAIT 1 1000\n' | docker exec -i r-master redis-cli | tr '\n' ' ')"
echo "replica sees balance: $(cli r-replica GET balance:acc-1)"
docker network disconnect r-net r-replica   # Сетевой разрыв между мастером и репликой.
echo "SET 40  + WAIT 1 replica 500 ms  -> $(printf 'SET balance:acc-1 40\nWAIT 1 500\n' | docker exec -i r-master redis-cli | tr '\n' ' ')"
docker kill r-master >/dev/null             # Мастер умер до того, как реплика получила запись.
docker network connect r-net r-replica
cli r-replica REPLICAOF NO ONE >/dev/null   # Failover: реплика становится мастером.
echo "after failover balance: $(cli r-replica GET balance:acc-1)"
cleanup
