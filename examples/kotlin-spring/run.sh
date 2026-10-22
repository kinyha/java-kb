#!/usr/bin/env bash
# Опыты темы Kotlin with Spring & JPA. Запуск из корня Java KB: examples/kotlin-spring/run.sh
# Нужны JDK 21, kotlinc 2.x и Gradle (только чтобы собрать classpath). OFFLINE=1 — без обращения к сети.
# Шаги 3a и 5a нужны jar, которых обычно нет в кеше: каталог с ними задаёт EXTRA_JARS
# (jackson-module-kotlin-3.1.5.jar, kotlinx-coroutines-reactor-1.11.0.jar, kotlinx-coroutines-reactive-1.11.0.jar с Maven Central).
# Проверено: kotlinc 2.4.10, Spring 7.0.9, Hibernate 7.4.1, H2 2.4.240, Jackson 3.1.5, JBR 21.0.11.
set -u
cd "$(dirname "$0")"

KLIB=${KLIB:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib}
STD="$KLIB/kotlin-stdlib.jar"
REFLECT="$KLIB/kotlin-reflect.jar"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
step() { printf '\n===== %s\n' "$*"; }
# JUL-логи Spring и Hibernate (строка с датой, WARNING/INFO, стек) убираем, чтобы был виден результат опыта
quiet() { grep -vE '^[A-Z][a-z]{2} [0-9]+, 20|^Offending resource|^(INFO|WARNING|WARN):|^\s+at |^\s+\.\.\.|^\s+(Database|Default|Autocommit|Isolation|JDBC|Pool|Minimum|Maximum)'; }

CP=$(gradle -q -p classpath --project-cache-dir "$W/gradle" ${OFFLINE:+--offline} cp | sed -n 's/^CP=//p')   # кеш проекта — во временной папке
SPRING_OPEN="-Xplugin=$KLIB/allopen-compiler-plugin.jar -P plugin:org.jetbrains.kotlin.allopen:preset=spring"
JPA_NOARG="-Xplugin=$KLIB/noarg-compiler-plugin.jar -P plugin:org.jetbrains.kotlin.noarg:preset=jpa"
ENTITY_OPEN="-Xplugin=$KLIB/allopen-compiler-plugin.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.Entity"
kc() { local out=$1; shift; mkdir -p "$out"; kotlinc "$@" -cp "$CP" -jvm-target 21 -d "$out" 2>&1 | grep -E 'error' || true; }

kotlinc -version 2>&1
java -version 2>&1 | head -1

step "1. Прокси Spring: без плагина и с kotlin-spring"
kc "$W/proxy-plain" proxy/Proxies.kt
kc "$W/proxy-spring" proxy/Proxies.kt $SPRING_OPEN
echo "=== без плагина"
java -cp "$W/proxy-plain:$STD:$CP" pay.proxy.ProxiesKt 2>&1 | quiet
echo "=== с kotlin-spring"
java -cp "$W/proxy-spring:$STD:$CP" pay.proxy.ProxiesKt 2>&1 | quiet
step "1a. final-методы в байткоде"
javap -p -cp "$W/proxy-plain" pay.proxy.RefundService pay.proxy.PaymentService | grep -E 'class|refund|pay\('
javap -p -cp "$W/proxy-spring" pay.proxy.RefundService pay.proxy.PaymentService | grep -E 'class|refund|pay\('

