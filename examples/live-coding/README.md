# Live Coding

JDK 21, без сборки и зависимостей. Из корня репозитория:

```bash
java examples/live-coding/FirstRepeatDemo.java
java examples/live-coding/PairDemo.java
java examples/live-coding/OracleDemo.java
java examples/live-coding/FirstRepeat.java
java examples/live-coding/RefundPair.java
java examples/live-coding/MergeActivity.java
java examples/live-coding/BurstWindow.java
java examples/live-coding/DependencyOrder.java
```

Три демо дают 7 блоков вывода. Методы с wrong намеренно нарушают условие.
OracleDemo сравнивает 1093 массива длины 0..6 над алфавитом A/B/C с независимым
перебором. Это конечная проверка, не доказательство для произвольного входа.
Счётчики FirstRepeatDemo — число вызовов сравнения/вставки, не измерение времени.

Пять задач — готовые решения с 44 проверками; ошибка всегда вызывает
AssertionError, запуск с -ea тоже поддерживается. TODO и полная обвязка
заданий есть на странице. У всех вход не меняется, null вне условий.

FirstRepeat: непустые строковые ID, сравнение по содержимому, индекс первой
повторной доставки, -1 при отсутствии. Пустой массив разрешён.
RefundPair: int-суммы, int target, два различных индекса; минимальный j,
при нём минимальный i. Сумма математическая, переполнение int не допустимо.
MergeActivity: непустые полуоткрытые интервалы long, start<end; объединение
склеивает и перекрытие, и касание. record неизменяем, сортируется копия.
BurstWindow: неотрицательные long timestamps уже отсортированы, width>0;
полуоткрытое окно [s,s+width), правая граница исключена. Разность безопасна
при этих условиях, сложение s+width не используется.
DependencyOrder: n 0..10000, m 0..50000, endpoints 0..n-1; повторные рёбра
разрешены. Наименьший лексикографический топологический порядок, цикл —
Optional.empty; пустой граф — Optional.of(empty list).

Вывод снят на JBR 21.0.11. Порядок хеш-коллекций не печатается и не влияет
на выбор ответа. Все проверочные сообщения фиксированы кодом.
