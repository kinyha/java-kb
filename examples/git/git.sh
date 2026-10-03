#!/usr/bin/env bash
# Опыты с git на временном репозитории: merge и rebase, конфликт, cherry-pick, revert, reflog, bisect.
set -u
D=$(mktemp -d); cd "$D"
export GIT_COMMITTER_DATE="@1790009999 +0000" GIT_AUTHOR_DATE="@1790009999 +0000"   # одинаковые хеши при каждом запуске
export GIT_AUTHOR_NAME=dev GIT_AUTHOR_EMAIL=dev@example.com GIT_COMMITTER_NAME=dev GIT_COMMITTER_EMAIL=dev@example.com
t=0; c() { t=$((t+60)); GIT_AUTHOR_DATE="2026-10-01T10:00:00+00:00 +$t" GIT_COMMITTER_DATE="@$((1790000000+t)) +0000" git commit -q "$@"; }
g() { git log --graph --format='%s' "$@" | sed 's/^/   /'; }
git init -q -b main
echo "fee = 1%" > fee.txt; git add .; c -m "A: fee 1%"
echo "limit = 1000" > limit.txt; git add .; c -m "B: limit"
git switch -q -c feature
echo "cashback = 2%" > cashback.txt; git add .; c -m "F1: cashback"
echo "cashback = 3%" > cashback.txt; c -am "F2: cashback 3%"
git switch -q main
echo "currency = EUR" > currency.txt; git add .; c -m "C: currency"

echo "== merge: history keeps both lines and a merge commit"
git switch -q -c merged feature; git switch -q main; git branch -q m main
git switch -q m; git merge -q --no-edit feature -m "M: merge feature"; g
echo "== what a commit is: git cat-file -p M"
git cat-file -p HEAD | grep -v '^$' | grep -E '^(tree|parent|author|M:)' | sed -E 's/ <dev@example.com>.*//; s/^/   /'
echo "   tree of M: $(git ls-tree --name-only HEAD | tr '\n' ' ')"
echo "   branch file: .git/refs/heads/m -> $(git rev-parse --short m), HEAD -> $(cat .git/HEAD)"
echo "== rebase: feature commits are re-created on top of main"
git switch -q -c rebased feature; F1=$(git rev-parse --short HEAD~1)
git rebase -q main; g main..rebased; g -1 main
echo "   F1 before rebase $F1, after $(git rev-parse --short HEAD~1): new commits, new hashes"

echo "== conflict: both branches change the same line"
git switch -q main; git switch -q -c fee-2; echo "fee = 2%" > fee.txt; c -am "D: fee 2%"
git switch -q main; echo "fee = 1.5%" > fee.txt; c -am "E: fee 1.5%"
git merge -q fee-2 >/dev/null 2>&1; echo "   git status: $(git status --short)"; sed 's/^/   /' fee.txt
echo "fee = 2%" > fee.txt; git add fee.txt; c -m "M2: resolve fee"; echo "   resolved: $(cat fee.txt)"

echo "== cherry-pick a fix and revert a bad commit"
git branch release main                                   # релизная ветка отделилась раньше хотфикса
echo "limit = 500" > limit.txt; c -am "H: hotfix limit 500"
git switch -q release; git cherry-pick main >/dev/null; echo "   release got: $(git log -1 --format=%s), limit = $(cut -d' ' -f3 limit.txt)"
git switch -q main; git revert --no-edit HEAD >/dev/null; echo "   main after revert: $(git log -1 --format=%s | cut -c1-40), limit = $(cut -d' ' -f3 limit.txt)"

echo "== reset --hard loses a commit; reflog finds it"
echo "x" > important.txt; git add .; c -m "I: important work"
lost=$(git rev-parse --short HEAD); git reset -q --hard HEAD~1
echo "   after reset: important.txt exists: $([ -f important.txt ] && echo yes || echo no)"
found=$(git reflog --format='%h %gs' | grep -m1 'I: important' | cut -d' ' -f1)
git reset -q --hard "$found"; echo "   reflog: $lost found again, important.txt exists: $([ -f important.txt ] && echo yes || echo no)"

echo "== bisect: find the commit that broke the test among 32"
git switch -q -c perf main; v=10
for i in $(seq 1 32); do [ $i -eq 23 ] && v=99; printf "build=%s\nlatency=%s\n" $i $v > perf.txt; git add .; c -m "P$i"; done
git bisect start HEAD HEAD~32 >/dev/null
out=$(git bisect run sh -c '[ "$(grep latency perf.txt | cut -d= -f2)" -lt 50 ]' 2>&1)
echo "   first bad: $(echo "$out" | grep -A6 -E "is the first '?bad'? commit" | grep -m1 -E '^ +P[0-9]+' | tr -d ' '), test runs: $(echo "$out" | grep -c '^running')"
git bisect reset >/dev/null 2>&1
rm -rf "$D"
