#!/usr/bin/env python3
"""PreToolUse-хук для Bash: детерминированный запрет опасных команд.
Код выхода 2 блокирует вызов, текст из stderr уходит модели как причина отказа."""
import json, re, sys

call = json.load(sys.stdin)
cmd = call.get("tool_input", {}).get("command", "")
RULES = [
    (r"\brm\s+-[a-zA-Z]*r[a-zA-Z]*f|\brm\s+-[a-zA-Z]*f[a-zA-Z]*r", "recursive force delete is blocked by policy"),
    (r"git\s+push\s+.*(--force|-f\b)", "force push is blocked by policy"),
    (r"db/migrations", "migrations change only through a reviewed PR"),
]
for pattern, reason in RULES:
    if re.search(pattern, cmd):
        print(f"BLOCKED by guard.py: {reason}: {cmd}", file=sys.stderr)
        sys.exit(2)
sys.exit(0)
