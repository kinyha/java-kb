#!/usr/bin/env bash
# Все опыты темы Java Upgrade. Запуск из корня Java KB: examples/java-upgrade/run.sh
# JDK 21 и JDK 25 задаются через J21 и J25 (каталоги bin). Скрипт печатает вывод каждого опыта.
set -u
cd "$(dirname "$0")"

J21=${J21:-$HOME/.sdkman/candidates/java/21.0.11-jbr/bin}
J25=${J25:-"/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin"}
M2=${M2:-$HOME/.m2/repository}
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

step() { printf '\n===== %s\n' "$*"; }
# JBR 21 по умолчанию игнорирует неизвестные -XX флаги (IgnoreUnrecognizedVMOptions=true),
# обычная OpenJDK — нет. Возвращаем стандартное поведение явно.
STRICT=-XX:-IgnoreUnrecognizedVMOptions

"$J21/java" -version 2>&1 | head -1
"$J25/java" -version 2>&1 | head -1

step "1. Удалённый JAXB: библиотека собрана под 8, сервис запущен на 21"
"$J21/javac" --release 8 -Xlint:-options -d "$W/bank" jaxb/BankClient.java
"$J21/jar" --create --file "$W/bank-lib.jar" -C "$W/bank" .
"$J21/javac" --release 21 -cp "$W/bank-lib.jar" -d "$W/app" jaxb/PaymentService.java
"$J21/java" -cp "$W/bank-lib.jar:$W/app" app.PaymentService 2>&1 | head -6
step "1a. Тот же код с --release 11"
"$J21/javac" --release 11 -d "$W/bank11" jaxb/BankClient.java 2>&1 | head -2
step "1b. jdeps находит дыру до запуска"
"$J21/jdeps" "$W/bank-lib.jar"
step "1c. jakarta.xml.bind-api 4.x в classpath не помогает"
"$J21/jar" tf "$M2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.5/jakarta.xml.bind-api-4.0.5.jar" | grep 'DatatypeConverter.class'
"$J21/java" -cp "$W/bank-lib.jar:$W/app:$M2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.5/jakarta.xml.bind-api-4.0.5.jar" \
  app.PaymentService 2>&1 | sed -n 3p

step "2. Версия class-файла и откат"
"$J25/javac" -d "$W/cv25" classversion/Receipt.java
"$J25/javac" --release 21 -d "$W/cv21" classversion/Receipt.java
"$J21/javap" -v "$W/cv25/Receipt.class" | grep major
"$J21/javap" -v "$W/cv21/Receipt.class" | grep major
"$J25/java" -cp "$W/cv25" Receipt
"$J21/java" -cp "$W/cv25" Receipt 2>&1
"$J21/java" -cp "$W/cv21" Receipt
step "2a. ASM внутри библиотек читает байткод сам"
ASM=$M2/org/ow2/asm/asm
"$J21/java" -cp "$ASM/9.7.1/asm-9.7.1.jar" classversion/ScanClass.java "$W/cv25/Receipt.class" 2>&1 | head -2
"$J21/java" -cp "$ASM/9.8/asm-9.8.jar" classversion/ScanClass.java "$W/cv25/Receipt.class"
"$J21/java" -cp "$ASM/9.7.1/asm-9.7.1.jar" classversion/ScanClass.java "$W/cv21/Receipt.class"
step "2b. Тот же ASM 9.7.1 и класс самой JDK: на 21 и на 25"
"$J21/java" -cp "$ASM/9.7.1/asm-9.7.1.jar" classversion/ScanClass.java java/lang/String.class 2>&1 | head -2
"$J25/java" -cp "$ASM/9.7.1/asm-9.7.1.jar" classversion/ScanClass.java java/lang/String.class 2>&1 | head -2

step "3. Strong encapsulation"
"$J21/java" encapsulation/LegacySerializer.java 2>&1 | head -2
"$J21/java" --add-opens java.base/java.math=ALL-UNNAMED encapsulation/LegacySerializer.java
"$J21/javac" -d "$W/enc" encapsulation/LegacySerializer.java
printf 'Main-Class: LegacySerializer\nAdd-Opens: java.base/java.math\n' > "$W/manifest.txt"
"$J21/jar" --create --file "$W/enc.jar" --manifest "$W/manifest.txt" -C "$W/enc" .
echo "-- java -jar с Add-Opens в манифесте"
"$J21/java" -jar "$W/enc.jar" | head -1
echo "-- тот же JAR через -cp"
"$J21/java" -cp "$W/enc.jar" LegacySerializer 2>&1 | head -1

