# Java: What's New

Тема: [Java › Modern Java › What's New](../../topics/java-versions.html).
Все Java-демо используют только JDK. HttpClientDemo поднимает собственный
HTTP-сервер на случайном loopback-порту и закрывает сервер и клиент после опыта.

Из корня базы, укажи каталоги JDK 21 и 25:

    python3 examples/java-versions/verify.py \
      --java21-home /path/to/jdk21 \
      --java25-home /path/to/jdk25 \
      --evidence-dir /tmp/java-versions-evidence

`/path/to/jdk21` и `/path/to/jdk25` — заменяемые примеры путей, не реальные каталоги.
Без `--java21-home` драйвер использует `~/.sdkman/candidates/java/current`.
Результаты проверены на JBR 21.0.11+10-1163.116-jcef и OpenJDK 25.0.2+10-69.
JDK 25 для проверки скачана из официального архива OpenJDK во временный каталог;
SHA-256 архива для macOS/AArch64:
`7581b0d1752cd5acbf39e286c03f07b6cd6c205b562eb2fe753ff0253cf4c1bf`.

Драйвер компилирует и запускает код во временном каталоге, проверяет результаты
через AssertionError и точный stdout. Отдельные subprocess-пробы сверяют код
возврата компилятора/JVM, диагностику и major/minor class-файла. Исходники базы
не меняются. Отклонение API для `--release 8` проверено по сигнатурам платформы
компилятором 21; сама JVM 8 не запускалась.

- VarSwitchDemo: выбор overload по выведенному локальному типу, diamond без
  target type, yield и содержимое text block.
- DataPatternsDemo: exhaustive pattern switch и record с общей mutable-ссылкой
  против defensive snapshot. Копия списка не делает mutable elements immutable.
- SequencedDemo: reverse view пишет в исходный список; отдельный snapshot не
  меняется и отвергает мутацию.
- HttpClientDemo: реальный ответ 500 успешно получается через send и sendAsync.
- VirtualThreadsDemo: 12 virtual tasks, Semaphore с двумя разрешениями, barrier
  обеспечивает пересечение двух держателей. Измерено ограничение ресурса, не RPS
  и не количество carrier threads; настоящая БД не подключается.
- ScopedValuesDemo: A/B/A после исключения, plain virtual child без binding,
  завершение внешней привязки. StructuredTaskScope здесь не используется.
- Java25Syntax и CompactReceipt: module import, instance main, constructor
  prologue, compact source file; все без preview-флагов на 25.
- RuntimeFloor, ApiFloor и Preview21: разные границы syntax/API/class/runtime.

Дополнительно проверены `UseCompactObjectHeaders=false` на OpenJDK 25.0.2 и его
доступность как product-флага. При проверке JVM-флагов использовался
`-XX:-IgnoreUnrecognizedVMOptions`, чтобы JBR не скрывала неизвестную опцию.
Изменения JDK 27 описаны по первичным JEP; runtime 27 не запускалась.
TLS, HTTP/2, реальные банк/БД, preview Structured Concurrency, FFM-вызовы native
кода, AOT-производительность и нагрузочные характеристики приложения не проверены.
