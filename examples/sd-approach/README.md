# Interview Framework: write capacity check

Пример к теме System Design › Interview Framework: pgbench вставляет строки операций в PostgreSQL 17 по одной и пачками по 100.

```bash
docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
./examples/sd-approach/run.sh
```

Числа на странице получены на ноутбуке с 18 ядрами в Docker; в продакшене с медленным диском и синхронной репликой они ниже.
