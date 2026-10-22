# Coroutines: опыты

Запуск из корня Java KB:

```bash
examples/kotlin-coroutines/run.sh
```

Нужны JDK 21, `kotlinc` 2.x в `PATH` и `kotlinx-coroutines-core-jvm-1.11.0.jar`: скрипт ищет его в кешах Gradle и Maven, путь можно задать через `COROUTINES_JAR`.
Проверено на kotlinc 2.4.10, kotlinx-coroutines 1.11.0, JBR 21.0.11, Mac с 18 CPU.
Времена в выводе зависят от машины: число волн в опыте с `Dispatchers.Default` равно `ceil(100 / CPU)`.

| Файл | Что доказывает | Шаг темы |
|---|---|---|
| `Problem.kt` | 10 000 авторизаций с двумя параллельными вызовами по 100 и 80 мс на одном потоке `main` | 1 |
| `Mechanism.kt` | suspend без kotlinx.coroutines: функция возвращает `COROUTINE_SUSPENDED`, возобновляется из другого потока; state machine в `javap` | 2 |
| `Structure.kt` | падение одного ребёнка отменяет соседей; некооперативный код не замечает таймаут; `catch (e: Exception)` глотает отмену | 3 |
| `Dispatching.kt` | `Thread.sleep` на `Default`, `IO`, виртуальных потоках и `delay`: время, число потоков, задержка health-check | 4 |
| `Errors.kt` | `async` без `await`, `supervisorScope`, scope сервиса с `Job()` и `SupervisorJob()` | 5 |
| `Flows.kt` | cold flow выполняется на каждый `collect`, backpressure, conflation в `StateFlow`, потеря события в `SharedFlow` | 6 |
| `Interop.kt` | `CompletableFuture.await()`, `future { }`, отмена корутины отменяет future | 7 |
