# Reactive demos

Запускаемые примеры к темам папки Spring › Reactive. Нужны JDK 21 и Maven.
Зависимости зафиксированы в `pom.xml`: Reactor Core и Reactor Test 3.8.7.

```bash
cd examples/reactive
./run.sh PhilosophyDemo        # все части
./run.sh PhilosophyDemo 2 3    # только части 2 и 3
```

Скрипт собирает classpath через Maven, компилирует файл в `target/<Demo>` и запускает. Дополнительные флаги JVM передаются через `JAVA_OPTS`.

| Файл | Тема | Что показывает |
|---|---|---|
| `PhilosophyDemo.java` | Reactive Philosophy | 1 — асинхронная отправка без спроса копит строки в памяти; 2 — свой `Flow.Publisher` читает курсор только по `request(n)`; 3 — наивный издатель падает с `StackOverflowError`, цикл drain нет; 4 — нарушения §3.9 и §2.13 в Reactor; 5 — какой `request` доходит до источника через `publishOn` и `limitRate`; 6 — `Flux.interval` и `OverflowException`; 7 — `Mono.just` против `fromCallable`, `switchIfEmpty` с жадным аргументом; 8 — cold и hot; 9 — блокирующий `SubmissionPublisher.submit` |
| `OperatorsDemo.java` | Reactor Operators | на виртуальном времени: 1 — `map` против `flatMap`; 2 — `flatMap`, `flatMap(f, 2)`, `concatMap`, `flatMapSequential`; 3 — `zip` с пустым источником, `switchIfEmpty`, `merge` и `concat`; 4 — `onErrorResume`, `onErrorMap`, `retry` над `fromFuture`; 5 — `Retry.backoff` и место `timeout`; 6 — `cache` с ошибкой и TTL, `share`, `replay`, варианты `Sinks`; 7 — `StepVerifier`, `withVirtualTime` и издатель, собранный снаружи |
