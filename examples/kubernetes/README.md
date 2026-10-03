# Kubernetes demo

Пример к теме DevOps › Kubernetes Basics: k3s 1.34 в Docker-контейнере kb-k3s, Deployment, Service, ConfigMap и метки. Нужен Docker; kubectl используется изнутри контейнера k3s.

```bash
./examples/kubernetes/k8s.sh
docker rm -f kb-k3s     # удалить кластер
```

Имена подов случайны; задержка обновления файла из ConfigMap — от секунд до минуты.
