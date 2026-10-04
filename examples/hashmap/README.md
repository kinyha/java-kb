# HashMap demos

Примеры к теме Java › HashMap. JDK 21, без зависимостей.

```bash
cd examples/hashmap
java --add-opens java.base/java.util=ALL-UNNAMED InternalsDemo.java   # индексы, рост, деление корзин, treeify, цена коллизий
java PitfallsDemo.java                                                 # equals без hashCode, порядок обхода, merge, гонки
```

Числа времени и потерь в многопоточном опыте меняются от запуска к запуску.
