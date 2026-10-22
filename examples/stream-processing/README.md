# Stream processing demos

Примеры к теме Backend › Messaging › Stream Processing. Нужны JDK 21 и Maven; Kafka Streams 4.2.1.

```bash
cd examples/stream-processing
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" WindowsDemo.java                   # tumbling, hopping, session на одних платежах
java -cp "$(cat target/classpath.txt)" LateDemo.java                      # grace, опоздавшие события, suppress
java -cp "$(cat target/classpath.txt)" JoinDemo.java                      # KStream-KTable join, обычная и версионированная таблица
java -cp "$(cat target/classpath.txt)" CopartitionDemo.java 3             # join: у payments и cards по 3 партиции, ~20 с
java -cp "$(cat target/classpath.txt)" CopartitionDemo.java 2             # у cards 2 партиции: часть платежей без статуса
java -cp "$(cat target/classpath.txt)" TopologyDemo.java                  # топология, задачи, changelog и repartition, ~10 с
java -cp "$(cat target/classpath.txt)" EosDemo.java at_least_once         # падение до коммита: счётчик задваивается, ~25 с
java -cp "$(cat target/classpath.txt)" EosDemo.java exactly_once_v2       # то же с EOS: счётчик верный, ~30 с
```

`WindowsDemo`, `LateDemo` и `JoinDemo` работают на `TopologyTestDriver` без брокера: время событий задаёт сам тест.
`TopologyDemo`, `CopartitionDemo` и `EosDemo` поднимают брокер KRaft внутри JVM через `EmbeddedKafkaKraftBroker` из spring-kafka-test,
Docker не нужен. `EosDemo` запускает экземпляры приложения A и B отдельными JVM и убивает A через `Runtime.halt`.
Строки `Bootstrap metadata` и `Formatting metadata directory` печатает встроенный брокер.
