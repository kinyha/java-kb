# Metrics demo

Пример к теме DevOps › Observability › Metrics: два экземпляра с Micrometer 1.15 и Prometheus 3.6 в Docker. Нужны JDK 21, Maven и Docker.

```bash
cd examples/metrics
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
./run.sh
```

Прогон длится около 45 секунд. Скорости и перцентили слегка меняются от запуска к запуску.
