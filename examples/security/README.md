# Security demos

Примеры к темам Spring › Security. Нужны JDK 21 и Maven; Spring Boot 4.1.1, Spring Security 7.1.

```bash
cd examples/security
./run.sh SecurityDemo          # настройки Boot по умолчанию: одна цепочка, форма + Basic
./run.sh SecurityDemo custom   # две цепочки: stateless API на /api/** и сайт с формой
```

Демо поднимает веб-сервер на случайном порту и само шлёт запросы через `java.net.http.HttpClient`.
Пароль обоих пользователей — `secret`, хранится с `{noop}` только ради демо.

DEBUG-лог Spring Security для разбора пути запроса:

```bash
./run.sh SecurityDemo   # один раз, чтобы собрать target/
java -Dseclog=debug --class-path "target/SecurityDemo:$(cat target/classpath.txt)" kb.security.SecurityDemo
```
