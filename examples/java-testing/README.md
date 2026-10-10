# Java: Testing

Тема: [Java › Tooling › Testing](../../topics/java-testing.html).
Нужны JDK 21, Maven и работающий Docker. Проверено на JBR 21.0.11,
Maven 3.9.16, Docker Engine 29.8.0 (Desktop 4.92.0).

Зависимости зафиксированы: JUnit Jupiter 5.11.4 / Platform 1.11.4,
Mockito 5.18.0, AssertJ 3.27.3, Testcontainers 2.0.5,
PostgreSQL JDBC 42.7.7, образ postgres:17.11-alpine.
Это версии воспроизводимого эксперимента, а не заявление об их новизне.

Из корня репозитория:

    python3 examples/java-testing/verify.py --work-dir /tmp/kb-java-testing-run

Каталог не должен существовать. По умолчанию выбирается новый временный каталог.
JDK и Maven берутся из sdkman current; на другой машине передай
--java-home /path/to/jdk21 --maven /path/to/mvn.
Весь проект копируется туда, логи и XML-отчёты находятся внутри копии.
Драйвер не меняет production-исходники репозитория.

В подготовленной копии:

    mvn test    # Surefire: 27 unit invocation, PostgreSQL IT не запускается
    mvn verify  # unit + Failsafe: 3 PostgreSQL integration tests

POM явно включает Failsafe integration-test/verify и failIfNoTests.
Mockito Core подключён как startup javaagent к тестовым JVM:
Dependency Plugin properties раскрывает путь к JAR. Пустая argLine нужна
для корректной поздней подстановки Surefire/Failsafe. При запуске тестов
из IDE отдельно настрой такой же agent; Maven JVM и тестовая forked JVM разные.

- FeePolicyTest: семь точных граничных случаев, включая long max,
  два недопустимых входа и один намеренно слабый тест.
- LifecycleTest / SharedLifecycleTest: PER_METHOD и PER_CLASS, parameterized
  invocation, before/after callbacks и живущее дольше экземпляра static-поле.
  Порядок назначен только в LifecycleTest ради стабильной обучающей трассы.
- AssertionsTest: BigDecimal equality, ordered/any-order коллекция,
  отложенные ошибки SoftAssertions и ThreadLocal при preemptive timeout.
- PaymentServiceTest: реальные FeePolicy и fixed Clock, mock банка,
  fake-хранилище ArrayList. Четыре исхода: approval, validation, decline, timeout.
- MockitoTrapsTest: default false, unused strict stub, spy stubbing,
  all-matchers и ArgumentCaptor, final класс с inline maker/startup agent.
- PaymentRepositoryIT: маленькая SQL-проба с реальным PreparedStatement,
  INSERT RETURNING, SQLState 23505/23514, изоляция READ COMMITTED,
  два соединения и rollback, TRUNCATE перед каждым методом. Это проверка SQL/JDBC,
  а не запуск всего PaymentService с production repository.
- DiscoveryProbe: driver убирает только Jupiter Engine JAR, сохраняя API/launcher.
  Ошибка обнаруживается при discover: само получение launcher может быть ленивым.

В конце driver создаёт отдельную mutant-копию и меняет ceiling на floor.
Слабая isPositive-проверка проходит. Семь точных случаев дают пять failures —
эта красная сборка намеренная, и driver проверяет результат по XML.

Static @Container живёт в рамках тестового класса; после Failsafe контейнер
останавливается, Ryuk очищает вспомогательные ресурсы. Тесты последовательные,
reuse не включён. Образ может скачаться при первом запуске. Отсутствие Docker
даёт ошибку, не тихий skip. Не проверялись настоящий банк, его HTTP-контракт,
production сеть, параллельный запуск этих SQL-тестов или кластерная БД.
