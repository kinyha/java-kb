# Backtracking

JDK 21, без зависимостей. Из корня репозитория:

```bash
java examples/backtracking/StateDemo.java
java examples/backtracking/ChoicesDemo.java
java examples/backtracking/SumDemo.java
java examples/backtracking/DuplicatesDemo.java
java examples/backtracking/Subsets.java
java examples/backtracking/Permutations.java
java examples/backtracking/Combinations.java
java examples/backtracking/CombinationSum.java
```

Четыре Demo дают 11 опытов на странице. Четыре остальных файла — решения
задач с 28 проверками; ошибки вызывают AssertionError. Заготовки на странице
содержат полный main и методы проверки, тела решения нужно дописать.

В Subsets и Permutations значения различны; порядок подмножеств внутри
списка не важен, элементы подмножества идут в порядке исходных индексов.
Combinations выбирает k чисел из 1..n; k=0 даёт одно пустое сочетание,
k вне 0..n — отсутствие ответов, n неотрицательно.
CombinationSum принимает различные положительные кандидаты и target>=0.
Пустой набор кандидатов и target=0 дополнительно разрешены. Значение можно
использовать многократно. Исходные массивы сохраняются.

SumDemo показывает намеренно ошибочный break на несортированном массиве.
Нули и отрицательные кандидаты в нём отклоняются до поиска: при повторном
выборе они не обеспечили бы уменьшение remaining. DuplicatesDemo отдельно
сравнивает позиции входа и уникальные ответы по значениям.

Результаты сняты на JBR 21.0.11. Счётчик visited — число входов в рекурсивный
combine, включая корень и листья. Он измеряет работу обхода, не время JVM.
List.copyOf снимает неглубокий неизменяемый снимок; Integer неизменяем.
