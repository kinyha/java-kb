# List demos

Примеры к теме Java › List. JDK 21, без зависимостей.

```bash
cd examples/lists
java --add-opens java.base/java.util=ALL-UNNAMED GrowthDemo.java   # ёмкость elementData при росте и удалении
java RemoveDemo.java                                                # remove(int)/remove(Object), пропуски, CME, removeIf
java -Xmx1g CompareDemo.java                                        # ArrayList против LinkedList: время и память
```

Замеры времени зависят от машины; на странице — один реальный прогон, важны отношения.
