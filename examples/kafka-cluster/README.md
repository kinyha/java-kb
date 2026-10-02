# Kafka cluster demos

Примеры к темам Backend › Messaging › Kafka. Нужны JDK 21, Maven и Docker: Kafka 4.3 в режиме KRaft — три контроллера и три брокера.

```bash
cd examples/kafka-cluster
docker compose -p kbkafka up -d
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" ProducerDemo.java
java -cp "$(cat target/classpath.txt)" ConsumerDemo.java
java -cp "$(cat target/classpath.txt)" DeliveryDemo.java
java -cp "$(cat target/classpath.txt)" ReplicationDemo.java   # останавливает и запускает брокеры, ~2 мин
java -cp "$(cat target/classpath.txt)" UncleanDemo.java       # теряет подтверждённые записи, ~1,5 мин
java -cp "$(cat target/classpath.txt)" OpsDemo.java           # compaction, retention, партиции, ~2 мин
```

Номера партиций для записей без ключа и смещения меняются от запуска к запуску.
Остановить кластер: `docker compose -p kbkafka down`.
