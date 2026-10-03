# Rate limiting demo

Пример к теме System Design › Building Blocks › Rate Limiting. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
cd examples/rate-limiting
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" RateLimitDemo.java
```

Демо очищает Redis командой FLUSHALL. Время в сценарии на границе окна задано явно; гонка из 200 запросов настоящая.
