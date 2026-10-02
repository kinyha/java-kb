# Caching demos

Примеры к папке Backend › Caching. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
cd examples/caching
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" CachingDemo.java
```

Redis настоящий; база в CachingDemo — заглушка с задержкой 20 мс и счётчиком запросов. Миллисекунды меняются от запуска к запуску.
