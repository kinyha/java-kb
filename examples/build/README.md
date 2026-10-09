# Maven & Gradle: конфигурация → classpath → запуск

Проверено на JDK 21.0.11 (JBR), Maven 3.9.16, Gradle 9.6.1, macOS arm64.
JUnit Jupiter 5.11.4 / Platform Launcher 1.11.4; версии Maven-плагинов в pom.xml.
Здесь один комплект Java-исходников, которым пользуются обе системы сборки.

Из корня Java KB:

```bash
python3 examples/build/verify.py --work-dir /tmp/java-kb-build-review
```

Каталог должен быть новым: существующие файлы скрипт не удаляет. Без `--work-dir`
он работает во временном каталоге и удаляет его после завершения. JDK берётся из
`JAVA_HOME` или `~/.sdkman/candidates/java/current`, инструменты — из PATH.
Переопределения: `MAVEN_CMD` и `GRADLE_CMD` (путь к исполняемому файлу).
Первому запуску нужна сеть для pinned Maven-плагинов и JUnit из Maven Central.

Драйвер сохраняет `project/` — рабочую копию с `fixture-repo`, Maven `target`,
Gradle `build` — и `logs/` с stdout/stderr каждой команды. Maven использует свой
пустой `m2` внутри рабочего каталога. Gradle использует обычный пользовательский
кеш, но уникальный файловый репозиторий содержит собственные неизменные фикстуры.
Build cache и configuration cache в опытах выключены явно. Сборка не меняет
исходники KB и не публикует артефакты в удалённый репозиторий.

`fixtures/v1` и `fixtures/v2` определяют один FQCN MoneyApi. V2 сохраняет currency
и добавляет minorUnits. LegacyAdapter компилируется с v1, NewAdapter — с v2.
Драйвер вызывает javac --release 21 и jar, создаёт соответствующие Maven POM
и checksum-файлы. App видит оба адаптера, но не вызывает MoneyApi напрямую.
Его unit-тесты не исполняют этот путь; тесты domain проверяют расчёт комиссии.

Что проверяется реальными инструментами:

- Maven reactor собирает domain раньше app вопреки порядку modules.
- Verify выполняет два JUnit-теста domain. App сам тестов не содержит.
- Отдельный app verify до install не находит domain; tree может вывести частичный
  граф с предупреждением о его недостающем POM. После install артефакт доступен.
- Maven default выбирает API 1.0, запуск падает с NoSuchMethodError. Обратный порядок
  двух dependencies и профиль align выбирают 2.0, обе JVM печатают new=2.
- Обычный app JAR содержит App.class и не содержит Fee.class. Запуск только с этим
  JAR падает с NoClassDefFoundError, а не со смешанным конфликтом версий.
- Прямой compiler:compile после clean не создаёт отчётов тестов и JAR.
- Gradle выбирает 2.0 по умолчанию и с обычной constraint 1.0. Strict 1.0 выбирается,
  но снова ломает вызов адаптера: разрешение метаданных не проверяет ABI.
- Корневой build проверяет оба проекта. Выбор :app:build тянет domain.jar, но
  dry-run не включает domain.test. Повторный корневой build сообщает UP-TO-DATE.
- В lifecycle dependsOn включает prepare, mustRunAfter не включает его.
- В visibility implementation не добавляет money-api в compile classpath client,
  но добавляет в runtime. Api добавляет в оба. Проекты здесь без Java-кода:
  этот опыт проверяет только разрешённые конфигурации.
- JDK 21 с -source/-target 17 разрешает List.getFirst(); --release 17 отвергает его.
  Запуск такого байткода на Java 17 не выполнялся: опыт доказывает границу компилятора.

Полные логи включают служебные строки и пути; на странице указаны выбранные
неизменённые строки. Строки разных запусков разделены пустой строкой.
Репозиторий фикстур локальный; deploy, удалённый build cache, wrapper-bootstrap и
Failsafe с внешней средой не запускались. Их роль объясняется по документации,
без выдуманного лога. Бинарная воспроизводимость архивов не измерялась.
