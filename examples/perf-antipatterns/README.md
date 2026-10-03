# Performance antipatterns demo

Пример к теме System Design › Reliability › Performance Antipatterns. Нужны JDK 21, Maven и Docker.
Отдельный PostgreSQL с задержкой 1 мс на сетевом интерфейсе контейнера (tc netem):

```bash
docker run -d --name kb-pg-slow --cap-add NET_ADMIN -e POSTGRES_PASSWORD=pw -p 55433:5432 postgres:17-alpine
docker exec kb-pg-slow sh -c 'apk add -q iproute2-tc && tc qdisc add dev eth0 root netem delay 1ms'
cd examples/perf-antipatterns
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" AntipatternsDemo.java
docker rm -f kb-pg-slow
```

Миллисекунды меняются от запуска к запуску на единицы процентов.
