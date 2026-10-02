# PostgreSQL replication demo

Пример к теме Databases › Scaling › Replication: primary и потоковая реплика PostgreSQL 17 в Docker. Нужны JDK 21, Maven и Docker.

```bash
cd examples/pg-replication
./setup.sh   # primary на :55441, реплика на :55442, сеть репликации pg-repl
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" ReplicationDemo.java
```

Демо задаёт реплике задержку применения 500 мс, разрывает сеть репликации, убивает primary и повышает реплику. Перед повторным запуском снова выполните ./setup.sh.
