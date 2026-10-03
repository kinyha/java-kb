#!/usr/bin/env bash
# Java в Kubernetes на k3s 1.34: память JVM и лимиты, пробы, корректная остановка при выкатке.
set -u
cd "$(dirname "$0")"
K="docker exec -i kb-k3s kubectl"
if ! docker ps --format '{{.Names}}' | grep -q '^kb-k3s$'; then
  docker run -d --name kb-k3s --privileged rancher/k3s:v1.34.1-k3s1 server --disable traefik --disable metrics-server >/dev/null
  until $K get nodes 2>/dev/null | grep -q ' Ready'; do sleep 2; done
fi
docker build -q -t kb-k8s-java:1 . >/dev/null
docker save kb-k8s-java:1 | docker exec -i kb-k3s ctr images import - >/dev/null
$K delete deploy,svc,cm,pod --all --now >/dev/null 2>&1

echo "== memory: limit 256Mi, the app fills 300 MB of heap"
mem() {   # $1 — имя, $2 — MaxRAMPercentage
$K apply -f - >/dev/null <<YAML
apiVersion: v1
kind: Pod
metadata: { name: $1 }
spec:
  restartPolicy: Never
  containers:
    - name: app
      image: kb-k8s-java:1
      imagePullPolicy: Never
      env:
        - { name: ALLOC_MB, value: "300" }
        - { name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=$2" }
      resources: { requests: { memory: 256Mi }, limits: { memory: 256Mi } }
YAML
  until [ "$($K get pod $1 -o jsonpath='{.status.phase}')" = Failed ]; do sleep 1; done
  local reason=$($K get pod $1 -o jsonpath='{.status.containerStatuses[0].state.terminated.reason}')
  local code=$($K get pod $1 -o jsonpath='{.status.containerStatuses[0].state.terminated.exitCode}')
  local heap=$($K logs $1 2>/dev/null | grep -m1 maxHeap)
  local last=$($K logs $1 2>/dev/null | grep -E 'allocated|OutOfMemory' | tail -1 | cut -c1-60)
  printf "   %-28s %s, terminated: %s, exit %s, last log: %s\n" "MaxRAMPercentage=$2:" "$heap" "$reason" "$code" "$last"
}
mem mem-100 100
mem mem-75 75
$K delete pod mem-100 mem-75 --now >/dev/null

deploy() {   # $1 — путь liveness-пробы, $2 — preStop: none | sleep
local prestop=""
[ "$2" = sleep ] && prestop='lifecycle: { preStop: { exec: { command: ["sleep", "5"] } } }'
$K apply -f - >/dev/null <<YAML
apiVersion: apps/v1
kind: Deployment
metadata: { name: payments }
spec:
  replicas: 3
  selector: { matchLabels: { app: payments } }
  strategy: { rollingUpdate: { maxUnavailable: 0, maxSurge: 1 } }
  template:
    metadata: { labels: { app: payments } }
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: app
          image: kb-k8s-java:1
          imagePullPolicy: Never
          resources: { requests: { memory: 256Mi, cpu: 100m }, limits: { memory: 256Mi } }
          env: [{ name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=75" }]
          readinessProbe: { httpGet: { path: /ready, port: 8080 }, periodSeconds: 2, failureThreshold: 1 }
          livenessProbe:  { httpGet: { path: $1, port: 8080 }, periodSeconds: 2, failureThreshold: 3 }
          $prestop
---
apiVersion: v1
kind: Service
metadata: { name: payments }
spec: { selector: { app: payments }, ports: [{ port: 80, targetPort: 8080 }] }
YAML
$K rollout status deploy/payments --timeout=120s >/dev/null
}
pods() { $K get pods -l app=payments -o jsonpath='{range .items[*]}{.metadata.name}{" "}{end}'; }
eps() { $K get endpointslices -l kubernetes.io/service-name=payments -o jsonpath='{range .items[*].endpoints[*]}{.conditions.ready}{" "}{end}' | tr ' ' '\n' | grep -c true; }
restarts() { $K get pods -l app=payments -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{" "}{end}'; }

$K run client --image=alpine:3.22 --restart=Never -- sleep 3600 >/dev/null
$K wait --for=condition=Ready pod/client --timeout=120s >/dev/null

# «База» — отдельный сервис db: busybox nc слушает 5432.
$K apply -f - >/dev/null <<YAML
apiVersion: apps/v1
kind: Deployment
metadata: { name: db }
spec:
  replicas: 1
  selector: { matchLabels: { app: db } }
  template:
    metadata: { labels: { app: db } }
    spec: { containers: [{ name: db, image: alpine:3.22, command: ["nc", "-lk", "-p", "5432", "-e", "true"] }] }
---
apiVersion: v1
kind: Service
metadata: { name: db }
spec: { selector: { app: db }, ports: [{ port: 5432 }] }
YAML
$K rollout status deploy/db --timeout=120s >/dev/null

echo "== the database is down for 30 s (db scaled to 0)"
for live in /live /ready; do
  deploy $live none
  $K scale deploy/db --replicas=0 >/dev/null; sleep 30
  e=$(eps); r=$(restarts)
  $K scale deploy/db --replicas=1 >/dev/null; $K rollout status deploy/db --timeout=60s >/dev/null
  printf "   liveness on %-7s ready endpoints during outage: %s, restarts: %s\n" "$live:" "$e" "$r"
  $K delete deploy payments --now >/dev/null; sleep 2
done

echo "== two rollout restarts under load (client: request every 50 ms, timeout 1 s)"
for ps in none sleep; do
  deploy /live $ps
  $K exec client -- sh -c 'rm -f /tmp/r; end=$(( $(date +%s) + 75 )); while [ $(date +%s) -lt $end ]; do wget -q -T 1 -O /dev/null http://payments 2>/dev/null && echo ok >> /tmp/r || echo fail >> /tmp/r; sleep 0.05; done' &
  sleep 3
  for n in 1 2; do $K rollout restart deploy/payments >/dev/null; $K rollout status deploy/payments --timeout=120s >/dev/null; done
  wait
  printf "   preStop %-6s requests %s, failed %s\n" "$ps:" "$($K exec client -- sh -c 'wc -l < /tmp/r' | tr -d ' ')" "$($K exec client -- sh -c 'grep -c fail /tmp/r')"
  $K delete deploy payments --now >/dev/null; sleep 2
done
