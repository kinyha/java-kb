# Kafka cluster demos

Примеры к темам Backend › Messaging › Kafka. Нужны JDK 21, Maven и Docker: три брокера Kafka 4.3 в режиме KRaft.

```bash
cd examples/kafka-cluster
docker compose -p kbkafka up -d
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" ProducerDemo.java
java -cp "$(cat target/classpath.txt)" ConsumerDemo.java
```

Номера партиций для записей без ключа и смещения меняются от запуска к запуску.
Остановить кластер: `docker compose -p kbkafka down`.
