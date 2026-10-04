#!/usr/bin/env bash
# Эволюция интерфейса: Sbp собран против v1 и не пересобирается; меняется только Gateway.
set -u
cd "$(dirname "$0")/evolve"
OUT=$(mktemp -d); SRC=$(mktemp -d)
cp Gateway.v1.java "$SRC/Gateway.java"
javac -d "$OUT" "$SRC/Gateway.java" Sbp.java
for v in 2 3; do
  cp Gateway.v$v.java "$SRC/Gateway.java"
  javac -cp "$OUT" -d "$OUT" "$SRC/Gateway.java" Main.java       # Sbp.class остаётся от v1
  echo "== Gateway v$v, old Sbp.class"
  java -cp "$OUT" Main
done
echo "== recompile Sbp against v3"
javac -cp "$OUT" -d "$OUT" Sbp.java 2>&1 | grep error: | sed -E 's#^.*/?Sbp.java#Sbp.java#'
rm -rf "$OUT" "$SRC"
