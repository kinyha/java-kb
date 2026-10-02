#!/usr/bin/env bash
# Демо к теме Cassandra: модель по запросу, токены и реплики, уровни согласованности, upsert, LWT.
# Запуск: ./setup.sh, затем ./demo.sh. Демо останавливает и запускает узлы cas-2 и cas-3.
set -u
cql() { docker exec cas-1 cqlsh -e "$1" 2>&1 | grep -v -E '^$|Warnings|^\s*$'; }

cql "drop keyspace if exists pay;
     create keyspace pay with replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3};
     create table pay.payments_by_merchant (
         merchant_id int, day date, created_at timestamp, payment_id text, amount bigint,
         primary key ((merchant_id, day), created_at, payment_id)
     ) with clustering order by (created_at desc, payment_id asc);"
for i in 1 2 3 4 5; do
  cql "insert into pay.payments_by_merchant (merchant_id, day, created_at, payment_id, amount)
       values (7, '2026-10-02', '2026-10-02 10:0$i:00+0000', 'p-$i', ${i}00);"
done

echo "== query by partition key: last 3 payments of merchant 7 for a day"
cql "select created_at, payment_id, amount from pay.payments_by_merchant
     where merchant_id = 7 and day = '2026-10-02' limit 3;"
echo "== query without partition key"
cql "select * from pay.payments_by_merchant where amount = 300;"
echo "== replicas of partition (7, 2026-10-02)"
docker exec cas-1 nodetool getendpoints pay payments_by_merchant 7:2026-10-02

echo "== INSERT is an upsert: same primary key, new amount"
cql "insert into pay.payments_by_merchant (merchant_id, day, created_at, payment_id, amount)
     values (7, '2026-10-02', '2026-10-02 10:01:00+0000', 'p-1', 999);
     select payment_id, amount from pay.payments_by_merchant
     where merchant_id = 7 and day = '2026-10-02' and created_at = '2026-10-02 10:01:00+0000';"
echo "== lightweight transaction: insert only if absent"
cql "create table pay.idempotency (key text primary key, payment_id text);
     insert into pay.idempotency (key, payment_id) values ('k-42', 'p-100') if not exists;
     insert into pay.idempotency (key, payment_id) values ('k-42', 'p-200') if not exists;"

echo "== consistency levels with two of three replicas down"
docker stop cas-2 cas-3 >/dev/null
sleep 15
for cl in ONE QUORUM; do
  echo "-- CONSISTENCY $cl"
  cql "consistency $cl; select count(*) from pay.payments_by_merchant where merchant_id = 7 and day = '2026-10-02';" | grep -v "Consistency level set"
done
docker start cas-2 cas-3 >/dev/null
