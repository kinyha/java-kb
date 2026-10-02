# RabbitMQ broker demos

MessagingDemo — к теме Backend › Messaging › Brokers & Guarantees: очередь и pub/sub, at-most-once, at-least-once с идемпотентным консьюмером.
BrokerDemo — к теме Backend › Messaging › RabbitMQ: prefetch, порядок после requeue, ядовитое сообщение в quorum-очереди, single active consumer. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-rabbit -p 5672:5672 rabbitmq:4-management
cd examples/rabbit-broker
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" MessagingDemo.java
java -cp "$(cat target/classpath.txt)" BrokerDemo.java
```

Проверено на RabbitMQ 4.3.6 и amqp-client 5.37.0.
