#!/usr/bin/env bash
# Модули: public-класс неэкспортированного пакета недоступен при компиляции, а глубокая рефлексия — при выполнении.
set -u
cd "$(dirname "$0")/modules"
OUT=$(mktemp -d)
javac -d "$OUT/payments" $(find payments -name '*.java')
javac -p "$OUT" -d "$OUT/app" $(find app -name '*.java')
echo "== run app"
java -p "$OUT" -m app/com.app.Main
echo "== run app with --add-opens payments/com.pay.api=app"
java -p "$OUT" --add-opens payments/com.pay.api=app -m app/com.app.Main | tail -1
echo "== compile app code that imports com.pay.internal"
mkdir -p "$OUT/bad/com/app" && cp app/module-info.java "$OUT/bad/" && cp BadMain.java "$OUT/bad/com/app/"
javac -p "$OUT" -d "$OUT/badout" "$OUT/bad/module-info.java" "$OUT/bad/com/app/BadMain.java" 2>&1 | grep -E "error:" | sed -E 's#^.*/BadMain.java#BadMain.java#'
rm -rf "$OUT"
