#!/usr/bin/env bash
# Какие алерты срабатывают в трёх сценариях: promtool test rules на синтетических рядах (Prometheus 3.6).
# 1000 успешных запросов в минуту; инциденты начинаются на 25-м часу, чтобы окно в сутки было заполнено.
set -u
cd "$(dirname "$0")"
python3 gen.py probe > tests.yml
docker run --rm -v "$PWD":/w -w /w --entrypoint promtool prom/prometheus:v3.6.0 test rules tests.yml > probe.out 2>&1
python3 gen.py table probe.out
python3 gen.py expect probe.out > tests.yml          # те же моменты, но с ожиданиями = найденному
printf "   promtool with exact expectations: "
docker run --rm -v "$PWD":/w -w /w --entrypoint promtool prom/prometheus:v3.6.0 test rules tests.yml 2>&1 | grep -E 'SUCCESS|FAILED' | head -1 | tr -d ' '
rm -f probe.out tests.yml
