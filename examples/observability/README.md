# Observability demo

Пример к теме DevOps › Observability › Overview: три сервиса на JDK HttpServer с SLF4J, Logback и JSON-логами в общий файл logs/app.json. Нужны JDK 21 и Maven.

```bash
cd examples/observability
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
mkdir -p logs
java -Dlogback.configurationFile=logback.xml -cp "$(cat target/classpath.txt)" ObservabilityDemo.java
```

Идентификаторы запросов случайны, миллисекунды меняются от запуска к запуску.
