# Reactive demos

Запускаемые примеры к темам папки Spring › Reactive. Нужны JDK 21 и Maven.
Зависимости зафиксированы в `pom.xml`: Reactor Core, Reactor Test и Reactor Tools 3.8.7, BlockHound 1.0.18, Micrometer Context Propagation 1.2.1, Logback 1.5.19 (`logback.xml` печатает поток и traceId из MDC).

```bash
cd examples/reactive
./run.sh PhilosophyDemo        # все части
./run.sh PhilosophyDemo 2 3    # только части 2 и 3
```

Скрипт собирает classpath через Maven, компилирует файл в `target/<Demo>` и запускает. Дополнительные флаги JVM передаются через `JAVA_OPTS`.

`PitfallsDemo` запускают по одной части: `blockhound`, `debug-hook` и `debug-agent` меняют поведение JVM до конца процесса. BlockHound на JDK 13+ требует флаг:

```bash
JAVA_OPTS=-XX:+AllowRedefinitionToAddDeleteMethods ./run.sh PitfallsDemo blockhound
```

| Файл | Тема | Что показывает |
|---|---|---|
| `PhilosophyDemo.java` | Reactive Philosophy | 1 — асинхронная отправка без спроса копит строки в памяти; 2 — свой `Flow.Publisher` читает курсор только по `request(n)`; 3 — наивный издатель падает с `StackOverflowError`, цикл drain нет; 4 — нарушения §3.9 и §2.13 в Reactor; 5 — какой `request` доходит до источника через `publishOn` и `limitRate`; 6 — `Flux.interval` и `OverflowException`; 7 — `Mono.just` против `fromCallable`, `switchIfEmpty` с жадным аргументом; 8 — cold и hot; 9 — блокирующий `SubmissionPublisher.submit` |
| `OperatorsDemo.java` | Reactor Operators | на виртуальном времени: 1 — `map` против `flatMap`; 2 — `flatMap`, `flatMap(f, 2)`, `concatMap`, `flatMapSequential`; 3 — `zip` с пустым источником, `switchIfEmpty`, `merge` и `concat`; 4 — `onErrorResume`, `onErrorMap`, `retry` над `fromFuture`; 5 — `Retry.backoff` и место `timeout`; 6 — `cache` с ошибкой и TTL, `share`, `replay`, варианты `Sinks`; 7 — `StepVerifier`, `withVirtualTime` и издатель, собранный снаружи |
| `PitfallsDemo.java` | Reactive Pitfalls | `block` — `block()` в неблокирующем потоке падает только при ожидании; `blockhound` — `map(jdbc)`, `Mono.just(jdbc()).subscribeOn`, `fromCallable`; `forgotten` — `doOnNext(save)` и `PublisherProbe`; `nested` — `subscribe()` внутри `flatMap`: ошибка, Context, отмена; `context` — место `contextWrite`, MDC с `enableAutomaticContextPropagation`; `flatmap` — 1000 платежей и банк на 20 запросов; `plain`, `checkpoint`, `debug-hook`, `debug-agent` — один и тот же стектрейс |
