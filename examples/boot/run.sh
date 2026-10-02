#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ $# -lt 1 || ! -f "$1.java" ]]; then
  echo 'Usage: ./run.sh AutoConfigDemo [args]' >&2
  exit 1
fi
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
demo="$1"; shift
mkdir -p "target/$demo"
if [[ "$demo" == AutoConfigDemo || "$demo" == ConfigDemo ]]; then
  cp -R resources/. "target/$demo/"
fi
sources=("$demo.java")
if [[ "$demo" == ShutdownDemo ]]; then sources+=(StartupDemo.java); fi
javac --release 21 -parameters -cp "$(cat target/classpath.txt)" -d "target/$demo" "${sources[@]}"
exec java --class-path "target/$demo:$(cat target/classpath.txt)" "kb.boot.$demo" "$@"
