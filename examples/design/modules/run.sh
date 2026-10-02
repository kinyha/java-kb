#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p target
mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=target/classpath.txt
deps=$(cat target/classpath.txt)
for variant in broken port fixed; do
  mkdir -p "target/$variant"
  find "target/$variant" -name '*.class' -delete
  find shared -name '*.java' > "target/$variant.sources"
  if [[ "$variant" == port ]]; then
    find fixed -name '*.java' ! -name NotificationSender.java >> "target/$variant.sources"
    echo broken/kb/modules/notifications/NotificationSender.java >> "target/$variant.sources"
  else
    find "$variant" -name '*.java' >> "target/$variant.sources"
  fi
  javac --release 21 -d "target/$variant" "@target/$variant.sources"
done
javac --release 21 -cp "$deps" -d target/checker ModuleDemo.java
java -cp "target/checker:$deps" ModuleDemo target
