# MongoDB demo

Пример к теме Databases › NoSQL › MongoDB. Нужны JDK 21, Maven и Docker.

```bash
cd examples/mongodb
./setup.sh   # реплика-сет из трёх узлов mongo:8.2, primary на localhost:27117
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" MongoDemo.java
```

Образ mongo:8 (8.0) не запускается на ядре Linux 6.19+ (SERVER-121912), поэтому версия закреплена на 8.2.
Демо останавливает и запускает secondary; перед повтором выполните ./setup.sh.
