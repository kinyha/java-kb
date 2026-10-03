# Scalability demos

Примеры к теме System Design › Fundamentals › Scalability. Нужны JDK 21, Maven и Docker с контейнером kb-redis.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
cd examples/scalability
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
./run.sh
```

ContentionDemo останавливает потоки volatile-флагом через секунду; числа зависят от процессора. SessionDemo поднимает четыре экземпляра на JDK HttpServer и nginx 1.31 перед ними.
