#!/usr/bin/env bash
# Kubernetes Basics на k3s 1.34 в Docker: Deployment, Service, ConfigMap, метки.
set -u
cd "$(dirname "$0")"
K="docker exec -i kb-k3s kubectl"
if ! docker ps --format '{{.Names}}' | grep -q '^kb-k3s$'; then
  docker run -d --name kb-k3s --privileged rancher/k3s:v1.34.1-k3s1 server --disable traefik --disable metrics-server >/dev/null
  until $K get nodes 2>/dev/null | grep -q ' Ready'; do sleep 2; done
fi
docker build -q -t kb-k8s-app:1 . >/dev/null
docker save kb-k8s-app:1 | docker exec -i kb-k3s ctr images import - >/dev/null
$K delete deploy,svc,cm,pod --all --now >/dev/null 2>&1
$K apply -f - < app.yaml >/dev/null
$K run client --image=alpine:3.22 --restart=Never -- sleep 3600 >/dev/null
$K rollout status deploy/payments --timeout=120s >/dev/null
$K wait --for=condition=Ready pod/client --timeout=120s >/dev/null
hit() { $K exec client -- sh -c "for i in \$(seq $1); do wget -qO- http://payments; done"; }
pods() { $K get pods -l app=payments -o jsonpath='{range .items[*]}{.metadata.name}{" "}{end}'; }
short() { sed -E 's/payments-[a-z0-9]+-//g'; }

echo "== Service spreads requests over the pods (30 requests)"
hit 30 | awk '{print $1}' | sort | uniq -c | awk '{printf "   %s x%s\n", $2, $1}' | short

echo "== a pod is deleted: the ReplicaSet creates a new one"
before=$(pods); victim=$(echo $before | awk '{print $1}')
$K delete pod $victim --wait=false >/dev/null
$K wait --for=delete pod/$victim --timeout=60s >/dev/null; $K rollout status deploy/payments --timeout=60s >/dev/null
echo "   before: $(echo $before | short)"
echo "   after:  $(pods | short)   (deleted $(echo $victim | short))"

echo "== ConfigMap changed to hello-v2"
$K patch cm payments-config -p '{"data":{"GREETING":"hello-v2","greeting":"hello-v2"}}' >/dev/null
t0=$(date +%s)
r=""; until echo "$r" | grep -q 'file=hello-v2'; do sleep 2; r=$(hit 1); done
echo "   after $(( $(date +%s) - t0 )) s: $(echo "$r" | short)"
$K rollout restart deploy/payments >/dev/null; $K rollout status deploy/payments --timeout=120s >/dev/null
echo "   after rollout restart: $(hit 1 | short)"

echo "== a pod loses the label app=payments"
eps() { $K get endpointslices -l kubernetes.io/service-name=payments -o jsonpath='{range .items[*].endpoints[*]}{.targetRef.name}{" "}{end}'; }
until [ "$(pods | wc -w | tr -d ' ')" = 3 ]; do sleep 1; done          # старые поды после restart ушли
p=$(eps | awk '{print $1}')
$K label pod $p app=debug --overwrite >/dev/null
sleep 3; $K rollout status deploy/payments --timeout=60s >/dev/null
echo "   pods with app=payments: $($K get pods -l app=payments --no-headers | wc -l | tr -d ' '), with app=debug: $($K get pods -l app=debug --no-headers | wc -l | tr -d ' ')"
echo "   endpoints of the Service: $(eps | short)"
echo "   relabeled pod still runs: $($K get pod $p -o jsonpath='{.status.phase}') ($(echo $p | short))"
