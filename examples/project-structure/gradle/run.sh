#!/usr/bin/env bash
# Модули Gradle: app обходит payments и импортирует payments-persistence. Нужны JDK 21 и Gradle 8+.
set -uo pipefail
cd "$(dirname "$0")"
echo "== implementation"
gradle -q --offline clean :app:compileJava 2>&1 | grep -m1 'error:' | sed 's|.*/src/main/java/||'
echo "== api"
gradle -q --offline clean :app:compileJava -Pleak && echo "app compiles"
