# BigDecimal & Money

JDK 21, без сторонних зависимостей. Из корня репозитория:

```bash
java examples/bigdecimal/InputDemo.java
java examples/bigdecimal/RepresentationDemo.java
java examples/bigdecimal/RoundingDemo.java
java examples/bigdecimal/MoneyDemo.java
```

На JBR 21.0.11 проверены все четыре программы. RoundingDemo печатает два
порядка округления трёх строк по 0.015, восемь режимов на положительной и
отрицательной середине, overloads divide, MathContext, двойное округление и
неассоциативность округлённой add. MoneyDemo проверяет отказ для лишней дроби,
смешения валют и переполнения long. Распределение проверено на 103 случаях:
ноль, сумма меньше count, Long.MAX_VALUE и 100 воспроизводимых случайных входов.
Независимые проверки используют BigInteger для суммы, неотрицательность долей
и разницу максимум в единицу. Ошибка приводит к AssertionError.

Модель Money представляет settled amounts в единицах Currency metadata.
Это выбранный контракт демо, не универсальная финансовая политика. Разрешены
отрицательные Money для возвратов; equal-share allocation принимает только
неотрицательный total и count > 0. Порядок получателей остатка — порядок массива.
Не реализованы weighted allocation, FX и правила cash rounding.

## Карта покрытия и подача

Центральный вопрос: почему два сервиса с точным BigDecimal получают разные
итоги и какой контракт предотвращает расхождение? Сквозной пример — счёт с
дробными расчётными строками, переходящий к проводимой денежной сумме.

- Боль: per-line vs total rounding — incident, пошаговая схема, RoundingDemo,
  карточка rounding-boundary.
- Double, источники String/integer/valueOf, constructor(double), невозможность
  восстановить потерянные цифры, отсутствие NaN — input, InputDemo, why-decimal.
- Unscaled × 10^-scale, precision, leading/trailing zeros, negative scale,
  stripTrailingZeros/toString/toPlainString, immutability, preferred scales —
  representation, RepresentationDemo, схема пар и scale-precision.
- Equals/hashCode vs compareTo, HashSet vs TreeSet, зависимость divide от
  представления, нормализация денежного record — equality и money,
  RepresentationDemo/MoneyDemo, equals-compare.
- Exact vs nonterminating divide, overloads, MathContext precision vs scale,
  UNLIMITED, все восемь RoundingModes и отрицательные числа, двойное округление,
  non-associative contextual add — rounding, RoundingDemo, division и
  rounding-boundary; детали parallel reduce отданы parallel-streams.
- Валюта, граница settled amount vs rate/intermediate value, UNNECESSARY,
  longValueExact vs truncation, overflow, распределение остатка, SQL scale и
  точность JSON — money/boundaries, MoneyDemo, storage. JDBC/SQL не запускались: на странице
  нет SQL-демо, правила PostgreSQL 18 проверены по его документации. JSON round
  trip раскрыт в serialization, двоичное представление в data-repr.
- Compact long/BigInteger, bounded int scale и пределы ресурсов — deep в
  representation; внутренние алгоритмы BigInteger вне scope этой темы.

Прочитан Core Java Volume I, 14th edition, §3.9 в официальном отрывке InformIT:
переход от недостаточности primitive к созданию точного значения, методам
арифметики и сравнению. Сохраняем этот порядок причин, но начинаем со счёта и
расширяем до representation, границ округления и сохранения итогов. Не переносим
общее описание HALF_UP как школьного режима в рекомендацию для всех денег.
Java Interview Handbook используется только для отбора базовых вопросов;
технические факты проверены по Java 21 API, исходникам OpenJDK и запуском.
