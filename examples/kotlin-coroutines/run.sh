#!/usr/bin/env bash
# Все опыты темы Coroutines. Запуск из корня Java KB: examples/kotlin-coroutines/run.sh
# Нужны JDK 21, kotlinc 2.x и kotlinx-coroutines-core-jvm (ищется в кешах Gradle и Maven или задаётся COROUTINES_JAR).
# Проверено: kotlinc 2.4.10, kotlinx-coroutines 1.11.0, JBR 21.0.11, 18 CPU. Времена в выводе зависят от машины.
set -u
cd "$(dirname "$0")"

KLIB=${KLIB:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib}
STD="$KLIB/kotlin-stdlib.jar"
CO=${COROUTINES_JAR:-$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm \
  ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm -name 'kotlinx-coroutines-core-jvm-1.11.0.jar' 2>/dev/null | head -1)}
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
step() { printf '\n===== %s\n' "$*"; }

kotlinc -version 2>&1
java -version 2>&1 | head -1
echo "coroutines: $CO"
kotlinc ./*.kt -cp "$CO" -jvm-target 21 -d "$W" 2>&1 | grep -v '^warning' | grep -v '^\s*$' || true
CP="$W:$STD:$CO"

step "1. 10 000 авторизаций на одном потоке"
java -cp "$CP" pay.problem.ProblemKt
step "2. suspend без kotlinx.coroutines: COROUTINE_SUSPENDED и resume из другого потока"
java -cp "$CP" pay.mechanism.MechanismKt
step "2a. Сигнатура и state machine в байткоде"
javap -p -cp "$W" pay.mechanism.MechanismKt 'pay.mechanism.MechanismKt$authorize$1' | grep -E 'authorize|L\$0|J\$0|label|result|class'
javap -c -p -cp "$W" pay.mechanism.MechanismKt | sed -n '/Object authorize(/,/areturn$/p' | grep -E 'tableswitch|0: |1: |label|L\$0|J\$0|fraudScore|if_acmpne|areturn'
step "3. Structured concurrency, кооперативная отмена, проглоченная отмена"
java -cp "$CP" pay.structure.StructureKt
step "4. Dispatchers и блокирующий вызов"
java -cp "$CP" pay.dispatching.DispatchingKt
step "5. async, supervisorScope, Job против SupervisorJob"
java -cp "$CP" pay.errors.ErrorsKt
step "6. Flow: cold, backpressure, StateFlow, SharedFlow"
java -cp "$CP" pay.flows.FlowsKt
step "7. CompletableFuture: await, future, отмена"
java -cp "$CP" pay.interop.InteropKt
