#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ $# -lt 1 || ! -f "$1.java" ]]; then
  echo 'Usage: ./run.sh PoolDemo [args]' >&2
  exit 1
fi
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
demo="$1"; shift
exec java -Dlogback.configurationFile=logback.xml --class-path "$(cat target/classpath.txt)" "$demo.java" "$@"
