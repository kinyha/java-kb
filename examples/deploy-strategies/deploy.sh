#!/usr/bin/env bash
# Стратегии выкатки на k3s 1.34: rolling update, откат, blue-green, canary.
# Образ kb-k8s-app:1 из examples/kubernetes отвечает "pod=... version=$VERSION ...".
set -u
cd "$(dirname "$0")"
K="docker exec -i kb-k3s kubectl"
if ! docker ps --format '{{.Names}}' | grep -q '^kb-k3s$'; then
  docker run -d --name kb-k3s --privileged rancher/k3s:v1.34.1-k3s1 server --disable traefik --disable metrics-server >/dev/null
  until $K get nodes 2>/dev/null | grep -q ' Ready'; do sleep 2; done
fi
(cd ../kubernetes && docker build -q -t kb-k8s-app:1 . >/dev/null)
docker save kb-k8s-app:1 | docker exec -i kb-k3s ctr images import - >/dev/null
$K delete deploy,svc,cm,pod --all --now >/dev/null 2>&1

dep() {   # $1 имя, $2 версия, $3 реплики, $4 метки шаблона (yaml-словарь)
$K apply -f - >/dev/null <<YAML
apiVersion: apps/v1
kind: Deployment
metadata: { name: $1 }
spec:
  replicas: $3
  minReadySeconds: 3                 # новый под должен 3 с оставаться готовым, прежде чем выкатка пойдёт дальше
  selector: { matchLabels: $4 }
  strategy: { rollingUpdate: { maxSurge: 1, maxUnavailable: 0 } }
  template:
    metadata: { labels: $4 }
    spec:
      containers:
        - name: app
          image: kb-k8s-app:1
          imagePullPolicy: Never
          env: [{ name: VERSION, value: "$2" }]
          readinessProbe: { httpGet: { path: /, port: 8080 }, periodSeconds: 1 }
YAML
$K rollout status deploy/$1 --timeout=120s >/dev/null
}
svc() { $K apply -f - >/dev/null <<YAML
apiVersion: v1
kind: Service
metadata: { name: payments }
spec: { selector: $1, ports: [{ port: 80, targetPort: 8080 }] }
YAML
}
$K run client --image=alpine:3.22 --restart=Never -- sleep 3600 >/dev/null
$K wait --for=condition=Ready pod/client --timeout=120s >/dev/null
# Клиент: каждые 100 мс запрос; в файл — секунда от начала и версия.
load() { $K exec client -- sh -c "wget -q -O /dev/null http://payments; rm -f /tmp/v; s=\$(date +%s); end=\$((s + $1)); while [ \$(date +%s) -lt \$end ]; do v=\$(wget -q -T 1 -O- http://payments 2>/dev/null | sed -E 's/.*version=([0-9]+).*/v\1/'); echo \"\$((\$(date +%s) - s)) \${v:-fail}\" >> /tmp/v; sleep 0.1; done" ; }
timeline() { $K exec client -- sh -c 'cat /tmp/v' | awk '{c[$1" "$2]++; t[$1]=1} END {for (s in t) {line=s":"; for (k in c) {split(k,a," "); if (a[1]==s) line=line" "a[2]"="c[k]}; print line}}' | sort -n | awk -F: '{printf "   %2ss %s\n", $1, $2}'; }
mixed() { $K exec client -- sh -c 'cat /tmp/v' | awk '{seen[$1" "$2]=1; s[$1]=1} END {n=0; for (x in s) if ((x" v1") in seen && (x" v2") in seen) n++; print n}'; }

echo "== rolling update v1 -> v2, 4 replicas, maxSurge 1, maxUnavailable 0"
dep payments 1 4 '{ app: payments }'; svc '{ app: payments }'
load 30 & sleep 2; $K set env deploy/payments VERSION=2 >/dev/null; wait
echo "   seconds with both versions answering: $(mixed); failed requests: $($K exec client -- sh -c 'grep -c fail /tmp/v || true')"
timeline | head -12

echo "== rollback: kubectl rollout undo"
t0=$(date +%s); $K rollout undo deploy/payments >/dev/null; $K rollout status deploy/payments --timeout=120s >/dev/null
echo "   back to v1 in $(( $(date +%s) - t0 )) s, revisions: $($K rollout history deploy/payments | tail -n +3 | awk 'NF {print $1}' | tr '\n' ' ')"
$K delete deploy payments --now >/dev/null

echo "== blue-green: switch the Service selector"
dep blue 1 3 '{ app: payments, color: blue }'; dep green 2 3 '{ app: payments, color: green }'
svc '{ app: payments, color: blue }'
load 12 & sleep 5; svc '{ app: payments, color: green }'; wait
echo "   seconds with both versions answering: $(mixed); failed requests: $($K exec client -- sh -c 'grep -c fail /tmp/v || true')"
$K delete deploy blue green --now >/dev/null

echo "== canary: 9 stable pods v1 + 1 canary pod v2 behind one Service"
dep stable 1 9 '{ app: payments, track: stable }'; dep canary 2 1 '{ app: payments, track: canary }'
svc '{ app: payments }'
$K exec client -- sh -c 'for i in $(seq 400); do wget -q -O- http://payments; done' | sed -E 's/.*version=([0-9]+).*/v\1/' | sort | uniq -c | awk '{printf "   %s: %s of 400\n", $2, $1}'
$K delete deploy stable canary --now >/dev/null
