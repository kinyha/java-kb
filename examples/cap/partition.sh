#!/usr/bin/env bash
# CAP на практике: кластер etcd из трёх узлов, сетевое разделение 2 | 1.
# Узлы подключены к двум сетям: etcd-a (etcd-1, etcd-2, etcd-3) — по ней идёт Raft; разделение = отключить etcd-3 от неё.
set -u
IMG=quay.io/coreos/etcd:v3.6.5
docker rm -f etcd-1 etcd-2 etcd-3 >/dev/null 2>&1; docker network rm etcd-net >/dev/null 2>&1
docker network create etcd-net >/dev/null
CLUSTER=etcd-1=http://etcd-1:2380,etcd-2=http://etcd-2:2380,etcd-3=http://etcd-3:2380
for n in 1 2 3; do
  docker run -d --name etcd-$n --network etcd-net $IMG etcd --name etcd-$n \
    --initial-advertise-peer-urls http://etcd-$n:2380 --listen-peer-urls http://0.0.0.0:2380 \
    --advertise-client-urls http://etcd-$n:2379 --listen-client-urls http://0.0.0.0:2379 \
    --initial-cluster $CLUSTER --initial-cluster-state new >/dev/null
done
sleep 4
ctl() { local node=$1; shift; docker exec etcd-$node etcdctl --endpoints=http://127.0.0.1:2379 --command-timeout=3s "$@" 2>&1 | tail -1; }

echo "== healthy cluster"
echo "   put on etcd-3:                 $(ctl 3 put balance/anna 100)"
echo "   get on etcd-1:                 $(ctl 1 get balance/anna --print-value-only)"

docker network disconnect etcd-net etcd-3          # Разделение: etcd-3 один.
sleep 2
echo "== partition: {etcd-1, etcd-2} | {etcd-3}"
echo "   put on majority side (etcd-1): $(ctl 1 put balance/anna 40)"
echo "   put on minority side (etcd-3): $(ctl 3 put balance/anna 999)"
echo "   linearizable get on etcd-3:    $(ctl 3 get balance/anna --print-value-only)"
echo "   serializable get on etcd-3:    $(ctl 3 get balance/anna --print-value-only --consistency=s)"
echo "   linearizable get on etcd-1:    $(ctl 1 get balance/anna --print-value-only)"

docker network connect etcd-net etcd-3             # Сеть восстановлена.
sleep 3
echo "== partition healed"
echo "   get on etcd-3:                 $(ctl 3 get balance/anna --print-value-only)"
docker rm -f etcd-1 etcd-2 etcd-3 >/dev/null; docker network rm etcd-net >/dev/null
