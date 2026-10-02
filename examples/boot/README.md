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

## Configuration & Profiles

```bash
./run.sh ConfigDemo
./run.sh ConfigDemo --spring.profiles.active=prod
./run.sh ConfigDemo --spring.profiles.active=sandbox
./run.sh ConfigDemo --spring.profiles.active=prod --spring.config.additional-location=file:./config-demo/external/
PAYMENTS_BANK_TIMEOUT=900ms ./run.sh ConfigDemo --spring.profiles.active=prod
PAYMENTS_BANK_TIMEOUT=900ms SPRING_APPLICATION_JSON='{"payments":{"bank":{"timeout":"500ms"}}}' ./run.sh ConfigDemo --spring.profiles.active=prod
PAYMENTS_BANK_TIMEOUT=900ms SPRING_APPLICATION_JSON='{"payments":{"bank":{"timeout":"500ms"}}}' ./run.sh ConfigDemo --spring.profiles.active=prod --payments.bank.timeout=250ms
./run.sh ConfigDemo --spring.config.import=classpath:/config-demo/override.yaml
./run.sh ConfigDemo --spring.config.import=optional:classpath:/config-demo/missing.yaml
./run.sh ConfigDemo --spring.config.import=classpath:/config-demo/missing.yaml
./run.sh ConfigDemo --payments.bank.max-attempts=0
./run.sh ConfigDemo --payments.bank.timeout=oops
./run.sh ConfigDemo --payments.bank.timeout=250
./run.sh ConfigDemo snapshot
```

`ConfigDemo` регистрирует record через `@EnableConfigurationProperties` и
использует `spring.config.location=classpath:/config-demo/`. В ресурсах есть
обычный YAML, документ для sandbox, файл prod и импортируемый файл. Внешний
YAML лежит в `config-demo/external/`; запуск идёт из `examples/boot`.
Скрипт копирует все ресурсы, а пример не включает автоконфигурацию соседнего демо.

Вывод показывает типизированное время, списки и map, различие канонического
и camelCase имени в `@Value`, а также Origin победившего свойства. Все адреса
банков учебные. `snapshot` меняет PropertySource уже созданного контекста:
Environment читает новое значение, record сохраняет прежнее.

Ожидаемые ошибки старта перехвачены, чтобы напечатать их вид. Поэтому демонстрация
завершается кодом 0 даже при ошибке. Рабочее приложение с такой конфигурацией
запуск не завершит. Для обязательного отсутствующего import Boot дополнительно
пишет диагностику в stderr; на странице приведён фрагмент собственного stdout.
YAML и Bean Validation обеспечивают SnakeYAML, Hibernate Validator и реализация
EL из Tomcat. Их версии заданы BOM Spring Boot 4.1.1.
