#!/usr/bin/env bash
# Prometheus 3.6 в Docker опрашивает два экземпляра на хосте раз в секунду.
set -euo pipefail
cd "$(dirname "$0")"
docker rm -f kb-prom >/dev/null 2>&1 || true
IP=$(docker run --rm alpine getent ahostsv4 host.docker.internal | awk 'NR==1{print $1}')
docker run -d --name kb-prom -p 9090:9090 --add-host host:"$IP" \
  -v "$PWD/prometheus.yml":/etc/prometheus/prometheus.yml:ro prom/prometheus:v3.6.0 >/dev/null
sleep 2
java -cp "$(cat target/classpath.txt)" MetricsDemo.java
docker rm -f kb-prom >/dev/null
