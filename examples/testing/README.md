# Testing demos

Пример к теме Spring › Testing. Нужны JDK 21, Maven и запущенный Docker.

```bash
cd examples/testing
mvn test                                                              # четыре класса A–D по алфавиту
mvn test -Dlogging.level.org.springframework.test.context.cache=DEBUG # статистика кеша контекстов
```

- `A_PaymentControllerTest` — срез `@WebMvcTest`, `MockMvcTester`, `@MockitoBean` сервиса.
- `B_RepositoryWriteTest` и `C_RepositoryReadTest` — одна конфигурация, один контекст из кеша, откат `@Transactional`.
- `D_PaymentFlowTest` — полный путь HTTP → база с mock банка; отдельный контекст и контейнер.

PostgreSQL 17 поднимает Testcontainers 2 через `@ServiceConnection` (`Containers.java`).
Время в выводе зависит от машины и того, скачан ли образ.
