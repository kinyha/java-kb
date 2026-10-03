# Event sourcing demo

Пример к теме System Design › Distributed Systems › Event-driven, CQRS, Event Sourcing. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/event-driven
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" EventStoreDemo.java
```
