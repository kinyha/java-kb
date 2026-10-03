# Back-of-the-envelope: latency check

Пример к теме System Design › Back-of-the-envelope. Нужны JDK 21, Maven и Docker с контейнерами kb-redis и kb-pg.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/sd-estimation
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -Xmx2g -cp "$(cat target/classpath.txt)" LatencyDemo.java
```

Числа зависят от машины. На странице — ноутбук с Apple Silicon, базы в Docker Desktop: их fsync не доходит до диска так же строго, как fsync macOS.
