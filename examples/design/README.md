# Design demos

Основные примеры к папке Code Design. Нужен только JDK 21, сборка не нужна:

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

Для Components есть отдельное демо с ArchUnit 1.5.1. Оно читает байткод настоящих
пакетов payments, bank и notifications; нужны JDK 21 и Maven. Из корня репозитория:

```bash
bash examples/design/modules/run.sh
```

run.sh компилирует три варианта в разные каталоги. broken содержит цикл и обращение
к payments.internal. port уже разрывает цикл через BankGateway, но оставляет доступ
уведомлений к внутреннему классу. fixed проходит обе проверки. Затем ModuleDemo
считает Ca/Ce/I/A/D для исправленного графа. Ca и Ce — связи между тремя собственными
компонентами; A учитывает публичные типы, как принято в ArchUnit. Maven загружает
зависимости, javac компилирует небольшие исходники, сервер и Spring не запускаются.

FAIL в первых двух вариантах ожидается: демо проверяет, что каждое нарушение действительно
найдено. Если результат отличается от ожидаемого, процесс завершится с ошибкой.
