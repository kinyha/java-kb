#!/usr/bin/env bash
# Весь конвейер темы AI Task Workflow на своём Claude Code: тест первым -> ревью теста человеком ->
# реализация -> гейт -> ревьюер с чистым контекстом. Модель haiku, весь прогон — порядка 10 центов.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
TRACE="python3 $HERE/../ai-agents/trace.py"
D=$("$HERE/setup.sh"); cd "$D"; echo "demo repo: $D"
TOOLS=(--tools "Read,Write,Edit,Bash,Glob" --allowedTools "Read,Write,Edit,Glob,Bash(./test.sh)")
COMMON=(-p --safe-mode --model haiku --output-format stream-json --verbose --max-turns 12 --no-session-persistence)

echo "== 1. test first"
claude "${COMMON[@]}" "${TOOLS[@]}" "Прочитай SPEC.md и CLAUDE.md. Напиши только тест RefundsTest.java (обычный main с assert и проверкой исключений, без JUnit) по всем критериям приёмки. Refunds.java не трогай. Запусти ./test.sh и покажи, что тест красный и почему." > step1.jsonl
$TRACE step1.jsonl
echo; echo "== 2. human: read RefundsTest.java, fix weak or missing cases, then press Enter"
read -r _ || true
printf 'out/\n' > .gitignore
git add -A && git -c user.name=me -c user.email=me@example.com commit -qm "red"; RED=$(git rev-parse --short HEAD)

echo "== 3. implementation"
claude "${COMMON[@]}" "${TOOLS[@]}" "Реализуй Refunds.refund по SPEC.md так, чтобы ./test.sh стал зелёным. RefundsTest.java не меняй. Покажи вывод ./test.sh." > step2.jsonl
$TRACE step2.jsonl

echo "== 4. gate"
"$HERE/gate.sh" "$D" "$RED"

echo "== 5. reviewer with clean context"
claude "${COMMON[@]}" --tools "Read" --allowedTools "Read" "Ты ревьюер. Прочитай SPEC.md и дифф ниже. Найди расхождения со спекой и непокрытые входы, которые могут дать неверную сумму возврата. Не больше 4 пунктов, по одной строке, каждый с конкретным примером входа и результата.

$(git diff "$RED" -- Refunds.java)" > step3.jsonl
python3 -c "import json,sys; print([json.loads(l) for l in open('step3.jsonl')][-1]['result'])"
