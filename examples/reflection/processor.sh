#!/usr/bin/env bash
# Обработка аннотаций при компиляции: собираем процессор, затем компилируем Refunds.java с ним.
set -u
cd "$(dirname "$0")/processor"
OUT=$(mktemp -d)
javac -d "$OUT/proc" Audited.java AuditProcessor.java
javac -cp "$OUT/proc" -processor audit.AuditProcessor -d "$OUT/app" Refunds.java 2>&1 | grep -E "Note:|error:|errors?$" | sed -E 's#^.*/Refunds.java#Refunds.java#'
echo "class file produced: $([ -f "$OUT/app/Refunds.class" ] && echo yes || echo no)"
rm -rf "$OUT"
