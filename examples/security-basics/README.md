# Security basics demos

Примеры к папке Backend › Security. Нужен JDK 21; для WebVulnDemo ещё Maven (H2 в памяти). Ключи и подписи случайные, при каждом запуске другие.

```bash
cd examples/security-basics
java AuthDemo.java
java JwtAttacksDemo.java
java OAuthFlowDemo.java
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" WebVulnDemo.java
```
