#!/usr/bin/env bash
# Spring Modulith 2.1.1 и ArchUnit на трёх вариантах одного магазина. Нужны JDK 21 и Maven.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p target
mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=target/classpath.txt
deps=$(cat target/classpath.txt)

build() {  # build <variant> <source dirs...>
  local variant=$1; shift
  rm -rf "target/$variant" && mkdir -p "target/$variant"
  find "$@" -name '*.java' > "target/$variant.sources"
  javac --release 21 -cp "$deps" -d "target/$variant" "@target/$variant.sources"
}
build broken shared broken
build later shared broken reports
build fixed shared fixed
javac --release 21 -cp "$deps" -d target/checker StructureCheck.java
cp archunit.properties target/checker/

check() {  # check <mode> <variant>
  java -cp "target/checker:target/$2:$deps" StructureCheck "$1" "$2" "target/$2"
}
check modulith broken
check modulith fixed
rm -rf target/archunit_store          # Заморозка начинается с пустого хранилища.
check freeze broken                   # Первый запуск записывает текущие нарушения.
check freeze later                    # Новое нарушение не заморожено.
check freeze fixed                    # Исправленное нарушение уходит из хранилища.
check spring fixed
