# Digital Wallet demo

Пример к теме System Design › Case Studies › Digital Wallet. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
cd examples/sd-wallet
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" WalletDemo.java
```

Прогон занимает несколько минут: каждая взаимная блокировка ждёт deadlock_timeout = 1 с. Итог наивного варианта и число блокировок меняются от запуска к запуску.
