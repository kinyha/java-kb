# Redis in production demos

Примеры к теме Backend › Caching › Redis in Production. Нужны Docker, для LockDemo ещё JDK 21 и Maven.

```bash
cd examples/redis-ops
./persistence.sh   # перезапуск без персистентности и с AOF; потеря записи при failover
./cluster.sh       # три мастера: слоты, MOVED, CROSSSLOT, hash tags

docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" LockDemo.java
```

Скрипты сами создают и удаляют контейнеры. IP-адрес в ответе MOVED меняется от запуска к запуску.
