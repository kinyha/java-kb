# Notification Service demo

Пример к теме System Design › Case Studies › Notification Service. Нужны JDK 21, Maven и Docker с контейнером kb-redis.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
cd examples/sd-notifications
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" NotificationDemo.java
```

Задержка OTP в общей очереди зависит от скорости машины; порядок величин повторяется.
