#!/usr/bin/env bash
# Ambassador / sidecar: два контейнера в одном сетевом пространстве, как контейнеры одного пода.
set -u
cd "$(dirname "$0")"
IMG=nginx:1.31-alpine
cleanup() { docker rm -f app ambassador rates >/dev/null 2>&1; docker network rm cp-net >/dev/null 2>&1; }
cleanup
docker network create cp-net >/dev/null
docker run -d --name rates --network cp-net -v "$PWD/rates.conf":/etc/nginx/nginx.conf:ro $IMG >/dev/null
# Приложение — alpine с curl; ничего не знает о повторах.
docker run -d --name app --network cp-net alpine sleep 600 >/dev/null
docker exec app apk add -q curl >/dev/null 2>&1
# Сайдкар разделяет сетевое пространство app: для приложения он — localhost:9000.
docker run -d --name ambassador --network container:app -v "$PWD/ambassador.conf":/etc/nginx/nginx.conf:ro $IMG >/dev/null
sleep 1
calls() { docker exec app sh -c "for i in \$(seq 200); do curl -s -o /dev/null -w '%{http_code}\n' $1; done" | sort | uniq -c | awk '{printf "%s x%s  ", $2, $1}'; }
echo "== the app calls the rates service 200 times (about 30% of responses are 503)"
echo "   directly, http://rates:8080:            $(calls http://rates:8080/eur)"
echo "   via ambassador, http://localhost:9000:  $(calls http://localhost:9000/eur)"
echo "== same network namespace"
echo "   inside the app container, port 9000 is open: $(docker exec app netstat -tln 2>/dev/null | awk '/LISTEN/ {print $4}' | grep ':9000$')"
echo "   app container runs: $(docker exec app ps -o comm= | sort -u | tr '\n' ' ')"
cleanup
