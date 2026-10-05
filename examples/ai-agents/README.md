# How Code Agents Work — демо

Пример к теме AI Engineering › How Code Agents Work.

```bash
cd examples/ai-agents
java AgentLoop.java                      # устройство цикла агента; без сети и ключей
python3 trace.py recorded/run1.jsonl     # разбор записанного прогона Claude Code
python3 trace.py recorded/run2.jsonl     # прогон, где гейт отклонил компиляцию
./run.sh                                 # свои прогоны: нужен залогиненный Claude Code, ~1 цент на haiku
```

`recorded/*.jsonl` — очищенные записи настоящих прогонов Claude Code 2.1.287 (haiku 4.5): `trace.py --sanitize`
оставляет только события цикла, без путей, id сессии и списка плагинов. Свои прогоны дадут другие ответы:
модель не детерминирована.
