# Sorting in Java

Два запуска без сборки на JDK 21. Вывод страницы получен на JBR 21.0.11+10.

```sh
java examples/java-sorting/SortingDemo.java
java -Djava.util.concurrent.ForkJoinPool.common.parallelism=2 examples/java-sorting/ParallelSortDemo.java
java -Djava.util.concurrent.ForkJoinPool.common.parallelism=1 examples/java-sorting/ParallelSortDemo.java
```

SortingDemo показывает стабильность, изменение массива через Arrays.asList,
неизменяемый список и копию, границы диапазона, переполнение компаратора,
направление сортировки с null и число сравнений упорядоченных объектов.

ParallelSortDemo записывает имена потоков в потокобезопасное множество.
Имена и состав множества зависят от планировщика. На 8192 объектах JDK 21
использует последовательную ветку; на 32768 с parallelism=2 — параллельную.

По API сортировка объектов parallelSort должна быть стабильной. В данной
сборке пример даёт stable=false на большом массиве: это воспроизведение
JDK-8076446, а не разрешённая особенность API. С дополнительным ключом id
порядок задан явно и проверяется отдельно. С parallelism=1 оба размера
сортируются последовательно. Пример проверяет порядок сумм и порядок id
у явного второго ключа; нарушение стабильности печатает для диагностики.

Источник ошибки: https://bugs.openjdk.org/browse/JDK-8076446
