# Service communication demo

Пример к теме System Design › Architecture › Service Communication. Нужны JDK 21, Maven и Docker с контейнером kb-redis.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
cd examples/service-communication
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" CommunicationDemo.java
```

Очередь — список Redis без подтверждений, только для демонстрации развязки. Задержка резерва и время сборки ответа меняются от запуска к запуску.
