# Date & Time

JDK 21, без зависимостей. Из корня репозитория:

```bash
java examples/date-time/TimelineDemo.java
java examples/date-time/ResolutionDemo.java
java examples/date-time/CalendarDemo.java
java examples/date-time/FormattingDemo.java
java examples/date-time/ClockDemo.java
```

Проверено на JBR 21.0.11, встроенная TZDB 2026a. TimelineDemo печатает версию
через ZoneRulesProvider; на другой версии базы проверь правила, а не подгоняй
вывод. Даты переходов фиксированы: Europe/Berlin, 31 марта и 27 октября 2024.
Текущее время и default zone не влияют на вывод. Все пять программ запущены.

## Карта покрытия

Центральный вопрос: как превратить время встречи в определённый момент и
сохранить его смысл при переносе, расчёте и записи? Тип — концепция. Семь шагов:

1. Одинаковое 02:30 соответствует двум моментам — incident, TimelineDemo,
   пошаговая схема, карточка instant-local.
2. Поля календаря, Instant, region ZoneId vs fixed ZoneOffset, преобразования
   sameInstant/sameLocal, equals vs isEqual/compareTo — model, TimelineDemo,
   instant-local и zones.
3. Ноль/один/два offsets, gap/overlap, сдвиг по atZone, explicit offset и ofStrict,
   отклонение неоднозначного ввода — resolution, ResolutionDemo, схема gap,
   zones; ofLocal/resolveLocal — deep по исходникам OpenJDK 21.
4. Calendar vs elapsed arithmetic, Period/Duration, DST 23/25 часов,
   getDays vs total days, plusMonths clipping и якорь повторения,
   TemporalAdjusters — arithmetic, CalendarDemo, amounts.
5. Формат vs смысл, два этапа parsing, SMART/STRICT/LENIENT, u/y/Y,
   Locale, ISO transfer with offset, immutable thread-safe formatter — formatting,
   FormattingDemo, formatting-traps.
6. Clock dependency, deadline equality, fixed clock, зона Clock,
   nanos vs actual resolution, monotonic nanoTime для замера — clock, ClockDemo;
   подробные unit tests отданы java-testing.
7. Прошедший факт vs будущая локальная договорённость, TZDB changes, запрос
   локального дня через half-open instant bounds, epoch units, Date round trip
   теряет nanos — boundaries, CalendarDemo/ClockDemo, senior в карточках.
   JDBC column/timezone mapping — jdbc; JSON schema/serializer — serialization;
   планировщик, holidays, FX calendars и alternate chronologies вне scope.

## Исследование подачи

Полностью прочитан официальный курс Cay Horstmann, Advanced Java LiveLessons,
Lesson 9 Date and Time, основанный на Core Java Volume II. Порядок курса:
timeline/Duration → LocalDate/adjusters → LocalTime → zone rules/DST → legacy.
Наглядны пропущенное Berlin 02:30 и различие семи календарных дней с Duration.
Здесь начинаем с overlap встречи, затем разделяем поля и момент и расширяем
до явного контракта разрешения, строгого ввода и границ хранения. Сохраняем
механизм календарной арифметики; замер алгоритма через wall-clock из курса
заменяем рекомендацией nanoTime по контракту System Java 21. Не переносим
упрощённое отождествление SQL Timestamp и Instant в JDBC-рекомендацию.

Java Interview Handbook, раздел Date-Time API, использован для отбора вопросов
Instant/LocalDateTime, Duration/Period и thread safety formatter. Его общий совет
«все timestamps в UTC» уточнён: будущая договорённость в местном времени должна
сохранить исходные поля и регион. Технические ответы проверены по Java 21 API,
ZonedDateTime.java в OpenJDK 21u и собственным запускам.

## Проверенные границы

- Overlap различает два instants на час; для gap atZone изменяет LocalDateTime.
- OfStrict отвергает gap и несовместимый offset. RequireUnique отвергает gap
  и overlap, но пропускает normal; это выбранная политика демо.
- Period одного дня даёт 23/25 elapsed hours, Duration — 24.
- SMART принимает 2023-02-29 как 2023-02-28; STRICT отвергает. Без era yyyy
  при STRICT не собирает даже корректный LocalDate. Week-year зависит от locale.
- Deadline проверен непосредственно до границы и на ней, без sleep.
- Legacy Date теряет 456789 ns; epoch millis, ошибочно прочитанные как seconds,
  дают год 56791. Интервал локального дня длится 23 часа.

На странице нет неподтверждённого JDBC/SQL опыта. Наносекундная точность
представления не является измерением точности реальных системных часов.
