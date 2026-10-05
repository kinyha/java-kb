#!/usr/bin/env bash
# Два коротких прогона Claude Code в неинтерактивном режиме (-p) с потоком событий.
# Нужен установленный и залогиненный Claude Code. Модель haiku — прогон стоит порядка цента.
# Записи событий кладутся в ./runs, разбор — python3 trace.py runs/run1.jsonl
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
D=$("$HERE/setup.sh")
mkdir -p "$HERE/runs"
COMMON=(-p --safe-mode --model haiku --output-format stream-json --verbose --max-turns 6
        --allowedTools "Read,Grep,Glob" --no-session-persistence)
cd "$D"
claude "${COMMON[@]}" "Как округляется комиссия в этом проекте и сколько вернут при оплате 999 копеек? Ответь в две строки." > "$HERE/runs/run1.jsonl"
claude "${COMMON[@]}" "Скомпилируй проект через javac в /tmp/out и скажи, есть ли ошибки. Ответь одной строкой." > "$HERE/runs/run2.jsonl"
echo "demo repo: $D"
python3 "$HERE/trace.py" "$HERE/runs/run1.jsonl"
python3 "$HERE/trace.py" "$HERE/runs/run2.jsonl"
