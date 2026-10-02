# Boot demos

Примеры к темам Spring › Boot. Нужны JDK 21 и Maven.
В `pom.xml` зафиксированы Spring Boot 4.1.1 и Spring Framework 7.0.9.

```bash
cd examples/boot
./run.sh AutoConfigDemo
./run.sh AutoConfigDemo opaque
./run.sh AutoConfigDemo report
```

`run.sh` компилирует один файл с `--release 21 -parameters`, копирует `resources/`
в classpath и запускает класс из пакета `kb.boot`. Внешние сервисы не нужны.

`AutoConfigDemo` загружает `BankAutoConfiguration` из настоящего
`AutoConfiguration.imports`. Первый запуск сравнивает стандартный шлюз,
пользовательский бин и отключение свойства. `opaque` показывает два бина
из-за типа `Object` в пользовательском `@Bean`; `report` печатает причину
отступления автоконфигурации из `ConditionEvaluationReport`.

В демо используется `@SpringBootConfiguration + @EnableAutoConfiguration`
и явный `@Import`: component scan мог бы подобрать соседние учебные варианты
и испортить условия опыта. Банк заменён локальной функцией. `ObjectProvider`
даёт запустить контекст без шлюза и увидеть ошибку выбора при двух шлюзах.
В реальном обязательном платёжном пути зависимость обычно задают конструктором.
