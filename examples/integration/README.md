# Integration demos

Примеры к темам Spring › Integration. Нужны JDK 21 и Maven; Spring Boot 4.1.1.

```bash
cd examples/integration
./run.sh CacheDemo            # @Cacheable: прокси, ключи, ссылка на общий объект, stampede
./run.sh CacheDemo caffeine   # Caffeine со сроком жизни 1 секунда
```

Внешние сервисы не нужны: «банк» изображает метод, который спит 200 мс.
