#!/usr/bin/env bash
# Нагрузочные опыты: k6 1.3 в Docker против Service.java на хосте.
set -u
cd "$(dirname "$0")"
IP=$(docker run --rm alpine getent ahostsv4 host.docker.internal | awk 'NR==1{print $1}')
java Service.java > /dev/null & SVC=$!
trap 'kill $SVC 2>/dev/null' EXIT
sleep 2
k6() {   # $1 скрипт, далее переменные окружения
  local script=$1; shift
  local envs=(); for e in "$@"; do envs+=(-e "$e"); done
  docker run --rm --add-host target:"$IP" -v "$PWD":/s grafana/k6:1.3.0 run --quiet --summary-trend-stats "med,p(99)" --summary-export /s/summary.json "${envs[@]}" /s/$script >/dev/null 2>&1
  python3 - <<'PY'
import json
m = json.load(open('summary.json'))['metrics']
d = m['http_req_duration']
rate = m['http_reqs']['rate']
dropped = m.get('dropped_iterations', {}).get('count', 0)
print(f"{rate:6.0f} req/s   p50 {d['med']:6.0f} ms   p99 {d['p(99)']:6.0f} ms   dropped {dropped:.0f}")
PY
}
echo "== open model: arrival rate vs latency (capacity ~400 req/s)"
for r in 200 300 380 420 500; do printf "   rate %3s/s -> " $r; k6 open.js RATE=$r; done

echo "== a 2 s stall at second 10: closed model (10 users) vs open model (200 req/s)"
( sleep 10; curl -s -X POST "localhost:8601/stall?ms=2000" ) & ST=$!; printf "   closed, 10 VUs   -> "; k6 closed.js VUS=10; wait $ST
( sleep 10; curl -s -X POST "localhost:8601/stall?ms=2000" ) & ST=$!; printf "   open, 200 req/s  -> "; k6 open.js RATE=200; wait $ST
rm -f summary.json
