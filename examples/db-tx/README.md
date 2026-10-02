# Transactions demos

Примеры к папкам Databases › Transactions и PostgreSQL Internals. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/db-tx
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" IsolationDemo.java
java -cp "$(cat target/classpath.txt)" LocksDemo.java
java -cp "$(cat target/classpath.txt)" MvccDemo.java   # номера транзакций зависят от истории базы
java -cp "$(cat target/classpath.txt)" VacuumDemo.java
```
