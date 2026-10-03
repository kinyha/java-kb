#!/usr/bin/env bash
# Jaeger 2 all-in-one: OTLP/HTTP на 4318, API запросов на 16686.
set -euo pipefail
cd "$(dirname "$0")"
docker rm -f kb-jaeger >/dev/null 2>&1 || true
docker run -d --name kb-jaeger -p 4318:4318 -p 16686:16686 jaegertracing/jaeger:2.10.0 >/dev/null
sleep 3
java -cp "$(cat target/classpath.txt)" TracingDemo.java
docker rm -f kb-jaeger >/dev/null
