#!/usr/bin/env bash
# Лямбда в байткоде: invokedynamic, приватный метод lambda$..., bootstrap LambdaMetafactory.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" Fees.java
(cd "$OUT" && ls *.class)
javap -p -c -v -cp "$OUT" Fees | grep -E "invokedynamic|private static long lambda|REF_invokeStatic java/lang/invoke/LambdaMetafactory.metafactory|^ +[0-9]: #[0-9]+ REF_invokeStatic Fees.lambda|REF_invokeStatic java/lang/Long.parseLong" | grep -v "^ *#[0-9]* = " | sed -E 's/ +$//; s/metafactory:\(.*\)Ljava\/lang\/invoke\/CallSite;/metafactory:(Lookup, String, MethodType, ...)CallSite/' | sort -u
rm -rf "$OUT"
