# Background jobs demo

Пример к теме System Design › Building Blocks › Background Jobs. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/background-jobs
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" JobQueueDemo.java
```

Время выполнения меняется от запуска к запуску; соотношение FOR UPDATE и SKIP LOCKED сохраняется.
