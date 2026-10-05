# AI Task Workflow — демо

Пример к теме AI Engineering › AI Task Workflow: тест первым, ревью теста человеком, реализация,
детерминированный гейт и ревьюер с чистым контекстом на настоящих прогонах Claude Code.

```bash
cd examples/ai-workflow
python3 ../ai-agents/trace.py recorded/step1.jsonl   # агент пишет тест, тест красный
python3 ../ai-agents/trace.py recorded/step2.jsonl   # агент реализует, тест не меняет
python3 answer.py recorded/step3.jsonl               # ответ ревьюера с чистым контекстом
./run.sh                                             # весь конвейер на своём Claude Code (~10 центов на haiku)
./gate.sh <repo> <red-commit>                        # гейт отдельно
```

`result/` — файлы учебного репозитория после прогона: спека, правила, тест (агент + граничный случай,
добавленный человеком), реализация агента и её дифф. `recorded/` — очищенные записи прогонов
Claude Code 2.1.287 (haiku 4.5). Свои прогоны дадут другие тесты и замечания.
