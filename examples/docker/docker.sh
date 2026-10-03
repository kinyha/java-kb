#!/usr/bin/env bash
# Опыты с образами и контейнерами для Java-сервиса.
set -u
cd "$(dirname "$0")"
b() { docker build -q -f "$1" -t "$2" . >/dev/null; }
size() { docker image inspect "$1" --format '{{.Size}}' | awk '{printf "%d MB", $1/1000000}'; }

echo "== image size"
b Dockerfile.jdk kb-docker-jdk; b Dockerfile.multistage kb-docker-multistage; b Dockerfile.jlink kb-docker-jlink
echo "   single stage on JDK:   $(size kb-docker-jdk)"
echo "   multi-stage, JRE:      $(size kb-docker-multistage)"
echo "   multi-stage, jlink:    $(size kb-docker-jlink)"

echo "== layer cache: 40 MB of dependencies, then a change in the app"
mkdir -p libs; [ -f libs/deps.jar ] || head -c 40000000 /dev/urandom > libs/deps.jar
b Dockerfile.layers kb-docker-layers
before=$(docker image inspect kb-docker-layers --format '{{range .RootFS.Layers}}{{println .}}{{end}}')
cp app/App.java /tmp/App.java.bak
sed -i '' 's/draining and stopping/draining and stopping (v2)/' app/App.java      # изменение кода, а не комментария
b Dockerfile.multistage kb-docker-multistage; b Dockerfile.layers kb-docker-layers
after=$(docker image inspect kb-docker-layers --format '{{range .RootFS.Layers}}{{println .}}{{end}}')
cp /tmp/App.java.bak app/App.java
echo "   layers total: $(echo "$after" | grep -c .), changed after rebuild: $(comm -13 <(echo "$before" | sort) <(echo "$after" | sort) | grep -c .)"
docker history kb-docker-layers --format '{{.Size}}\t{{.CreatedBy}}' | grep -E 'COPY' | sed -E 's/ *# buildkit//; s/^/   /' | cut -c1-70

echo "== what the JVM sees in a container"
run() { docker run --rm "$@" kb-docker-multistage sh -c 'timeout 2 java App 2>/dev/null | head -1'; }
echo "   no limits:                         $(run)"
echo "   --memory=512m --cpus=2:            $(run --memory=512m --cpus=2)"
echo "   same + -XX:MaxRAMPercentage=75:    $(docker run --rm --memory=512m --cpus=2 -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 kb-docker-multistage sh -c 'timeout 2 java App 2>/dev/null | head -1')"
echo "   cgroup memory.max inside:          $(docker run --rm --memory=512m kb-docker-multistage cat /sys/fs/cgroup/memory.max)"

echo "== docker stop -t 10: who is PID 1"
b Dockerfile.shellform kb-docker-shell
docker build -q -f Dockerfile.script --build-arg SCRIPT=start-noexec.sh -t kb-docker-noexec . >/dev/null
docker build -q -f Dockerfile.script --build-arg SCRIPT=start-exec.sh -t kb-docker-exec . >/dev/null
for img in kb-docker-multistage kb-docker-shell kb-docker-noexec kb-docker-exec; do
  docker run -d --name kb-stop $img >/dev/null; sleep 2
  pid1=$(docker exec kb-stop sh -c 'cat /proc/1/comm')
  t0=$(python3 -c 'import time; print(time.time())'); docker stop -t 10 kb-stop >/dev/null; t1=$(python3 -c 'import time; print(time.time())')
  code=$(docker inspect kb-stop --format '{{.State.ExitCode}}')
  hook=$(docker logs kb-stop 2>&1 | grep -c SIGTERM)
  printf "   %-22s PID 1 = %-5s stop took %4.1f s, exit code %s, shutdown hook ran: %s\n" "$img" "$pid1" "$(python3 -c "print($t1-$t0)")" "$code" "$([ "$hook" -gt 0 ] && echo yes || echo no)"
  docker rm kb-stop >/dev/null
done
