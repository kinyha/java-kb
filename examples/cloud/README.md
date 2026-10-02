# Spring Cloud demo

Пример к теме Spring › Spring Cloud. Нужны JDK 21 и Maven.

```bash
cd examples/cloud
./run.sh CloudDemo
```

В одной JVM поднимаются два экземпляра «банка» и платёжный сервис с `@FeignClient`,
Spring Cloud LoadBalancer (discovery задан списком адресов) и circuit breaker Resilience4j.
Проверено на Spring Boot 4.1.1 и Spring Cloud 2025.1.3: официальная ветка для Boot 4.1 —
2026.0, на момент проверки она была доступна только как milestone.
