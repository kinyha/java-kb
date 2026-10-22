# Value Objects (Project Valhalla)

Примеры требуют EA-билд JDK 28 с JEP 401 и JEP 539. Вывод в теме получен на
`openjdk version "28-ea" 2027-03-23`, build 28-ea+20-1588, macOS/AArch64
(jdk.java.net/28, архив сверен по sha256). На JDK 21 запускаются только
`SameCode.java`, `RecordThis.java` и `Prepare.java`: в них нет нового синтаксиса.

Полная проверка всех выводов и ошибок компиляции:

```bash
JAVA_KB_JDK28=/path/to/jdk-28/Contents/Home python3 examples/value-objects/verify.py
```

`JAVA_KB_JDK` задаёт JDK 21 для `Prepare.java` (по умолчанию
`~/.sdkman/candidates/java/current`). Class-файлы попадают во временную папку.

Отдельные запуски из корня репозитория:

```bash
java examples/value-objects/SameCode.java                      # без preview
java --enable-preview examples/value-objects/SameCode.java     # тот же исходник
java --enable-preview examples/value-objects/Equality.java
java --enable-preview examples/value-objects/Identity.java
java --enable-preview examples/value-objects/JdkClasses.java
java --enable-preview examples/value-objects/Construction.java
java examples/value-objects/RecordThis.java                    # с --enable-preview не компилируется
java --enable-preview examples/value-objects/Arrays.java
java --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout examples/value-objects/Layout.java
java --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout examples/value-objects/OrderLayout.java
java --enable-preview examples/value-objects/Scalarization.java
java --enable-preview -XX:CompileCommand=quiet -XX:CompileCommand=dontinline,*::plus examples/value-objects/Scalarization.java
java --enable-preview examples/value-objects/Predict.java
```

Отрицательные примеры (`negative/`) намеренно не компилируются с
`--release 28 --enable-preview`. `SyncDate.java` без preview компилируется с
предупреждением `[identity]`. `Larval.java` нужен только для `javap -v`:
javap из 28-ea+20 печатает кадр `early_larval` и затем `Error: Bad CP index: -2`.

Аллокации в `Scalarization` зависят от момента JIT-компиляции; стабильны только
прогретые раунды. Гистограммы heap (`SameCode`, `Arrays`) повторялись побайтно
на нескольких запусках, но зависят от билда JDK.

## Карта покрытия

- Identity и её цена: `==` на `Long`, массив дат как массив ссылок, почему JVM
  не может сама убрать identity, связь с escape analysis — шаг identity-cost,
  SameCode, схема массивов; ошибка `Long == Long` — в java-types.
- Объявление: value class, value record, неявный final, abstract value class,
  Number и Record, sealed, наследование, hasIdentity, ACC_IDENTITY, 30 классов
  JDK, String не value — шаг declare, Rules, JdkClasses, схема иерархии,
  карточка what-is.
- `==` для value-объектов: класс, биты, рекурсия; String-поле, NaN, -0.0,
  унаследованный equals и hashCode, стоимость и безопасность, if_acmpeq —
  шаг equality, Equality, пошаговая схема, карточка value-equality, Predict.
- Что ломается: synchronized (javac и runtime), wait/notify, WeakReference,
  WeakHashMap, IdentityHashMap, `new Integer`, кеш Integer, рефлексия,
  сериализация, finalize, старый JAR с `--enable-preview`, подготовка на JDK 21
  (DiagnoseSyncOnValueBasedClasses, предупреждения javac) — шаг breaks,
  Identity, Prepare, SyncDate, карточка migration. Механика WeakReference —
  в gc-references.
- Конструирование: larval-объект, ранняя и поздняя фаза, super() в конце,
  явный super(), инициализаторы, record с preview, JEP 539, ACC_STRICT_INIT,
  verifier, early_larval frame, trusted finals, static strict-поля — шаг
  construction, RecordThis, EarlyThis, Construction, Larval, пошаговая схема.
  Compact constructor обычного record — в records.
- Flattening и scalarization: 64 бита с флагом null, массивы и поля, NON_ATOMIC
  внутри value-объекта, буферизация, PrintFieldLayout, scalarization через
  границы методов, соглашение о вызовах, условия (тип, generics, прогрев,
  LoadableDescriptors) — шаг flattening, Arrays, Layout, Scalarization, схема
  раскладки, карточка heap-guarantee, OrderLayout. Заголовок и compressed oops —
  в object-layout.
- Статус и границы: JEP 390, 534, 401, 539, черновик null-restricted, JEP 402,
  специализация generics, как попробовать, когда value class не нужен — шаг
  status, уточнение в карточке flattening.

Основа подачи — JEP 401: от «identity не нужна неизменяемым данным» к
сравнению, конструированию и оптимизациям. Пример с датами взят из JEP,
сквозной домен (платежи, Money, Iban, блокировка по счёту) и все выводы
получены здесь. Valhalla на собеседованиях спрашивают редко; набор вопросов
собран из обычных вопросов про `Integer ==`, кеш обёрток, records и
escape analysis с поправкой на JDK 28.
