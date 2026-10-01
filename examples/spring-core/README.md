# Spring Core demos

Запускаемые примеры к страницам `topics/`. Требуются JDK 21 и Maven.
Зависимости зафиксированы: Spring Framework 7.0.9, Jakarta Annotations 3.0.0, AspectJ Weaver 1.9.24.

```bash
cd examples/spring-core
./run.sh ContextDemo
```

Скрипт собирает classpath через Maven и запускает Java source launcher. Сервер, Spring Boot и база данных не нужны. Каталог `target/` не коммитится. Консольный вывод соответствующего запуска приведён в уроке; временные пути и времена не используются как ожидаемые значения.

В каждом файле один самостоятельный эксперимент. Комментарии выделяют причину наблюдаемого поведения. Для сравнения с веткой 6.2 можно выполнить `mvn dependency:build-classpath -Dspring.version=6.2.19 -Dmdep.outputFile=target/classpath-6.txt` и `java --class-path "$(cat target/classpath-6.txt)" ContextDemo.java`.
