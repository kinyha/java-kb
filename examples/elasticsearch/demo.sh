#!/usr/bin/env bash
# Демо к теме Elasticsearch: анализаторы, text и keyword, BM25, near-real-time.
# Запуск: docker run -d --name kb-es -p 9200:9200 -e discovery.type=single-node \
#           -e xpack.security.enabled=false -e ES_JAVA_OPTS="-Xms1g -Xmx1g" elasticsearch:9.2.0
#         ./demo.sh
set -u
ES=localhost:9200
req() { curl -s -H 'Content-Type: application/json' -X "$1" "$ES$2" ${3:+-d "$3"}; }
py() { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }

echo "== analyzers: 'Оплата заказов в кофейнях Москвы'"
for a in standard russian; do
  printf '   %-8s ' "$a:"
  req POST /_analyze "{\"analyzer\": \"$a\", \"text\": \"Оплата заказов в кофейнях Москвы\"}" | py "print([t['token'] for t in d['tokens']])"
done

req DELETE /payments >/dev/null
req PUT /payments '{
  "settings": {"number_of_shards": 1, "number_of_replicas": 0},
  "mappings": {"properties": {
    "description": {"type": "text", "analyzer": "russian"},
    "merchant":    {"type": "text", "fields": {"raw": {"type": "keyword"}}},
    "amount":      {"type": "long"}}}}' >/dev/null
docs=('{"merchant": "Coffee Point", "description": "Оплата заказа в кофейне", "amount": 450}'
      '{"merchant": "Coffee Point", "description": "Возврат за кофе", "amount": 200}'
      '{"merchant": "Book House", "description": "Оплата книги о кофе и кофейнях", "amount": 900}'
      '{"merchant": "Taxi Go", "description": "Поездка до кофейни, оплата картой", "amount": 300}')
i=1; for d in "${docs[@]}"; do req PUT "/payments/_doc/p-$i" "$d" >/dev/null; i=$((i+1)); done
req POST /payments/_refresh >/dev/null

echo "== match 'кофейни оплата' (russian analyzer), ranked by BM25"
req POST /payments/_search '{"query": {"match": {"description": "кофейни оплата"}}}' \
  | py "[print(f\"   {h['_score']:.3f}  {h['_id']}  {h['_source']['description']}\") for h in d['hits']['hits']]"

echo "== term 'Coffee Point' on text field vs keyword field"
for f in merchant merchant.raw; do
  printf '   %-13s hits: ' "$f"
  req POST /payments/_search "{\"query\": {\"term\": {\"$f\": \"Coffee Point\"}}}" | py "print(d['hits']['total']['value'])"
done
echo "== terms aggregation on merchant.raw"
req POST /payments/_search '{"size": 0, "aggs": {"by_merchant": {"terms": {"field": "merchant.raw"}, "aggs": {"sum": {"sum": {"field": "amount"}}}}}}' \
  | py "[print(f\"   {b['key']}: {b['doc_count']} payments, {int(b['sum']['value'])}\") for b in d['aggregations']['by_merchant']['buckets']]"
echo "== aggregation on text field"
req POST /payments/_search '{"size": 0, "aggs": {"m": {"terms": {"field": "merchant"}}}}' \
  | py "e=d.get('error',{}); print('   ' + (e.get('root_cause',[{}])[0].get('reason','')[:110] if e else 'ok'))"

echo "== near real time: index and search immediately"
req PUT /payments/_doc/p-9 '{"merchant": "Late Cafe", "description": "Оплата в кофейне ночью", "amount": 100}' >/dev/null
printf '   right after index: '; req POST /payments/_search '{"query": {"match": {"description": "ночью"}}}' | py "print(d['hits']['total']['value'])"
printf '   get by id:         '; req GET /payments/_doc/p-9 | py "print(d['found'])"
sleep 1.5
printf '   after refresh:     '; req POST /payments/_search '{"query": {"match": {"description": "ночью"}}}' | py "print(d['hits']['total']['value'])"
