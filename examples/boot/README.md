# Boot demos

Примеры к темам Spring › Boot. Нужны JDK 21 и Maven.
В `pom.xml` зафиксированы Spring Boot 4.1.1 и Spring Framework 7.0.9.

```bash
cd examples/boot
./run.sh AutoConfigDemo
./run.sh AutoConfigDemo opaque
./run.sh AutoConfigDemo report
```

`run.sh` компилирует выбранное демо с `--release 21 -parameters`, копирует `resources/`
в classpath для AutoConfigDemo и ConfigDemo и запускает класс из пакета `kb.boot`.
Внешние сервисы не нужны. Для ShutdownDemo также компилируется общий StartupDemo.

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

## Startup & Packaging

```bash
./run.sh StartupDemo
./run.sh StartupDemo --fail-warmup
./run.sh ShutdownDemo
./run.sh ShutdownDemo expire
cd packaging
mvn -q package
java -jar target/startup-demo-1.0.jar
```

StartupDemo запускает встроенный Tomcat на свободном порту. ApplicationRunner
удерживается защёлкой. Драйвер посылает настоящий HTTP-запрос до Ready и получает
200 с warm=false при readiness REFUSING_TRAFFIC. После отпускания runner тот же
контроллер отвечает warm=true, readiness становится ACCEPTING_TRAFFIC. В режиме
fail-warmup runner падает: Ready не публикуется, контекст закрывается.

ShutdownDemo удерживает начатый запрос внутри контроллера и закрывает контекст
параллельно. В обычном режиме банк отпускается в пределах пятисекундной фазы,
клиент получает 200, затем close завершается. В режиме expire срок фазы равен
200ms, запрос удерживается до окончания close: paymentFinished остаётся false.
После опыта защёлки освобождаются, драйвер завершает свои потоки и клиент.

Веб-демо не копируют общий AutoConfiguration.imports: соседняя учебная
BankAutoConfiguration не участвует в запуске. Компоненты зарегистрированы явно.
Shutdown hook выключен только в обвязке: драйвер закрывает контекст сам.
Логирование сервера выключено для читаемого вывода. Все ожидания имеют предел.

packaging/pom.xml использует родитель и Maven-плагин Boot 4.1.1. Компилируется
только StartupDemo, затем repackage создаёт executable jar с BOOT-INF и
Main-Class org.springframework.boot.loader.launch.JarLauncher. В начале опыта
напечатан загрузчик класса: AppClassLoader при classpath и LaunchedClassLoader
при java -jar. Потоку запуска явно передан загрузчик приложения, чтобы поиск
ресурсов работал и из вложенных jar.

Документированные команды CDS, AOT cache JDK и native-подготовка разобраны на
странице, но этим демо не собираются и не измеряются.
