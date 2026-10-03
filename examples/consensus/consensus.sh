#!/usr/bin/env bash
# Raft в etcd 3.6: выбор нового лидера после остановки старого и блокировка с fencing token.
set -u
cd "$(dirname "$0")"
IMG=quay.io/coreos/etcd:v3.6.5
cleanup() { docker rm -f etcd-1 etcd-2 etcd-3 client-a client-b >/dev/null 2>&1; docker network rm etcd-net >/dev/null 2>&1; }
cleanup
docker network create etcd-net >/dev/null
CLUSTER=etcd-1=http://etcd-1:2380,etcd-2=http://etcd-2:2380,etcd-3=http://etcd-3:2380
for n in 1 2 3; do
  docker run -d --name etcd-$n --network etcd-net $IMG etcd --name etcd-$n \
    --initial-advertise-peer-urls http://etcd-$n:2380 --listen-peer-urls http://0.0.0.0:2380 \
    --advertise-client-urls http://etcd-$n:2379 --listen-client-urls http://0.0.0.0:2379 \
    --initial-cluster $CLUSTER --initial-cluster-state new >/dev/null
done
sleep 4
EP=http://etcd-1:2379,http://etcd-2:2379,http://etcd-3:2379
ctl() { docker exec etcd-$1 etcdctl --endpoints=http://127.0.0.1:2379 --command-timeout=1s "${@:2}" 2>&1; }
# Лидер — узел, у которого поле IS LEADER (9-е в выводе endpoint status) равно true.
leader() { for n in 1 2 3; do s=$(ctl $n endpoint status 2>/dev/null) || continue; [ "$(echo "$s" | cut -d, -f9 | tr -d ' ')" = true ] && echo etcd-$n; done; }
term() { ctl ${1#etcd-} endpoint status | cut -d, -f11 | tr -d ' '; }

echo "== leader election (Raft, election timeout 1000 ms by default)"
failover() {   # $1 — kill (авария) или stop (штатная остановка: SIGTERM)
  L=$(leader); local before=$(term $L)
  if [ "$1" = kill ]; then docker kill $L >/dev/null; else docker stop $L >/dev/null; fi
  local start=$(python3 -c 'import time; print(int(time.time()*1000))')
  local other=$( [ "$L" = etcd-1 ] && echo 2 || echo 1 )
  # Частые короткие попытки: каждая ждёт не больше 200 мс, поэтому точность замера ~0,2 с.
  until docker exec etcd-$other etcdctl --endpoints=http://127.0.0.1:2379 --command-timeout=200ms put k v 2>&1 | grep -q OK; do :; done
  local end=$(python3 -c 'import time; print(int(time.time()*1000))')
  L2=$(leader)
  printf "   %-5s %s (term %s): writes accepted again after %4d ms, new leader %s (term %s)\n" "$1" "$L" "$before" $((end - start)) "$L2" "$(term $L2)"
  docker start $L >/dev/null; sleep 3
}
failover kill
failover stop

echo "== lock with a 2 s lease; client A freezes while holding it"
docker volume rm kb-fence >/dev/null 2>&1; docker volume create kb-fence >/dev/null
# В образе etcd нет shell: клиенты — alpine с etcdctl, скопированным из образа etcd.
docker create --name etcd-bin $IMG >/dev/null; docker cp etcd-bin:/usr/local/bin/etcdctl ./etcdctl >/dev/null; docker rm etcd-bin >/dev/null
run_client() {   # $1 name, $2 команда под блокировкой
  docker run -d --name client-$1 --network etcd-net -v kb-fence:/data -v "$PWD/write.sh":/write.sh:ro \
    -v "$PWD/etcdctl":/usr/local/bin/etcdctl:ro alpine \
    etcdctl --endpoints=$EP lock --ttl=2 job -- sh -c "$2" >/dev/null
}
run_client a 'echo "A got the lock, token $ETCD_LOCK_REV" >> /data/events; sleep 4; /write.sh $ETCD_LOCK_REV A'
sleep 1.5
docker pause client-a >/dev/null                                   # «пауза GC»: нет keepalive аренды
sleep 3                                                              # аренда 2 с истекла
run_client b 'echo "B got the lock, token $ETCD_LOCK_REV" >> /data/events; /write.sh $ETCD_LOCK_REV B'
sleep 2
docker unpause client-a >/dev/null                                 # A просыпается и дописывает
sleep 4
show() { docker run --rm -v kb-fence:/data alpine sh -c "cat /data/$1" | sed 's/^/   /'; }
show events
echo "   naive storage:";  show naive.log | sed 's/^/  /'
echo "   fenced storage:"; show fenced.log | sed 's/^/  /'
cleanup; docker volume rm kb-fence >/dev/null; rm -f etcdctl