step "2. JPA: без плагинов, kotlin-jpa, kotlin-jpa + all-open для @Entity"
kc "$W/jpa-plain" jpa/*.kt
kc "$W/jpa-noarg" jpa/*.kt $JPA_NOARG
kc "$W/jpa-open" jpa/*.kt $JPA_NOARG $ENTITY_OPEN
for m in plain noarg open; do
  echo "=== $m"
  java -cp "$W/jpa-$m:$STD:$CP" pay.jpa.JpaDemoKt 2>&1 | grep -E '^[0-9]\. |^Exception in thread'
done

step "3. Jackson 3 без Kotlin-модуля: без -java-parameters и с ним"
kc "$W/json-plain" jackson/JacksonDemo.kt
kc "$W/json-params" jackson/JacksonDemo.kt -java-parameters
echo "== kotlinc без флагов"; java -cp "$W/json-plain:$STD:$CP" pay.jackson.JacksonDemoKt | head -1
echo "== kotlinc -java-parameters"; java -cp "$W/json-params:$STD:$CP" pay.jackson.JacksonDemoKt

if [ -n "${EXTRA_JARS:-}" ]; then
  echo "== jackson-module-kotlin 3.1.5 + kotlin-reflect, kotlinc без флагов"
  java -cp "$W/json-plain:$STD:$REFLECT:$CP:$EXTRA_JARS/jackson-module-kotlin-3.1.5.jar" pay.jackson.JacksonDemoKt --kotlin-module
else echo "(3a пропущен: нет EXTRA_JARS)"; fi

step "4. Null safety: JSpecify в Spring 7 (ожидается ошибка компиляции) и nullable-зависимость"
kc "$W/jdbc" nulls/JdbcNulls.kt
kc "$W/beans" nulls/OptionalBeans.kt
echo "-- без kotlin-reflect в classpath"; java -cp "$W/beans:$STD:$CP" pay.nulls.OptionalBeansKt 2>&1 | quiet
echo "-- с kotlin-reflect"; java -cp "$W/beans:$STD:$REFLECT:$CP" pay.nulls.OptionalBeansKt 2>&1 | quiet

step "5. Как Spring вызывает suspend-функции (байткод spring-core)"
CORE=$(tr ':' '\n' <<<"$CP" | grep 'spring-core-')
javap -c -p -cp "$CORE" org.springframework.core.CoroutinesUtils | grep -E 'public static .*invokeSuspendingFunction|getUnconfined|MonoKt.mono' | head -6
TX=$(tr ':' '\n' <<<"$CP" | grep 'spring-tx-')
javap -c -p -cp "$TX" org.springframework.transaction.interceptor.TransactionAspectSupport | grep -E 'instanceof .*ReactiveTransactionManager|isSuspendingFunction' | head -3

step "5a. suspend-контроллер в MockMvc и @Transactional на suspend-функции"
if [ -n "${EXTRA_JARS:-}" ]; then
  CO=$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm -name 'kotlinx-coroutines-core-jvm-1.11.0.jar' 2>/dev/null | head -1)
  RC=$(find ~/.m2/repository/io/projectreactor/reactor-core ~/.gradle/caches/modules-2/files-2.1/io.projectreactor/reactor-core -name 'reactor-core-3.8.*.jar' 2>/dev/null | grep -v sources | head -1)
  RS=$(find ~/.m2/repository/org/reactivestreams ~/.gradle/caches/modules-2/files-2.1/org.reactivestreams -name 'reactive-streams-1.0.4.jar' 2>/dev/null | head -1)
  NOREACTOR="$CO:$RC:$RS:$REFLECT"
  XCP="$NOREACTOR:$EXTRA_JARS/kotlinx-coroutines-reactor-1.11.0.jar:$EXTRA_JARS/kotlinx-coroutines-reactive-1.11.0.jar"
  mkdir -p "$W/co"
  kotlinc coroutines/*.kt -cp "$CP:$XCP" -jvm-target 21 $SPRING_OPEN -d "$W/co" 2>&1 | grep -E 'error' || true
  echo "== controller"; java -cp "$W/co:$STD:$CP:$XCP" pay.coroutines.SuspendControllerKt 2>&1 | quiet
  echo "== controller без kotlinx-coroutines-reactor"
  java -cp "$W/co:$STD:$CP:$NOREACTOR" pay.coroutines.SuspendControllerKt 2>&1 | grep -m1 NoClassDefFoundError
  echo "== @Transactional suspend + PlatformTransactionManager"; java -cp "$W/co:$STD:$CP:$XCP" pay.coroutines.SuspendTxKt 2>&1 | quiet
else echo "(пропущен: нет EXTRA_JARS)"; fi
