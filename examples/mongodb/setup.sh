#!/usr/bin/env bash
# Реплика-сет MongoDB 8.2 из трёх узлов. С хоста — прямое подключение к mongo-1 на localhost:27117.
set -eu
docker rm -f mongo-1 mongo-2 mongo-3 >/dev/null 2>&1 || true
docker network rm mongo-net >/dev/null 2>&1 || true
docker network create mongo-net >/dev/null
docker run -d --name mongo-1 --network mongo-net -p 27117:27017 mongo:8.2 --replSet rs0 --bind_ip_all >/dev/null
docker run -d --name mongo-2 --network mongo-net mongo:8.2 --replSet rs0 --bind_ip_all >/dev/null
docker run -d --name mongo-3 --network mongo-net mongo:8.2 --replSet rs0 --bind_ip_all >/dev/null
for i in $(seq 1 60); do docker exec mongo-1 mongosh --quiet --eval 'db.runCommand({ping:1}).ok' >/dev/null 2>&1 && break; sleep 0.5; done
docker exec mongo-1 mongosh --quiet --eval '
  rs.initiate({_id: "rs0", members: [
    {_id: 0, host: "mongo-1:27017", priority: 2},
    {_id: 1, host: "mongo-2:27017"},
    {_id: 2, host: "mongo-3:27017"}]})' >/dev/null
for i in $(seq 1 60); do [ "$(docker exec mongo-1 mongosh --quiet --eval 'db.hello().isWritablePrimary')" = "true" ] && break; sleep 0.5; done
docker exec mongo-1 mongosh --quiet --eval 'rs.status().members.map(m => m.name + " " + m.stateStr).join(", ")'
