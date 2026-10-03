# Booking System demo

Пример к теме System Design › Case Studies › Booking System. Нужны JDK 21, Maven и Docker.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
docker exec kb-pg psql -U postgres -c "create extension if not exists btree_gist"
cd examples/sd-booking
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "$(cat target/classpath.txt)" BookingDemo.java
```

Число лишних подтверждений у наивных вариантов меняется от запуска к запуску; у условного UPDATE и ограничения исключения всегда одно подтверждение.
