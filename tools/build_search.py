#!/usr/bin/env python3
"""Собирает assets/search.js — поисковый индекс по шагам уроков и вопросам всех тем.

Запускать после любого изменения в topics/*.html:
    python3 tools/build_search.py
"""
import html
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOPICS = os.path.join(ROOT, "topics")
OUT = os.path.join(ROOT, "assets", "search.js")

STEP = re.compile(r'<section\s+class="step"\s+id="([^"]+)"[^>]*>(.*?)</section>', re.S)
CARD = re.compile(r'<article\s+class="q"\s+id="([^"]+)"([^>]*)>(.*?)</article>', re.S)
H2 = re.compile(r"<h2[^>]*>(.*?)</h2>", re.S)
TITLE = re.compile(r"<h3[^>]*>(.*?)</h3>", re.S)
TAG = re.compile(r"<[^>]+>")
SPACE = re.compile(r"\s+")


def text(fragment):
    return SPACE.sub(" ", html.unescape(TAG.sub(" ", fragment))).strip()


def main():
    cards = []
    for name in sorted(os.listdir(TOPICS)):
        if not name.endswith(".html") or name.startswith("_"):
            continue
        topic = name[:-5]
        source = open(os.path.join(TOPICS, name), encoding="utf-8").read()
        source = re.sub(r"<!--.*?-->", "", source, flags=re.S)
        found = [("step", i, b, H2) for i, b in STEP.findall(source)]
        found += [("task" if 'data-type="task"' in attrs else "q", i, b, TITLE) for i, attrs, b in CARD.findall(source)]
        for kind, item_id, body, heading in found:
                title = heading.search(body)
                cards.append({
                    "topic": topic,
                    "id": item_id,
                    "kind": kind,
                    "title": text(title.group(1)) if title else item_id,
                    "text": text(body),
                })
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("/* Сгенерировано tools/build_search.py — руками не править. */\n")
        f.write("window.KB_SEARCH = ")
        json.dump(cards, f, ensure_ascii=False, indent=0)
        f.write(";\n")
    steps = sum(c["kind"] == "step" for c in cards)
    print(f"{os.path.relpath(OUT, ROOT)}: {steps} шагов уроков, {len(cards) - steps} вопросов и задач")


if __name__ == "__main__":
    main()
