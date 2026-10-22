# Flash Sale & Ticketing demo

Пример к теме System Design › Case Studies › Flash Sale & Ticketing. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-flash-pg -e POSTGRES_PASSWORD=pw -p 55439:5432 postgres:17-alpine -c max_connections=300
docker run -d --name kb-flash-redis -p 6389:6379 redis:8-alpine
cd examples/sd-flash-sale
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" FlashSaleDemo.java                 # все разделы, около минуты
java -cp "$(cat target/classpath.txt)" FlashSaleDemo.java race holds      # только выбранные
```

Разделы: `race` — 2000 покупателей за 100 единиц; `throughput` — заказов в секунду для каждой стратегии;
`gate` — Redis-привратник, запись пачками, эмуляция failover и сверка; `holds` — оплата на границе срока
против очистки резервов; `queue` — модель допуска из очереди ожидания (без Redis и базы, шаг 10 секунд).

Демо пересоздаёт свои таблицы и очищает Redis командой FLUSHALL, поэтому контейнеры отдельные,
на своих портах. Гонки настоящие: число лишних продаж у наивных вариантов и пропускная способность
меняются от запуска к запуску и зависят от машины. Потерю записей при failover демо эмулирует —
удаляет последние 40 покупок из Redis; настоящая потеря при асинхронной репликации показана в
`examples/redis-ops/persistence.sh`.
