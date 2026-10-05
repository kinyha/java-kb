# Least Privilege & Approval Gates — демо

Пример к теме AI Engineering › Least Privilege & Approval Gates.

```bash
cd examples/ai-permissions
./sandbox-demo.sh                                              # песочница macOS (sandbox-exec): запись и сеть
python3 ../ai-agents/trace.py recorded/with-settings.jsonl     # прогон с правилами из --settings
python3 ../ai-agents/trace.py recorded/untrusted.jsonl         # тот же проект без доверия: allow проигнорированы
cat recorded/audit-with-settings.jsonl                         # журнал из хука audit.py
./setup.sh                                                     # копия учебного проекта во временный каталог
```

`project-template/` — учебный проект: `.claude/settings.json` (allow/ask/deny и хуки), `.claude/hooks/guard.py`
(блокирует rm -rf, force push, миграции), `.claude/hooks/audit.py` (журнал попыток и выполнений), фиктивный `.env`.
Команда прогона — на странице темы, блок «Как попробовать самому». Записи — Claude Code 2.1.287, haiku 4.5;
пути заменены на `$DEMO`, имя пользователя — на `user`.

Проверить хук без агента: `echo '{"tool_input":{"command":"rm -rf x"}}' | python3 project-template/.claude/hooks/guard.py; echo $?` → 2.
