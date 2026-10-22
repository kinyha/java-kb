#!/usr/bin/env bash
# Все опыты темы Kotlin for Java Devs. Запуск из корня Java KB: examples/kotlin-for-java/run.sh
# Нужны JDK 21 и kotlinc 2.x (проверено на kotlinc 2.4.10, JBR 21.0.11). JSpecify берётся из кеша Gradle или JSPECIFY_JAR.
set -u
cd "$(dirname "$0")"

KLIB=${KLIB:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib}
STD="$KLIB/kotlin-stdlib.jar"
JSPECIFY_JAR=${JSPECIFY_JAR:-$(find ~/.gradle/caches/modules-2/files-2.1/org.jspecify ~/.m2/repository/org/jspecify -name 'jspecify-1.0.0.jar' 2>/dev/null | head -1)}
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
step() { printf '\n===== %s\n' "$*"; }

kotlinc -version 2>&1
java -version 2>&1 | head -1

# kotlinc читает .java только как исходники для типов; Java-классы собирает javac.
build() { # $1 — имя каталога, $2 — jvm-target
  local out="$W/$1"; mkdir -p "$out"
  kotlinc "$1"/*.kt $(ls "$1"/*.java 2>/dev/null) -jvm-target "${2:-21}" -d "$out" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
  ls "$1"/*.java >/dev/null 2>&1 && javac -cp "$out:$STD" -d "$out" "$1"/*.java 2>&1 || true
}

step "1. Где Kotlin всё равно бросает NPE"
build boundary
java -cp "$W/boundary:$STD" pay.ReceiptsKt
step "1a. Проверки, которые вставил компилятор"
javap -c -p -cp "$W/boundary" pay.ReceiptsKt | grep -E 'ownerDeclared|ownerInferred|checkNotNull|findOwner'
javap -c -p -cp "$W/boundary" pay.Checkout | grep -E 'gateway|ifnull|throwUninitialized'

step "1b. NPE в чистом Kotlin: open-метод из конструктора"
build initorder
java -cp "$W/initorder:$STD" pay.InitOrderKt

step "2. Kotlin-классы глазами Java"
build bytecode
java -cp "$W/bytecode:$STD" pay.JavaView
javap -p -cp "$W/bytecode" pay.Payment 'pay.Payment$Companion' pay.FeePolicy pay.PaymentKt

step "3. JSpecify: аннотированный Java-код (ожидаются две ошибки компиляции)"
kotlinc jspecify/*.kt jspecify/*.java -cp "$JSPECIFY_JAR" -d "$W/jspecify" 2>&1 | grep error

step "4. data class и @JvmRecord"
mkdir -p "$W/data"
kotlinc data/DataClass.kt data/Sealed.kt -jvm-target 21 -d "$W/data" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
java -cp "$W/data:$STD" pay.DataClassKt
javap -p -cp "$W/data" pay.Refund pay.Money
step "4a. sealed и when"
java -cp "$W/data:$STD" pay.SealedKt
javap -v -cp "$W/data" pay.PayResult | grep -A3 '^PermittedSubclasses'
javap -c -cp "$W/data" pay.SealedKt | sed -n '/int httpStatus/,/ireturn/p' | grep -E 'instanceof|areEqual|NoWhenBranch'
step "4b. Java дописывает вариант в sealed-иерархию: jvm-target 1.8 и 21"
for t in 1.8 21; do
  d="$W/sealed-$t"; mkdir -p "$d"; echo "-- jvm-target $t"
  kotlinc data/Sealed.kt -jvm-target "$t" -d "$d" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
  if javac -cp "$d:$STD" -d "$d" data/JavaRefund.java 2>&1 | grep error; then continue; fi
  java -cp "$d:$STD" pay.JavaRefund 2>&1 | head -2
done

step "5. extension functions и inline/reified"
build functions
java -cp "$W/functions:$STD" pay.ExtensionsKt
java -cp "$W/functions:$STD" pay.InlineKt
javap -p -cp "$W/functions" pay.ExtensionsKt
javap -v -cp "$W/functions" pay.InlineKt | grep -A2 ' only(java' | grep flags
javap -c -cp "$W/functions" pay.InlineKt | sed -n '/void main()/,/return$/p' | grep -E 'instanceof|onlyOf|ldc .*class'

step "6. read-only коллекции и final по умолчанию"
mkdir -p "$W/limits"
kotlinc limits/ReadOnly.kt limits/JavaLedger.java -d "$W/limits" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
javac -cp "$W/limits:$STD" -d "$W/limits" limits/JavaLedger.java
java -cp "$W/limits:$STD" pay.ReadOnlyKt
kotlinc limits/Services.kt -d "$W/limits" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
javac -cp "$W/limits:$STD" -d "$W/limits" limits/Subclasses.java 2>&1 | grep error
