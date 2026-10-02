# Data demos

Нужны JDK 21 и Maven. Зависимости управляются BOM Boot 4.1.1:
Framework 7.0.9, HikariCP 7.0.2 и H2 2.4.240. Внешняя база не нужна.

```bash
cd examples/data
./run.sh JdbcDemo
```

Скрипт компилирует выбранный файл с `--release 21 -parameters` и запускает
класс из пакета `kb.data`. `target/` игнорируется, логирование Hikari выключено
через slf4j-nop, чтобы вывод показывал только опыт.

JdbcDemo создаёт пул из одного физического соединения и ограничивает получение
второго 300ms. Пока первое занято, второе получение заканчивается исключением.
После close логическая оболочка закрыта, физическое соединение открыто, новый
заём использует новую оболочку над тем же физическим объектом H2. unwrap нужен
только для наблюдения и не переносится в рабочий платёжный код.

Далее создаются два счёта. Деньги — целые копейки: payer=10000, merchant=0.
Параметр с кавычками и OR остаётся значением id и возвращает пустую выборку.
JdbcTemplate отображает строку в record, отсутствие строки и повтор ключа
показывает через EmptyResultDataAccessException и DuplicateKeyException.

Перевод на 1000 копеек демонстрирует три исхода:

- При стандартном autoCommit первое списание уже зафиксировано, когда код
  выбрасывает ошибку до зачисления: payer=9000, merchant=0.
- На одном соединении с autoCommit=false ошибка приводит к явному rollback:
  payer=10000, merchant=0. После возврата Hikari выдаёт autoCommit=true.
- При успехе commit фиксирует обе записи: payer=9000, merchant=1000.

Ожидаемые ошибки перехватываются только для демонстрации. Демо не проводит
внешний платёж и не моделирует сетевой разрыв, изоляцию PostgreSQL или потерю
ответа на commit. Привязка соединений Spring-транзакции — тема spring-tx;
ручная JDBC-транзакция этого файла выполняет SQL напрямую через свой Connection.

## Spring Transactions

```bash
./run.sh TxDemo proxy
./run.sh TxDemo self
./run.sh TxDemo checked
./run.sh TxDemo checked-rollback
./run.sh TxDemo swallowed
./run.sh TxDemo required
./run.sh TxDemo new
./run.sh TxDemo nested
./run.sh TxDemo nested-outer
./run.sh TxDemo readonly
./run.sh TxDemo thread
./run.sh TxDemo template
```

TxDemo создаёт контекст Spring с JdbcTransactionManager, JdbcTemplate и прокси
PaymentService. Каждый запуск начинает с новой H2: счёт содержит 100 учебных
денежных единиц, платёж списывает 10. Эти суммы не используют данные JdbcDemo.

proxy откатывает запись; self обходит прокси и сохраняет SQL через autoCommit.
checked, checked-rollback и swallowed сравнивают правила отката. required даёт
UnexpectedRollbackException после внутреннего rollback-only; new сохраняет
независимый аудит; nested и nested-outer показывают разные исходы savepoint.
readonly показывает запись H2 при JDBC readOnly hint. thread проверяет отсутствие
транзакции в новом виртуальном потоке; ожидание ограничено 10s. template откатывает
запись явно через TransactionTemplate. Ошибки перехватываются только драйвером
опыта после выхода из прокси. Размер пула 4 позволяет получить второе соединение.

Общая основа зависимостей также включает Hibernate 7.4.5.Final, JPA 3.2,
Spring Data JPA/JDBC 4.1.1 и Caffeine JCache 3.2.4 для последующих демо Data.

## JPA Basics

```bash
./run.sh JpaDemo dirty
./run.sh JpaDemo merge
./run.sh JpaDemo query
./run.sh JpaDemo bulk
```

JpaDemo создаёт Hibernate SessionFactory и новый платёж с суммой 100.
dirty сравнивает два find, JDBC-чтение до и после flush, rollback и clear.
merge показывает отдельную managed-копию и позднюю правку исходного объекта.
query сравнивает скалярные JPQL-запросы при AUTO и COMMIT; результат COMMIT
показан именно для Hibernate 7.4.5.Final. bulk изменяет строку напрямую и
обновляет прежний объект через refresh. Второй уровень кеша выключен.

Чтение суммы через JDBC использует соединение текущей транзакции: оно обходит
память EntityManager и не вызывает автоматический flush JPQL. Счётчик UPDATE
наблюдает SQL через StatementInspector. Каждый запуск создаёт отдельную H2.

## Relations & Fetching

```bash
./run.sh RelationsDemo owning
./run.sh RelationsDemo orphan
./run.sh RelationsDemo closed
./run.sh RelationsDemo fetch
```

RelationsDemo создаёт три платежа трёх разных магазинов и зависимые возвраты.
owning сравнивает изменение обратной коллекции, одного владельца FK и обеих
сторон через moveTo. orphan показывает удаление возврата после исключения из
коллекции. closed обращается к ленивому магазину после закрытия контекста.
fetch читает один и тот же список через LAZY, fetch join и EntityGraph:
свежие контексты и выключенный L2 дают соответственно 4, 1 и 1 SELECT.

Счётчик StatementInspector наблюдает запросы Hibernate. JDBC-чтение FK
использует соединение текущей транзакции. Эскизы наследования, составного
ключа и двухэтапной пагинации на странице показывают отображение и подход;
отдельных режимов с их выводом в демо нет.
