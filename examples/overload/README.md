# Overload demo

Пример к теме System Design › Reliability › Overload. JDK 21, без зависимостей.

```bash
java examples/overload/OverloadDemo.java
```

Сервер — JDK HttpServer с пулом из 8 потоков; клиенты — открытый поток 1 200 запросов в секунду. Числа немного меняются от запуска к запуску; порядок результатов повторяется.
