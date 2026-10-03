# Unique ID generation demo

Пример к теме System Design › Building Blocks › Unique ID Generation. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
docker exec kb-pg psql -U postgres -c "create extension if not exists pgstattuple"
cd examples/id-generation
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" IdDemo.java
```

Идентификаторы и время вставки меняются от запуска к запуску; размер индекса и доля пустоты повторяются.
