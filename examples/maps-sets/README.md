# Other Maps & Sets demos

Примеры к теме Java › Other Maps & Sets. JDK 21, без зависимостей.

```bash
cd examples/maps-sets
java --add-opens java.base/java.util=ALL-UNNAMED MapsDemo.java   # LRU, TreeMap-навигация, HashSet внутри, IdentityHashMap, WeakHashMap
java -Xmx2g LookupBench.java                                    # get в HashMap, LinkedHashMap и TreeMap на миллионе ключей
```
