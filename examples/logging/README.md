# Logging demos

Страница: [Logging](../../topics/logging.html). Проверено на JBR/JDK 21.0.11,
SLF4J API 2.0.17 и Logback Classic/Core 1.5.18. Это фиксированные версии
эксперимента, а не рекомендация использовать их как последние доступные.

Из корня репозитория:

```bash
python3 examples/logging/verify.py --logs /tmp/kb-logging-logs
```

По умолчанию JDK и Maven берутся из `~/.sdkman/candidates/*/current`.
На другой машине передай `--java-home /path/to/jdk21 --maven /path/to/mvn`.
Maven Dependency Plugin 3.8.1 получает зависимости из Maven Central,
`javac --release 21` компилирует файлы в временный каталог. В репозитории
не появляются class-файлы, target или логи. stdout и stderr сохраняются отдельно.

- `ProviderDemo`: запускается только с API JAR, реальный NOP и предупреждения.
- `RoutesDemo`: наследование DEBUG, root ERROR, фильтр appender, двойная доставка
  одному appender и остановка additivity.
- `LazyDemo`: счётчики вызова аргумента/форматирования, Supplier, guard,
  незавершённый fluent builder и сохранение причины исключения.
- `MdcDemo`: однониточный пул с явным порядком через Future.get, утечка,
  отсутствие наследования у пула и нового virtual thread, снимок при submission,
  восстановление worker scope после исключения и вложенный putCloseable.
- `JsonDemo` + `logback-json.xml`: реальный native JsonEncoder. Python JSON parser
  проверяет одну строку, экранирование переноса/кавычек, MDC, KVP и их типы.
  Нестабильные timestamp/thread/context отключены только для повторяемого вывода.
- `AsyncDemo`: CountDownLatch держит sink, чтобы точно проверить заполнение
  очереди, низкоприоритетный discard, блокировку в ArrayBlockingQueue.put,
  offer/drop и копию MDC. Перед stop gate освобождается и ожидается доставка.
  Пять секунд — дедлайн зависшего теста, не измерение производительности.
- `Support`: очистка глобального LoggerContext и appender, который немедленно
  читает сообщение/причину; в async отдельный Gate фиксирует MDC после передачи.

Утверждения внутри программ проверяют причины результата; driver дополнительно
проверяет NOP и JSON. Не запускались реальный collector, rotation stress,
принудительное завершение JVM, повреждение диска или сбой внешней доставки.
Локальные события и успешный flush не доказывают долговечность бизнес-аудита.
