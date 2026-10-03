# Deployment strategies demo

Пример к теме DevOps › Deployment Strategies. Нужен Docker с контейнерами kb-k3s (поднимается скриптом) и kb-pg.

```bash
./examples/deploy-strategies/deploy.sh    # rolling update, откат, blue-green, canary на k3s 1.34
./examples/deploy-strategies/migrate.sh   # переименование столбца против expand/contract на PostgreSQL 17
```

Хронология выкатки и доли canary меняются от запуска к запуску. migrate.sh использует отдельную таблицу invoice.
