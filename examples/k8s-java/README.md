# Java in Kubernetes demo

Пример к теме DevOps › Java in Kubernetes: память JVM и лимиты, пробы, остановка при выкатке на k3s 1.34 в Docker. Нужен Docker.

```bash
./examples/k8s-java/k8s-java.sh
docker rm -f kb-k3s     # удалить кластер
```

Прогон длится несколько минут. Число перезапусков и ошибок при выкатке меняется от запуска к запуску.
