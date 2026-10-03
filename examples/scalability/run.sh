#!/usr/bin/env bash
# Оба демо масштабирования: конкуренция за блокировку и сессии за балансировщиком.
set -euo pipefail
cd "$(dirname "$0")"
java ContentionDemo.java
docker rm -f kb-scale-lb >/dev/null 2>&1 || true
IP=$(docker run --rm alpine getent ahostsv4 host.docker.internal | awk 'NR==1{print $1}')
docker run -d --name kb-scale-lb -p 8086-8087:8086-8087 --add-host backend:"$IP" \
  -v "$PWD/nginx.conf":/etc/nginx/nginx.conf:ro nginx:1.31-alpine >/dev/null
sleep 1
java -cp "$(cat target/classpath.txt)" SessionDemo.java
docker rm -f kb-scale-lb >/dev/null
