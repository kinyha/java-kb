# Tracing demo

Пример к теме DevOps › Observability › Tracing: три сервиса с OpenTelemetry Java SDK 1.55 и Jaeger 2.10 в Docker. Нужны JDK 21, Maven и Docker.

```bash
cd examples/tracing
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
./run.sh
```

Идентификаторы трасс случайны, число выбранных трасс (около 10% из 200) и миллисекунды меняются от запуска к запуску.
