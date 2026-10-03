# Alerting demo

Пример к теме DevOps › Alerting & Incidents: правила алертов по SLO проверяются promtool test rules из образа prom/prometheus:v3.6.0. Нужен Docker.

```bash
./examples/incidents/incidents.sh
```

gen.py создаёт синтетические ряды для четырёх сценариев и тесты: сначала с пустыми ожиданиями, чтобы увидеть срабатывания, затем с точными — они должны пройти. Вывод детерминирован.
