# Kotlin with Spring & JPA: опыты

Запуск из корня Java KB:

```bash
OFFLINE=1 examples/kotlin-spring/run.sh
```

Нужны JDK 21, `kotlinc` 2.x и Gradle. Gradle только собирает classpath из `classpath/build.gradle.kts`; опыты компилирует `kotlinc` напрямую, с плагинами all-open и no-arg из `$KLIB` или без них, чтобы было видно, что меняет каждый плагин.
Проверено: kotlinc 2.4.10, Spring Framework 7.0.9, Hibernate ORM 7.4.1, H2 2.4.240, Jackson 3.1.5, JBR 21.0.11. `OFFLINE=1` берёт зависимости только из локальных кешей.

| Каталог | Что доказывает | Шаг темы |
|---|---|---|
| `proxy/` | final-класс с `@Transactional` и final `@Configuration` падают на старте; open-класс с final-методом даёт NPE на пустом прокси; с пресетом `spring` всё работает | 1 |
| `jpa/` | без no-arg — `InstantiationException`; final-сущность превращает LAZY в EAGER (2 запроса); с all-open для `@Entity` — прокси и 1 запрос; data class как сущность теряется в `HashSet` и падает в `toString` | 2 |
| `jackson/` | Jackson 3 без Kotlin-модуля: без `-java-parameters` нет creator, с ним — NPE из проверки параметра вместо значения по умолчанию | 3 |
| `nulls/` | Spring 7 размечен JSpecify: `queryForObject` возвращает `T?` (ошибка компиляции ожидаема); nullable-зависимость необязательна только с kotlin-reflect | 4 |
| `jackson/` с `--kotlin-module` | с jackson-module-kotlin: значение по умолчанию подставлено, отсутствующий не-null id — `KotlinInvalidNullException` | 3 |
| `coroutines/` | suspend-контроллер в MockMvc: асинхронный запрос, продолжение в `kotlinx.coroutines.DefaultExecutor`, без coroutines-reactor — `NoClassDefFoundError`; `@Transactional` на suspend-функции с блокирующим менеджером коммитит до выполнения тела | 5 |

Шагам 3a и 5a нужны три jar с Maven Central, которых обычно нет в кеше: `tools.jackson.module:jackson-module-kotlin:3.1.5`, `org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.11.0`. Положи их в один каталог и передай его в `EXTRA_JARS`:

```bash
OFFLINE=1 EXTRA_JARS=/path/to/jars examples/kotlin-spring/run.sh
```
