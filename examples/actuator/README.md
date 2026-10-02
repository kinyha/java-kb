# Actuator demo

Пример к теме Spring › Boot › Actuator. Нужны JDK 21 и Maven; Spring Boot 4.1.1.

```bash
cd examples/actuator
./run.sh ActuatorDemo
```

Демо поднимает два контекста с веб-сервером на случайном порту и само ходит к ним по HTTP.
Первый — настройки Actuator по умолчанию. Второй открывает health, metrics и prometheus,
затем «роняет» банк флагом `Bank.up`, публикует `ReadinessState.REFUSING_TRAFFIC`
и читает метрику `payments.charge`. Внешние сервисы не нужны.