step "4. Integrity by default: 21 против 25"
"$J21/javac" --release 21 -XDignore.symbol.file -Xlint:none -d "$W/int" integrity/*.java 2>/dev/null
echo "-- Unsafe на 21"; "$J21/java" -cp "$W/int" OffHeapBuffer
echo "-- Unsafe на 25"; "$J25/java" -cp "$W/int" OffHeapBuffer 2>&1 | sed -E "s#(/private)?$W#<tmp>#"
echo "-- Unsafe на 25 с deny"; "$J25/java" --sun-misc-unsafe-memory-access=deny -cp "$W/int" OffHeapBuffer 2>&1 | head -1
echo "-- Security Manager на 21 с allow"; "$J21/java" -Djava.security.manager=allow -cp "$W/int" SandboxProbe 2>&1 | sed -E "s#(/private)?$W#<tmp>#"
echo "-- Security Manager на 25 с allow"; "$J25/java" -Djava.security.manager=allow -cp "$W/int" SandboxProbe 2>&1 | head -2
echo "-- Security Manager на 25 без флага"; "$J25/java" -cp "$W/int" SandboxProbe 2>&1 | head -2
step "4a. jdeprscan и jdeps по готовому JAR"
"$J21/jar" --create --file "$W/legacy.jar" -C "$W/int" .
"$J21/jdeprscan" --for-removal "$W/legacy.jar" | tail -n +2
"$J21/jdeps" --jdk-internals "$W/legacy.jar" | head -2

step "5. Флаги JVM: deprecated, obsolete, expired"
for J in "$J21" "$J25"; do
  echo "-- $("$J/java" -version 2>&1 | head -1)"
  for f in -XX:MaxRAMFraction=2 -XX:+G1UsePreventiveGC -XX:+PrintGCDateStamps -XX:+UseConcMarkSweepGC --illegal-access=permit; do
    echo "$f:"; "$J/java" $STRICT $f -version 2>&1 | grep -E 'warning|Unrecognized'
  done
done

echo "-- скрипт запуска из карточки: -Djava.security.manager=allow -XX:MaxRAMFraction=2"
for J in "$J21" "$J25"; do
  "$J/java" $STRICT -Djava.security.manager=allow -XX:MaxRAMFraction=2 -version 2>&1 | grep -E 'warning|Unrecognized|Error:'
done

step "6. Тихие изменения поведения"
"$J21/javac" --release 21 -d "$W/beh" behavior/ReceiptTime.java
echo "-- 21";             "$J21/java" -cp "$W/beh" ReceiptTime
echo "-- 21 COMPAT";      "$J21/java" -Djava.locale.providers=COMPAT -cp "$W/beh" ReceiptTime 2>&1 | grep -v LocaleProviderAdapter
echo "-- 25 COMPAT";      "$J25/java" -Djava.locale.providers=COMPAT -cp "$W/beh" ReceiptTime 2>&1 | grep -v LocaleProviderAdapter
echo "-- 21, LANG=C";     env -i PATH=/usr/bin LANG=C "$J21/java" -cp "$W/beh" ReceiptTime | tail -1
echo "-- 21, LANG=C, file.encoding=COMPAT"; env -i PATH=/usr/bin LANG=C "$J21/java" -Dfile.encoding=COMPAT -cp "$W/beh" ReceiptTime | tail -1
echo "-- GC при одном CPU"
"$J21/java" -XX:ActiveProcessorCount=1 -Xlog:gc -version 2>&1 | grep Using
"$J25/java" -XX:ActiveProcessorCount=1 -Xlog:gc -version 2>&1 | grep Using

step "7. javax -> jakarta: @PostConstruct в Spring 7"
CP=$(echo "$M2"/org/springframework/spring-{context,core,beans,aop,expression}/7.0.9/*-7.0.9.jar \
  "$M2"/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.jar \
  "$M2"/io/micrometer/micrometer-{observation,commons}/1.16.7/*-1.16.7.jar \
  "$M2"/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar \
  "$M2"/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2.jar \
  "$M2"/jakarta/annotation/jakarta.annotation-api/3.0.0/jakarta.annotation-api-3.0.0.jar | tr ' ' ':')
"$J21/java" -cp "$CP" spring/RateCacheDemo.java

step "8. Multi-release JAR"
"$J21/javac" --release 8 -Xlint:-options -d "$W/mr8" mrjar/base/lib/*.java
"$J21/javac" --release 21 -cp "$W/mr8" -d "$W/mr21" mrjar/java21/lib/Workers.java
"$J21/jar" --create --file "$W/workers.jar" --main-class lib.Main -C "$W/mr8" . --release 21 -C "$W/mr21" .
"$J21/jar" tf "$W/workers.jar" | grep class
"$J21/java" -jar "$W/workers.jar"
"$J25/java" -jar "$W/workers.jar"
"$J21/java" -Djdk.util.jar.enableMultiRelease=false -jar "$W/workers.jar"
