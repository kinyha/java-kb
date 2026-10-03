#!/usr/bin/env bash
# nginx 1.31 в Docker перед тремя бэкендами на хосте, затем демо.
# Адрес хоста берём как IPv4: в Docker Desktop host-gateway может оказаться IPv6, недоступным из контейнера.
set -euo pipefail
cd "$(dirname "$0")"
docker rm -f kb-lb >/dev/null 2>&1 || true
IP=$(docker run --rm alpine getent ahostsv4 host.docker.internal | awk 'NR==1{print $1}')
docker run -d --name kb-lb -p 8081-8084:8081-8084 --add-host backend:"$IP" \
  -v "$PWD/nginx.conf":/etc/nginx/nginx.conf:ro nginx:1.31-alpine >/dev/null
sleep 1
java LoadBalancingDemo.java
docker rm -f kb-lb >/dev/null
