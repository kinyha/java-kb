# Cloud patterns: sidecar / ambassador demo

Пример к теме System Design › Architecture › Cloud Patterns: приложение и nginx 1.31 как сайдкар в одном сетевом пространстве Docker, как контейнеры одного пода. Нужен Docker.

```bash
./examples/cloud-patterns/sidecar.sh
```

Сервис курсов отвечает 503 примерно в 30% случаев; точные числа меняются от запуска к запуску.
