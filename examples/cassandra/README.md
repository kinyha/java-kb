# Cassandra demo

Пример к теме Databases › NoSQL › Cassandra. Нужен Docker (около 2 ГБ памяти под три узла).

```bash
cd examples/cassandra
./setup.sh   # три узла Cassandra 5.0, стартуют по очереди, 2–3 минуты
./demo.sh    # модель по запросу, реплики, upsert, LWT, уровни согласованности
```

Демо останавливает и снова запускает cas-2 и cas-3; перед повтором дождитесь трёх строк UN в `docker exec cas-1 nodetool status`.
