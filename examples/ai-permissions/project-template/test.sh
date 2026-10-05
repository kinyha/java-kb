#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
javac -d /tmp/perm-demo-out src/Fees.java && echo "compiled"
