#!/usr/bin/env bash
# Во что компилируется enum: класс, поля констант, $VALUES и values() через clone().
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" Channel.java
javap -p -c -cp "$OUT" Channel | grep -E "^public final class|public static final Channel|private static final Channel\[\]|public static Channel\[\] values|getstatic.*VALUES|invokevirtual.*clone|static \{\};|new +#|ldc +#.*String CARD|invokespecial.*\"<init>\"" | head -14 | sed -E 's/ +$//'
rm -rf "$OUT"
