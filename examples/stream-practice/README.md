# Stream Practice

JDK 21, без зависимостей. Каждую задачу можно запустить отдельно:

```bash
java PaidIds.java
java Totals.java
java Latest.java
java TopClients.java
java Products.java
java TopPayments.java
java ShapeDemo.java
```

Полные решения и проверки соответствуют заготовкам в теме. Проверки при несовпадении
бросают AssertionError, поэтому FAIL нельзя принять за успешное завершение.
TopPayments дополнительно сравнивает 100 входов с фиксированным seed с эталоном на циклах.
Суммы — целые minor units одной валюты, в условиях оговорено отсутствие переполнения long.
Входные списки, записи и строки в задачах не null; id в TopPayments уникальны.

## Карта покрытия и подача

- Контракт результата: форма, пустой вход, порядок, дубли, валюта — шаг contract и условия задач.
- filter → map → distinct, encounter order, toList — задача paid-ids.
- groupingBy и downstream, вложенная карта, summingLong — шаг reduction и задача totals.
- Повторный id, toMap и ассоциативная merge-функция — задача latest.
- Топ агрегатов, ничьи, limit после сортировки, разные валюты — задача top-clients.
- flatMap теряет владельца и пустые группы, downstream flatMapping — шаг flatten и задача products.
- Топ внутри группы и collectingAndThen, k=0, память и сложность — задача top-payments и шаг limits.
- Изменяемый внешний контейнер, parallel, reduce с mutable identity — границы;
  устройство Collector и ForkJoinPool остаются в collectors и parallel-streams.
- Денежная модель и округление — соседняя bigdecimal, SQL-агрегаты — темы SQL.

Основа подачи: Dev.java, Using a Collector as a Terminal Operation — переход от формы
результата к группе и downstream. Задачи авторские, на общем backend-домене, а не
выданные за реальные вопросы конкретной компании. Списки enhorse/java-interview
сверены для типов уточнений: порядок, distinct, collect/reduce, parallel, flatMap.
Контракты API проверены по Javadoc Java 21, результаты — запуском этих файлов.
