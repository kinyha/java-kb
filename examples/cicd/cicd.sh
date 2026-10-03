#!/usr/bin/env bash
# Опыты конвейера на копии examples/testing-strategy: кеш зависимостей, воспроизводимая сборка, продвижение образа.
set -u
SRC="$(cd "$(dirname "$0")/../testing-strategy" && pwd)"
W=$(mktemp -d); cp -R "$SRC/pom.xml" "$SRC/src" "$W/"; cd "$W"
secs() { python3 -c "import time; print(time.time())"; }

echo "== mvn verify (compile + unit tests): cold vs warm dependency cache"
t0=$(secs); mvn -q -B -Dmaven.repo.local="$W/cold-repo" verify >/dev/null 2>&1; t1=$(secs)
files=$(find "$W/cold-repo" -name '*.jar' | wc -l | tr -d ' '); size=$(du -sm "$W/cold-repo" | cut -f1)
t2=$(secs); mvn -q -B -Dmaven.repo.local="$W/cold-repo" verify >/dev/null 2>&1; t3=$(secs)
printf "   empty cache:  %5.1f s, downloaded %s jars, %s MB\n" "$(python3 -c "print($t1-$t0)")" "$files" "$size"
printf "   warm cache:   %5.1f s\n" "$(python3 -c "print($t3-$t2)")"

jar() { mvn -q -B -Dmaven.repo.local="$W/cold-repo" clean package -DskipTests "$@" >/dev/null 2>&1; shasum -a 256 target/*.jar | cut -c1-12; }
echo "== the same commit built twice"
a=$(jar); sleep 2; b=$(jar)
echo "   default:                   $a vs $b -> $([ "$a" = "$b" ] && echo identical || echo different)"
a=$(jar -Dproject.build.outputTimestamp=2026-10-01T00:00:00Z); sleep 2; b=$(jar -Dproject.build.outputTimestamp=2026-10-01T00:00:00Z)
echo "   with outputTimestamp:      $a vs $b -> $([ "$a" = "$b" ] && echo identical || echo different)"

echo "== build once, promote the same image"
printf 'FROM eclipse-temurin:21-jre-alpine\nCOPY target/*.jar /app/app.jar\n' > Dockerfile
docker build -q -t kb-ci:sha-1a2b3c . >/dev/null
docker tag kb-ci:sha-1a2b3c kb-ci:staging; docker tag kb-ci:sha-1a2b3c kb-ci:prod
for t in sha-1a2b3c staging prod; do printf "   kb-ci:%-12s %s\n" "$t" "$(docker image inspect kb-ci:$t --format '{{.Id}}' | cut -c8-19)"; done
jar >/dev/null; docker build -q --no-cache -t kb-ci:rebuilt . >/dev/null
printf "   rebuilt for prod: %s (another artifact than the one tested)\n" "$(docker image inspect kb-ci:rebuilt --format '{{.Id}}' | cut -c8-19)"
docker rmi -f kb-ci:sha-1a2b3c kb-ci:staging kb-ci:prod kb-ci:rebuilt >/dev/null
rm -rf "$W"
