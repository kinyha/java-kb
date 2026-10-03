#!/usr/bin/env bash
# API gateway на nginx 1.31 перед сервисами в Docker-сети; обнаружение экземпляров через DNS Docker.
set -u
cd "$(dirname "$0")"
IMG=nginx:1.31-alpine
cleanup() { docker rm -f gw auth orders-1 orders-2 orders-3 users-1 users-2 >/dev/null 2>&1; docker network rm gw-net >/dev/null 2>&1; }
cleanup
docker network create gw-net >/dev/null
svc() {   # $1 — имя контейнера, $2 — сетевой псевдоним (имя сервиса)
  docker run -d --name $1 --hostname $1 --network gw-net --network-alias $2 \
    -v "$PWD/backend.conf":/etc/nginx/nginx.conf:ro $IMG >/dev/null
}
svc auth auth; svc orders-1 orders; svc orders-2 orders; svc users-1 users
sleep 1
docker run -d --name gw --network gw-net -p 8088:8080 -v "$PWD/gateway.conf":/etc/nginx/nginx.conf:ro $IMG >/dev/null
sleep 1
req() { curl -s -o /tmp/gw.body -w "%{http_code}" "$@" ; }
show() { local code; code=$(req "${@:2}"); printf "   %-34s %s %s\n" "$1" "$code" "$(head -c 80 /tmp/gw.body | tr -d '\r\n' | sed -E 's/<[^>]*>//g' | sed -E 's/ +/ /g; s/req=[0-9a-f]{32}/req=<32 hex>/')"; }

echo "== routing and authentication at the gateway"
show "GET /orders/42, no token"     http://localhost:8088/orders/42
show "GET /orders/42, bad token"    -H "Authorization: Bearer bad" http://localhost:8088/orders/42
show "GET /orders/42, good token"   -H "Authorization: Bearer good" http://localhost:8088/orders/42
show "GET /users/7, good token"     -H "Authorization: Bearer good" http://localhost:8088/users/7

echo "== rate limit 5 r/s, burst 2: 10 requests at once"
sleep 1                                              # ведро после предыдущих запросов пустеет
codes=""; for i in $(seq 10); do codes="$codes $(req -H 'Authorization: Bearer good' http://localhost:8088/orders/1)"; done
echo "  $codes"

echo "== cost of auth_request: 300 sequential requests"
timeit() { python3 - "$@" <<'PY'
import sys, time, urllib.request
url, auth = sys.argv[1], sys.argv[2] == "auth"
req = urllib.request.Request(url, headers={"Authorization": "Bearer good"} if auth else {})
for _ in range(50): urllib.request.urlopen(req).read()
t = time.perf_counter()
for _ in range(300): urllib.request.urlopen(req).read()
print(f"{(time.perf_counter() - t) / 300 * 1000:.2f} ms per request")
PY
}
echo "   /plain/ (no auth_request): $(timeit http://localhost:8088/plain/1 none)"
echo "   /users/ (auth_request):    $(timeit http://localhost:8088/users/1 auth)"

echo "== discovery: start orders-3 and users-2, then send 30 requests to each"
svc orders-3 orders; svc users-2 users
sleep 3
count() { for i in $(seq 30); do sleep 0.25; curl -s -H 'Authorization: Bearer good' http://localhost:8088/$1/1 | cut -d' ' -f1; done | sort | uniq -c | awk '{printf "%s=%s ", $2, $1}'; }
echo "   /orders/ (name in a variable, resolver valid=1s): $(count orders)"
echo "   /users/  (name resolved at nginx start):         $(count users)"
cleanup
