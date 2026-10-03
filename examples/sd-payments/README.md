# Payment System demo

Пример к теме System Design › Case Studies › Payment System. Нужны JDK 21, Maven и Docker. PostgreSQL настоящий, PSP — модель в памяти.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/sd-payments
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" PaymentDemo.java
```
