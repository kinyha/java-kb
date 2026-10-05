#!/usr/bin/env bash
# Копирует учебный проект с правилами и хуками во временный каталог и печатает путь.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
D=$(mktemp -d /tmp/perm-demo.XXXX)
cp -R "$HERE/project-template/." "$D/"
(cd "$D" && git init -q && git add -A && git -c user.name=demo -c user.email=demo@example.com commit -qm init)
echo "$D"
