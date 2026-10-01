#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ $# -lt 1 || $# -gt 2 || ! -f "$1.java" ]]; then
  echo 'Usage: ./run.sh ContextDemo [--parameters]' >&2
  exit 1
fi
compiler_flags=(--release 21)
variant=plain
if [[ $# -eq 2 ]]; then
  if [[ "$2" != --parameters ]]; then
    echo 'Only --parameters is supported as the second argument' >&2
    exit 1
  fi
  compiler_flags+=(-parameters)
  variant=parameters
fi
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
demo_dir="target/$1-$variant"
mkdir -p "$demo_dir"
javac "${compiler_flags[@]}" -cp "$(cat target/classpath.txt)" -d "$demo_dir" "$1.java"
exec java --class-path "$demo_dir:$(cat target/classpath.txt)" "$1"
