#!/usr/bin/env bash
# Что остаётся от generics в байткоде: компилируем Erased.java и показываем нужные строки javap.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" Erased.java
javap -c -p -v -cp "$OUT" Erased | grep -E "static long first|static <T|descriptor: \(L|Signature: #|checkcast|invokeinterface" | sed -E 's/ +$//'
rm -rf "$OUT"
