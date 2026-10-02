# Distributed transactions demo

Пример к теме System Design › Distributed Systems › Distributed Transactions. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine -c max_prepared_transactions=10
docker exec kb-pg psql -U postgres -c "create database wallet" -c "create database bank"
cd examples/dist-tx
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" DistTxDemo.java
```

Prepared-транзакции переживают отключение клиента; демо убирает хвосты прошлых запусков перед стартом.
