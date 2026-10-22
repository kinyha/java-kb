# Kotlin for Java Devs: опыты

Запуск из корня Java KB:

```bash
examples/kotlin-for-java/run.sh
```

Нужны JDK 21 и `kotlinc` 2.x в `PATH`. Проверено на kotlinc 2.4.10 и JBR 21.0.11.
Для опыта с JSpecify скрипт ищет `jspecify-1.0.0.jar` в кешах Gradle и Maven, путь можно задать через `JSPECIFY_JAR`.

| Каталог | Что доказывает | Шаг темы |
|---|---|---|
| `boundary/` | где Kotlin бросает NPE: присваивание platform type, выведенный тип, `!!`, `lateinit`, вызов из Java с null | 1, 3 |
| `initorder/` | NPE в чистом Kotlin: open-метод из конструктора базового класса | карточка про null safety |
| `bytecode/` | что видит Java: свойства, конструктор с маской аргументов по умолчанию, `companion`, `@JvmStatic`, `object`, top-level функции | 2 |
| `jspecify/` | JSpecify-аннотации превращают platform type в настоящий тип: две ошибки компиляции | 3 |
| `data/` | data class в `HashSet`, поля из тела класса, `copy`, `@JvmRecord`; sealed и `when`, `PermittedSubclasses` при jvm-target 21 и его отсутствие при 1.8 | 4 |
| `functions/` | статический выбор extension-функции, член выигрывает у extension, `inline` и `reified` в байткоде | 5 |
| `limits/` | read-only список меняется из Java, `listOf` из одного и многих элементов, final по умолчанию | 6 |

`kotlinc` без `-jvm-target` собирает байткод Java 8 (major 52). В Gradle с `kotlin { jvmToolchain(21) }` плагин ставит jvmTarget 21.
