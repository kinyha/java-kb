#!/usr/bin/env bash
# Куда компилятор кладёт инициализаторы полей: в каждый конструктор, который вызывает super(), сразу после него.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" Payment.java
javap -p -c -cp "$OUT" Payment | grep -E "public Payment\(|invokespecial|putfield|ldc +#.*String (RUB|init)" | sed -E 's/ +$//'
rm -rf "$OUT"
