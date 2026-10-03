# Consensus demo

Пример к теме System Design › Distributed Systems › Consensus & Time: кластер etcd 3.6.5 из трёх узлов в Docker и клиенты на alpine. Нужен Docker.

```bash
./examples/consensus/consensus.sh
```

Скрипт сам создаёт и удаляет контейнеры, сеть и том. Время недоступности после аварийного падения лидера меняется от запуска к запуску (1,1–3,2 с в трёх прогонах).
