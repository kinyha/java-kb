# Design demos

Примеры к папке Code Design. Нужен только JDK 21, сборка не нужна:

```bash
cd examples/design
java SolidDemo.java
java CreationalDemo.java
java StructuralDemo.java
java BehavioralDemo.java
java HexagonalDemo.java
java DddDemo.java values
java DddDemo.java aggregate
java DddDemo.java race
java DddDemo.java events
```

DddDemo показывает равенство и копирование value object, инвариант возвратов,
повтор команды и проверку версии двух снимков. Режим events сравнивает локальное
событие с сохранением состояния и outbox. Store — учебная модель в памяти:
реальная база и брокер не нужны. Ошибка сохранения задаётся явно; после неё
следующая попытка начинает с новой копии. Каждая команда запускает новый опыт.
