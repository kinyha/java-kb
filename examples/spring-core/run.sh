#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ $# -ne 1 || ! -f "$1.java" ]]; then
  echo 'Usage: ./run.sh ContextDemo' >&2
  exit 1
fi
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
exec java --class-path "$(cat target/classpath.txt)" "$1.java"
