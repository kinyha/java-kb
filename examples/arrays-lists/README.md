# Arrays & Lists

Из корня репозитория на JDK 21, без зависимостей:

```sh
java --add-opens java.base/java.util=ALL-UNNAMED examples/arrays-lists/SequenceDemo.java
java examples/arrays-lists/LinkedTraversalDemo.java
java examples/arrays-lists/RingQueueDemo.java
```

Вывод получен на JBR 21.0.11+10. Флаг первого запуска позволяет показать
внутренний массив ArrayList и его длину. Это диагностика реализации,
а не рекомендованный способ пользоваться списком в приложении.

LinkedTraversalDemo — учебная двусвязная цепочка, повторяющая выбор ближайшего
конца при поиске по индексу в LinkedList JDK 21. Она считает чтения связей;
сама JDK не предоставляет такой счётчик. Счётчики не измеряют наносекунды.

RingQueueDemo — ограниченная очередь с явным size, а не реализация
ArrayDeque JDK. При заполнении возвращает false. Стандартный ArrayDeque
расширяется; оба варианта используют циклическое движение индексов.
