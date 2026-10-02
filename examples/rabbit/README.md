# RabbitMQ demos

Пример к теме Spring › Integration › RabbitMQ in Spring. Нужны JDK 21, Maven и RabbitMQ на localhost:5672.

```bash
docker run -d --name kb-rabbit -p 5672:5672 rabbitmq:4-management
cd examples/rabbit
./run.sh RabbitDemo routing   # topic exchange, confirms и returns
./run.sh RabbitDemo requeue   # исключение без настройки: бесконечный requeue
./run.sh RabbitDemo dlx       # retry.max-retries и dead letter exchange
docker rm -f kb-rabbit
```

Проверено на RabbitMQ 4.3.6, Spring Boot 4.1.1, Spring AMQP 4.1.1. Число доставок
в режиме requeue и миллисекунды в dlx меняются от запуска к запуску.
