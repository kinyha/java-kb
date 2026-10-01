#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ $# -lt 1 || ! -f "$1.java" ]]; then
  echo 'Usage: ./run.sh PoolDemo [args]' >&2
  exit 1
fi
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
demo="$1"; shift
mkdir -p "target/$demo"
# Компилируем без -parameters: MvcArgumentsDemo показывает, что тогда происходит.
javac --release 21 -nowarn -XDsuppressNotes -cp "$(cat target/classpath.txt)" -d "target/$demo" "$demo.java"
exec java -Dlogback.configurationFile=logback.xml --class-path "target/$demo:$(cat target/classpath.txt)" "$demo" "$@"
