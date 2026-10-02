#!/usr/bin/env bash
# Кластер Cassandra 5.0 из трёх узлов в Docker (куча по 512 МБ). Узлы стартуют по очереди.
set -eu
docker rm -f cas-1 cas-2 cas-3 >/dev/null 2>&1 || true
docker network rm cas-net >/dev/null 2>&1 || true
docker network create cas-net >/dev/null
for n in 1 2 3; do
  docker run -d --name cas-$n --network cas-net \
    -e CASSANDRA_SEEDS=cas-1 -e CASSANDRA_CLUSTER_NAME=kb -e CASSANDRA_DC=dc1 \
    -e CASSANDRA_ENDPOINT_SNITCH=GossipingPropertyFileSnitch \
    -e MAX_HEAP_SIZE=512M -e HEAP_NEWSIZE=128M cassandra:5.0 >/dev/null
  for i in $(seq 1 120); do
    docker exec cas-$n nodetool status 2>/dev/null | grep -q "^UN.*$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cas-$n)" && break
    sleep 2
  done
done
docker exec cas-1 nodetool status | grep -E "^(UN|DN|UJ)"
