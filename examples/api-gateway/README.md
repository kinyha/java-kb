# API gateway demo

Пример к теме System Design › Building Blocks › API Gateway & Service Discovery: шлюз на nginx 1.31 перед сервисами в Docker-сети. Нужен Docker.

```bash
./examples/api-gateway/gateway.sh
```

X-Request-Id случаен и заменён в выводе заглушкой; какой экземпляр ответит и распределение 30 запросов меняются от запуска к запуску.
