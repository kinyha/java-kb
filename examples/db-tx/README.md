# Transactions demos

Примеры к папкам Databases › Transactions, PostgreSQL Internals, Performance, Schema и Scaling. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/db-tx
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" IsolationDemo.java
java -cp "$(cat target/classpath.txt)" LocksDemo.java
java -cp "$(cat target/classpath.txt)" MvccDemo.java   # номера транзакций зависят от истории базы
java -cp "$(cat target/classpath.txt)" VacuumDemo.java
java -cp "$(cat target/classpath.txt)" ArchitectureDemo.java   # перезапускает контейнер kb-pg ради пустого кеша
java -cp "$(cat target/classpath.txt)" IndexDemo.java
java -cp "$(cat target/classpath.txt)" QueryPlanDemo.java
java -cp "$(cat target/classpath.txt)" IndexTypesDemo.java
java -cp "$(cat target/classpath.txt)" MigrationDemo.java
java -cp "$(cat target/classpath.txt)" ModelingDemo.java
java -cp "$(cat target/classpath.txt)" PartitionDemo.java
java -cp "$(cat target/classpath.txt)" JsonbDemo.java

# SlowQueriesDemo нужен pg_stat_statements:
docker rm -f kb-pg
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine \
  -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=top
java -cp "$(cat target/classpath.txt)" SlowQueriesDemo.java
```
