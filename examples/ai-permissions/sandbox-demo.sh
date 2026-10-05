#!/usr/bin/env bash
# Песочница ОС на macOS: sandbox-exec (Seatbelt) — тот же механизм, на котором стоит песочница Bash в Claude Code.
# Профиль: всё разрешено, кроме записи вне рабочего каталога и исходящей сети.
set -u
W=$(mktemp -d /tmp/sbx-work.XXXX)
PROFILE="(version 1)(allow default)
(deny file-write* (require-all (require-not (subpath \"$W\")) (require-not (subpath \"/private$W\")) (require-not (literal \"/dev/null\"))))
(deny network-outbound)"
run() {
  printf '%-44s' "$1"
  if sandbox-exec -p "$PROFILE" /bin/sh -c "$2" >/dev/null 2>/tmp/sbx.err; then echo "ok"
  else rc=$?; msg=$(head -1 /tmp/sbx.err | sed -E 's#.*: ##'); echo "blocked (exit $rc)${msg:+: $msg}"; fi
}
cd "$W"
run "write inside the work dir"            "echo x > $W/result.txt"
run "write to the home directory"          "echo x > \$HOME/.sbx-demo-should-not-exist"
run "write to /tmp outside the work dir"   "echo x > /tmp/sbx-outside.txt"
run "read a file outside (allowed here)"   "cat /etc/hosts"
run "outgoing network: curl example.com"   "curl -s -m 5 https://example.com"
ls "$HOME/.sbx-demo-should-not-exist" >/dev/null 2>&1 && echo "home file exists" || echo "home file does not exist"
rm -rf "$W"
