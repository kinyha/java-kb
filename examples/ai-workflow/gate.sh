#!/usr/bin/env bash
# Гейт после работы агента: решение принимает скрипт, а не текст агента.
#   1) тест не изменён с красного коммита;  2) тесты запускаем сами;  3) дифф только в ожидаемых файлах.
set -u
cd "${1:?usage: gate.sh <repo> <red-commit>}"
RED=${2:?red commit}
ok=1
if git diff --quiet "$RED" -- RefundsTest.java; then echo "test unchanged since red:   yes"; else echo "test unchanged since red:   NO"; ok=0; fi
if ./test.sh > /tmp/gate-test.log 2>&1; then echo "./test.sh (run by gate):   exit 0"; else echo "./test.sh (run by gate):   FAILED"; ok=0; fi
CHANGED=$(git status --porcelain | grep -v '^?? out/' | awk '{print $2}' | sort | tr '\n' ' ')
echo "changed files:             $CHANGED"
case " $CHANGED" in *SPEC.md*|*test.sh*|*CLAUDE.md*) echo "protected file changed:    YES"; ok=0;; *) echo "protected file changed:    no";; esac
[ $ok = 1 ] && echo "GATE: PASS" || echo "GATE: FAIL"
