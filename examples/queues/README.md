# Queue & Deque demos

Примеры к теме Java › Queue & Deque. JDK 21, без зависимостей.

```bash
cd examples/queues
java --add-opens java.base/java.util=ALL-UNNAMED DequeDemo.java   # кольцевой массив ArrayDeque, API очереди, ArrayDeque против Stack
java -Xmx2g PriorityDemo.java                                     # куча PriorityQueue, изменённый приоритет, замер push/pop
```
