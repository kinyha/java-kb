#!/usr/bin/env bash
# Что лежит в класс-файлах: скрытое поле this$0, NestMembers/NestHost, лямбда без отдельного класса.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" Outer.java
echo "== class files"; (cd "$OUT" && ls *.class | sort)
echo "== Outer\$Inner"; javap -p -v -cp "$OUT" 'Outer$Inner' | grep -E "final Outer this\\\$0|Outer\\\$Inner\(Outer\)|NestHost|putfield.*total|getfield.*total" | grep -vE "^ +#[0-9]+ = |^ +#[0-9]+ REF" | sed -E "s/ +$//"
echo "== Outer\$Idle"; javap -p -cp "$OUT" 'Outer$Idle' | grep -E "this|Idle\(" | sed -E 's/ +$//'
echo "== Outer\$Nested"; javap -p -cp "$OUT" 'Outer$Nested' | grep -E "class|Nested\(" | grep -vE "^ +#[0-9]+ = |^ +#[0-9]+ REF" | sed -E "s/ +$//"
echo "== Outer"; javap -p -v -cp "$OUT" Outer | grep -E "NestMembers|^ +Outer\\\$|lambda\\\$lambda|invokedynamic" | grep -vE "^ +#[0-9]+ = |^ +#[0-9]+ REF" | sed -E "s/ +$//"
rm -rf "$OUT"
