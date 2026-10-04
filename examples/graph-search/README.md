# Graph Search

JDK 21. Без сборки и зависимостей. Из корня репозитория:

```bash
java examples/graph-search/IslandsDemo.java
java examples/graph-search/GridPathsDemo.java
java examples/graph-search/DependenciesDemo.java
java examples/graph-search/Islands.java
java examples/graph-search/ShortestBinaryPath.java
java examples/graph-search/DependencyOrder.java
java examples/graph-search/DirectedCycle.java
```

Три Demo содержат девять опытов со страницы. Четыре остальных файла —
готовые решения задач с 33 проверками; ошибка вызывает AssertionError.
Для тренировки замени тело solve заготовкой со страницы.

IslandsDemo: 1 — суша, 0 — вода; FOUR и EIGHT меняют определение соседства.
GridPathsDemo: 0 — проход, 1 — препятствие; dist считает рёбра, path — клетки.
ShortestBinaryPath: квадратная матрица, восемь направлений, длина в клетках.
Все матрицы прямоугольные, строки ненулевые, значения только 0/1.
В задаче ShortestBinaryPath пустая матрица дополнительно разрешена.

Рёбра DependenciesDemo, DependencyOrder и DirectedCycle записаны как
[предпосылка, зависимая задача], вершины 0..n-1. Повторные рёбра разрешены:
они учитываются и при подсчёте входящих рёбер, и при их снятии.
Это отличается от порядка [курс, предпосылка] в Course Schedule на LeetCode.
Тест порядка проверяет все вершины и все зависимости, поэтому принимает
любой правильный топологический порядок. Входные массивы сохраняются.

Реальные выводы: JBR 21.0.11. Диагностические списки упорядочены правилами
обхода, заданными в коде; другой порядок соседей может изменить путь или
топологический порядок при сохранении расстояния и всех зависимостей.
