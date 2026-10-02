# Security demos

Примеры к темам Spring › Security. Нужны JDK 21 и Maven; Spring Boot 4.1.1, Spring Security 7.1.

```bash
cd examples/security
./run.sh SecurityDemo          # настройки Boot по умолчанию: одна цепочка, форма + Basic
./run.sh SecurityDemo custom   # две цепочки: stateless API на /api/** и сайт с формой
./run.sh AuthnDemo             # пароли, ProviderManager, вход формой и смена id сессии
./run.sh AuthzDemo             # порядок правил URL, роли, @PreAuthorize и обход через this
./run.sh AuthzDemo fixed       # те же правила URL в правильном порядке
./run.sh JwtDemo               # проверка JWT: подпись, срок, aud, конвертер ролей
```

`JwtDemo` сам создаёт RSA-ключ и подписывает токены, Keycloak не нужен.

Демо поднимает веб-сервер на случайном порту и само шлёт запросы через `java.net.http.HttpClient`.
Пароль обоих пользователей — `secret`, хранится с `{noop}` только ради демо.

DEBUG-лог Spring Security для разбора пути запроса:

```bash
./run.sh SecurityDemo   # один раз, чтобы собрать target/
java -Dseclog=debug --class-path "target/SecurityDemo:$(cat target/classpath.txt)" kb.security.SecurityDemo
```
