#!/usr/bin/env bash
# Redis Cluster из трёх мастеров (без реплик, только чтобы увидеть слоты). Запуск: ./cluster.sh
set -u
IMG=redis:8-alpine
cleanup() { docker rm -f rc-1 rc-2 rc-3 >/dev/null 2>&1; docker network rm rc-net >/dev/null 2>&1; }
cleanup
docker network create rc-net >/dev/null
for i in 1 2 3; do
  docker run -d --name rc-$i --network rc-net $IMG redis-server --cluster-enabled yes --save "" >/dev/null
done
sleep 1
ips=$(for i in 1 2 3; do docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' rc-$i; done)
docker exec rc-1 redis-cli --cluster create $(for ip in $ips; do printf '%s:6379 ' $ip; done) --cluster-yes >/dev/null
until docker exec rc-1 redis-cli CLUSTER INFO | grep -q cluster_state:ok; do sleep 0.5; done

echo "== slots per master"
docker exec rc-1 redis-cli CLUSTER SHARDS | awk '/^slots$/{getline a; getline b; printf "slots %s-%s\n", a, b}'
echo "== key -> slot"
for k in payment:p-1 payment:p-2 '{m-7}:limit' '{m-7}:tariff'; do
  echo "$k -> $(docker exec rc-1 redis-cli CLUSTER KEYSLOT "$k")"
done
echo "== client without cluster support"
docker exec rc-1 redis-cli SET payment:p-2 CAPTURED
echo "== cluster-aware client (-c) follows MOVED"
docker exec rc-1 redis-cli -c SET payment:p-2 CAPTURED
echo "== multi-key command across slots"
docker exec rc-1 redis-cli -c MSET payment:p-1 A payment:p-2 B
echo "== same hash tag, same slot"
docker exec rc-1 redis-cli -c MSET '{m-7}:limit' 100 '{m-7}:tariff' 2.0%
cleanup
