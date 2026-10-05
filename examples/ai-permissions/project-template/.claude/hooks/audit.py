#!/usr/bin/env python3
"""Журнал прогона агента: каждая попытка (PreToolUse) и каждое выполнение (PostToolUse) — строка JSON."""
import json, sys, time

e = json.load(sys.stdin)
entry = {
    "ts": time.strftime("%H:%M:%S"),
    "event": e.get("hook_event_name"),
    "tool": e.get("tool_name"),
    "input": {k: v for k, v in (e.get("tool_input") or {}).items() if k in ("command", "file_path", "pattern")},
}
if e.get("hook_event_name") == "PostToolUse":
    r = e.get("tool_response")
    entry["ok"] = not (isinstance(r, dict) and r.get("is_error"))
with open(".claude/audit.jsonl", "a", encoding="utf-8") as f:
    f.write(json.dumps(entry, ensure_ascii=False) + "\n")
