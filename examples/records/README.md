# Records

JDK 21, без зависимостей. Полный запуск из корня репозитория:

```bash
java examples/records/RecordsDemo.java
java examples/records/AliasingDemo.java
java examples/records/EqualityDemo.java
java examples/records/SerializationDemo.java
```

Байткод: компилируй во временную папку, а не в репозиторий.

```bash
mkdir -p /tmp/java-kb-records-classes
javac -d /tmp/java-kb-records-classes examples/records/RecordsDemo.java
javap -p -c -classpath /tmp/java-kb-records-classes 'RecordsDemo$Normalized'
javap -p -c -classpath /tmp/java-kb-records-classes 'RecordsDemo$Receipt'
```

## Карта покрытия

- Контракт прозрачных данных; заголовок, поля, canonical constructor, accessors,
  equals/hashCode/toString; Java 16 — шаг contract, RecordsDemo, карточка what-is.
- Compact constructor: параметры до неявных присваиваний, нормализация, ошибка
  чтения через accessor, дополнительные конструкторы — шаг construction,
  RecordsDemo, javap, карточка constructor.
- Shallow immutability, defensive copy, mutable элементы и ключ HashSet — шаг
  ownership, AliasingDemo, карточка immutable.
- Одинаковый тип, reference-component equals, массивы, double, BigDecimal,
  copy invariant и переопределение accessor — шаг equality, EqualityDemo и ревью.
- final, extends Record, implements, generics, вложенные и local records,
  запрет дополнительных instance fields, reflection — шаг contract и ограничения.
- DTO и value object против entity, API эволюция и toString с секретами,
  Java Serialization через canonical — шаг boundaries, SerializationDemo,
  карточка usage. JSON-мэппинг зависит от выбранного маппера и его версии;
  JPA lifecycle остаётся в jpa, validation в spring-validation.

Основа подачи: Dev.java Using Records to Model Immutable Data — сначала данные и обещания заголовка, затем
конструирование и копирование. Не переносим представление «record immutable»
на граф изменяемых компонентов. JLS 21 §8.10 и java.lang.Record задают контракт;
вывод и байткод получены на JBR 21.0.11. Набор уточнений сверен с
Hruday-Boppa/Core-Java-Interview-Questions-List §20.5: определение, generated
methods, canonical и custom constructors, immutability, наследование, методы,
static и instance fields, области применения. Ситуации и демо разработаны здесь.
