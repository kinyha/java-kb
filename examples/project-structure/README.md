# Project Structure demos

Примеры к теме Backend › Code Design › Project Structure. Все команды — из этого каталога.

## Что пускает компилятор

Нужен только JDK 21:

```bash
java StructureDemo.java
```

`variants/` — четыре раскладки одного платёжного сервиса: package by layer, package by feature,
ports and adapters (`hex`), package by component. `shortcuts/` — `RefundController`, который читает
хранилище платежей в обход сервиса. Демо компилирует каждый вариант сначала без обхода, потом с ним,
и печатает первую ошибку javac. Второй прогон делает все типы без модификатора public: проверяется,
что граница держалась именно на package-private. Если результат отличается от ожидаемого, демо падает.

## Spring Modulith и замороженное правило ArchUnit

Нужны JDK 21 и Maven. Зависимости: Spring Modulith 2.1.1 (с ним ArchUnit 1.4.2 и Spring Framework 7.0.9),
Spring Boot 4.1.1 только как библиотека — приложение не запускается.

```bash
bash modulith/run.sh
```

- `broken` — модуль refunds импортирует `payments.persistence.PaymentRepository`. Тип public, javac молчит,
  `ApplicationModules.verify()` находит нарушение.
- `later` — к broken добавлен reports с тем же нарушением. Правило `FreezingArchRule` записало нарушения
  broken в `target/archunit_store` и падает только на новых.
- `fixed` — refunds ходит через `PaymentService`. Modulith и замороженное правило проходят, хранилище
  пустеет. Последняя строка поднимает обычный `AnnotationConfigApplicationContext` и показывает, что Spring
  внедряет package-private бины.

## Модули Gradle

Нужны JDK 21 и Gradle 8+ (проверено на 9.6.1):

```bash
bash gradle/run.sh
```

`app` зависит от `payments`, а `payments` — от `payments-persistence`. С `implementation` класс из app,
импортирующий persistence, не компилируется. С `-Pleak` зависимость подключается через `api`,
как compile-зависимость в Maven, и тот же класс собирается.
