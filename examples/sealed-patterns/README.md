# Sealed & Pattern Matching

JDK 21 без preview и сторонних зависимостей. Из корня репозитория:

```bash
java examples/sealed-patterns/PatternDemo.java
java examples/sealed-patterns/HierarchyDemo.java
java examples/sealed-patterns/EdgeDemo.java
java examples/sealed-patterns/GenericDemo.java
python3 examples/sealed-patterns/verify.py
```

`verify.py` по умолчанию использует `~/.sdkman/candidates/java/current`.
Для другого JDK передай `JAVA_KB_JDK=/path/to/jdk`. Скрипт сравнивает реальный
вывод, компилирует отрицательные примеры, проверяет диагностику, а затем
моделирует отдельное обновление API и повторную компиляцию клиента. Все
class-файлы попадают во временную папку и удаляются после проверки.

Отрицательные примеры намеренно не компилируются. Отдельный запуск:

```bash
mkdir -p /tmp/java-kb-patterns-negative
javac -d /tmp/java-kb-patterns-negative examples/sealed-patterns/negative/MissingCase.java
javac -d /tmp/java-kb-patterns-negative examples/sealed-patterns/negative/Dominated.java
javac -d /tmp/java-kb-patterns-negative examples/sealed-patterns/negative/OrScope.java
javac -d /tmp/java-kb-patterns-negative examples/sealed-patterns/negative/Unchecked.java
```

Отдельная компиляция (папку используй только для этого опыта):

```bash
mkdir -p /tmp/java-kb-patterns-evolution
javac -d /tmp/java-kb-patterns-evolution examples/sealed-patterns/evolution/v1/Outcome.java examples/sealed-patterns/evolution/v1/Client.java
javac -cp /tmp/java-kb-patterns-evolution -d /tmp/java-kb-patterns-evolution examples/sealed-patterns/evolution/v2/Outcome.java examples/sealed-patterns/evolution/v2/Run.java
java -cp /tmp/java-kb-patterns-evolution Run
javap -c -classpath /tmp/java-kb-patterns-evolution Client
# Последняя команда ожидаемо падает: v2 уже требует ветку Retry
javac -cp /tmp/java-kb-patterns-evolution -d /tmp/java-kb-patterns-evolution examples/sealed-patterns/evolution/v1/Client.java
```

Две Outcome.java принадлежат разным версиям одного API. Их нельзя компилировать
вместе. В HTML показаны отдельные фрагменты двух версий, а не единый Java-файл.

## Карта покрытия и подача

Центральный вопрос: как не потерять новый вариант результата и при этом не
перепутать полноту списка типов с допустимостью данных? Сквозной пример —
платёж: успех с суммой, отказ с причиной, временный исход с задержкой.

- Зачем sealed, отличие final/non-sealed, permits, прямые потомки, interface,
  module/package, implicit permits, reflection и открытая ветвь — hierarchy,
  HierarchyDemo, карточка sealed.
- Instanceof + cast → type pattern; та же ссылка, flow scoping, &&, ||,
  отрицательная проверка с return, null, mutability — flow, PatternDemo,
  OrScope, карточка instanceof.
- Record patterns: component accessors, порядок canonical, вложенность,
  var и inference, checked cast, generic applicability, accessor failure —
  deconstruction/nulls, GenericDemo/EdgeDemo/Unchecked, record-null.
- Switch: первое совпадение, guards, dominance, type coverage, default,
  enhanced statement vs expression — incident/dispatch, PatternDemo,
  MissingCase/Dominated, карточка switch.
- Null selector vs null component vs nested record; NPE vs MatchException —
  nulls, EdgeDemo, карточка record-null.
- Добавление permitted subtype, binary vs source/behavioral compatibility,
  полная пересборка, выбор между внешними операциями и полиморфизмом — evolution,
  v1/v2 и verify.py, senior в record-null.
- Семантика records и глубокая immutable-модель — соседняя records;
  подробный синтаксис switch expressions/yield — What's New (до решения
  пользователя о структуре Modern Java); business retry/idempotency —
  resilience и idempotency, без сетевого демо в этой теме.

Основа объяснения — курс Dev.java Using Pattern Matching: различать target,
pattern и результат matching; сначала одна проверка типа, затем несколько
веток и разбор компонентов. Не переносим его обобщённые утверждения о
производительности switch: сложность и скорость не обещаны языком. Устаревшие
preview-примеры курса проверены по JLS 21. Собственные ошибки и выводы сначала
проверены на JBR 21.0.11, затем встроены в урок.

Отбор интервью-вопросов сверен с Hruday-Boppa/Core-Java-Interview-Questions-List
§20.6–20.7. Его список задаёт ширину покрытия, технические ответы основаны на
JLS, Oracle Language Updates и MatchException API.
