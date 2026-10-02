# Outbox demo

Пример к теме Backend › Messaging › Transactional Outbox. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine -c wal_level=logical
cd examples/outbox
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" OutboxDemo.java
```

Брокер заменён журналом в памяти с рубильником «недоступен»: демо показывает транзакции базы, SKIP LOCKED и логическое декодирование WAL. Номера транзакций в выводе CDC и время меняются от запуска к запуску.
