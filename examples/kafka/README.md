# Kafka demos

Пример к теме Spring › Integration › Kafka in Spring. Нужны JDK 21 и Maven; Spring Boot 4.1.1, Spring Kafka 4.1.

```bash
cd examples/kafka
./run.sh KafkaDemo basics   # ключ и партиция, concurrency 4 на 3 партиции
./run.sh KafkaDemo errors   # DefaultErrorHandler по умолчанию: 10 попыток и пропуск
./run.sh KafkaDemo dlt      # повторы с паузой и DeadLetterPublishingRecoverer
```

Брокер Kafka в режиме KRaft поднимается внутри JVM через `EmbeddedKafkaKraftBroker`
из spring-kafka-test. Docker не нужен. Миллисекунды в выводе меняются от запуска к запуску.
