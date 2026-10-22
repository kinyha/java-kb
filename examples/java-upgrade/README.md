# Java Upgrade: что ломается при переходе на новую JDK

Примеры к теме Java › Modern Java › Java Upgrade.
Проверено на JDK 21.0.11 (JBR, sdkman) и JDK 25.0.3 (JBR из IntelliJ IDEA 2026.2), macOS arm64.

```bash
examples/java-upgrade/run.sh
```

Пути к JDK задаются переменными `J21` и `J25` (каталоги `bin`), локальный Maven-репозиторий — `M2`.
Сеть не нужна: Spring 7.0.9, ASM 9.7.1/9.8, jakarta.xml.bind-api 4.0.5 и annotation API
берутся из `~/.m2`. Временные классы и JAR скрипт кладёт в `mktemp -d` и удаляет.

| Каталог | Что доказывает |
|---|---|
| `jaxb/` | библиотека, собранная `--release 8`, запускается на 21 и падает с `NoClassDefFoundError` только при первом вызове; `--release 11` не компилирует; `jdeps` показывает `not found`; jakarta.xml.bind-api 4.x не закрывает пакет `javax.xml.bind` |
| `classversion/` | класс от javac 25 не запускается на 21 (`UnsupportedClassVersionError`), с `--release 21` запускается; ASM 9.7.1 не читает class-файл 69 ни с диска, ни `java/lang/String` из JDK 25 |
| `encapsulation/` | глубокая рефлексия в `java.math` даёт `InaccessibleObjectException`; `--add-opens` и `Add-Opens` в манифесте (только для `java -jar`) открывают пакет |
| `integrity/` | `sun.misc.Unsafe::allocateMemory` молчит на 21 и предупреждает на 25; `--sun-misc-unsafe-memory-access=deny` бросает исключение; `-Djava.security.manager=allow` работает на 21 и не даёт стартовать JVM 25; `jdeprscan --for-removal` |
| `behavior/` | CLDR ставит U+202F перед AM/PM, разбор строки с обычным пробелом падает; `COMPAT` работает на 21 и игнорируется на 25; `file.encoding=COMPAT`; Serial при одном CPU |
| `spring/` | Spring 7.0.9 не вызывает `@javax.annotation.PostConstruct`, вызывает `@jakarta.annotation.PostConstruct` |
| `mrjar/` | multi-release JAR: на 21 и 25 загружается `META-INF/versions/21`, с выключенным MR — базовая версия |

Что не запускалось: JDK 8, 11, 17, 26 и 27 локально нет. Поведение 8/11/17 и переход
на G1 по умолчанию в 27 (JEP 523) сверены по JEP и исходникам `gcConfig.cpp` тега `jdk-27-ga`;
дефолт `--sun-misc-unsafe-memory-access=warn` в 26 и 27 — по `sun/misc/Unsafe.java` тех же тегов.

JBR 21 по умолчанию ставит `IgnoreUnrecognizedVMOptions=true`, обычная OpenJDK и JBR 25 — `false`.
Поэтому опыты с флагами запускаются с `-XX:-IgnoreUnrecognizedVMOptions`.

Номера потоков (`VirtualThread[#28]`) и хеш модуля (`unnamed module @5e4c8041`) меняются от запуска к запуску.
