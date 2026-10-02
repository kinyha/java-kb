# Resilience demo

Пример к теме System Design › Reliability › Resilience Patterns. Нужны JDK 21 и Maven; Resilience4j 2.4.

```bash
cd examples/resilience
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" ResilienceDemo.java
```

Миллисекунды меняются от запуска к запуску; распределение jitter фиксировано seed.
