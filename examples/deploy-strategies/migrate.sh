#!/usr/bin/env bash
# Миграция схемы во время rolling update: v1 и v2 приложения работают одновременно.
# Нужен контейнер kb-pg (PostgreSQL 17, см. examples/db-tx).
set -u
q() { local out; out=$(docker exec -e PGOPTIONS='-c client_min_messages=warning' kb-pg psql -U postgres -qAt -v ON_ERROR_STOP=0 -c "$1" 2>&1 | grep -v '^ *\(LINE\|\^\)' | grep -v '^ *^')
      echo "${out:-OK}" | sed 's/^/     /'; }
quiet() { docker exec -e PGOPTIONS='-c client_min_messages=warning' kb-pg psql -U postgres -qAt -c "$1" >/dev/null; }
V1="insert into invoice(amount) values (100)"                       # код v1
V2="insert into invoice(amount_minor) values (200)"                 # код v2

echo "== breaking: rename the column in one step"
quiet "drop table if exists invoice; create table invoice(id bigserial primary key, amount bigint)"
echo "   migration: rename amount -> amount_minor"; q "alter table invoice rename column amount to amount_minor"
echo "   v1 pods still running:";  q "$V1"
echo "   v2 pods:";                q "$V2"
echo "   rollback to v1 does not help:"; q "$V1"

echo "== expand / contract"
quiet "drop table if exists invoice; create table invoice(id bigserial primary key, amount bigint)"
quiet "insert into invoice(amount) values (100)"
echo "   1. expand: add the new column, nullable"
q "alter table invoice add column amount_minor bigint"
echo "   v1 writes the old column:";    q "$V1"
echo "   v2 writes both columns:";      q "insert into invoice(amount, amount_minor) values (200, 200)"
echo "   2. backfill old rows:";        q "update invoice set amount_minor = amount where amount_minor is null"
echo "   rows missing amount_minor: $(docker exec kb-pg psql -U postgres -qAt -c 'select count(*) from invoice where amount_minor is null')"
echo "   3. contract, after no v1 is left and rollback to v1 is no longer needed:"
q "alter table invoice alter column amount_minor set not null, drop column amount"
echo "   v2 writes only the new column:"; q "$V2"
echo "   rows: $(docker exec kb-pg psql -U postgres -qAt -c "select string_agg(amount_minor::text, ' ' order by id) from invoice")"
