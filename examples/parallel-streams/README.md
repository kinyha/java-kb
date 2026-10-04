# Parallel Streams demos

Пример к теме Java › Parallel Streams. JDK 21, без зависимостей.

```bash
cd examples/parallel-streams
java -Xmx2g ParallelDemo.java   # общий пул, ускорение и замедление, порядок, гонки, блокирующие задачи
```

Время и ускорение зависят от числа ядер; на странице прогон на 18 ядрах. Исход опыта с общим ArrayList меняется от запуска к запуску.
