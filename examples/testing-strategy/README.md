# Testing strategy demo

Пример к теме Backend › Testing › Testing Strategy: покрытие JaCoCo против мутационного тестирования PIT. Нужны JDK 21 и Maven.

```bash
cd examples/testing-strategy
mvn -q test -Dtest=WeakFeeTest jacoco:report
cut -d, -f3,6-9 target/site/jacoco/jacoco.csv
mvn test-compile org.pitest:pitest-maven:mutationCoverage -DtargetTests=kb.fees.WeakFeeTest
mvn test-compile org.pitest:pitest-maven:mutationCoverage -DtargetTests=kb.fees.StrongFeeTest
```

Отчёт PIT: target/pit-reports/index.html.

Тест-двойники (тема Test Doubles). Хрупкий тест `overMocked_verifiesEveryCall` намеренно падает на V2 и по умолчанию исключён тегом `fragile`:

```bash
mvn test -Dtest=DoublesTest -Dfragile=none
python3 report.py
```

Контракт фейка: один набор правил для JDBC-репозитория (H2), исправленного и наивного фейка; наивный падает намеренно:

```bash
mvn test -Dtest='*ContractTest,*RepositoryTest' -Dfragile=none
python3 report.py JdbcPaymentRepositoryTest InMemoryPaymentRepositoryTest NaiveFakeContractTest
```
