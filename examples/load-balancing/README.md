# Load balancing demo

Пример к теме System Design › Building Blocks › Load Balancing: nginx 1.31 перед тремя бэкендами на JDK HttpServer. Нужны JDK 21 и Docker.

```bash
./examples/load-balancing/run.sh
```

У каждого бэкенда 4 рабочих потока; 8093 отвечает за 200 мс, остальные за 20 мс; 8094 закрывает соединение, не ответив. Задержки меняются от запуска к запуску.
