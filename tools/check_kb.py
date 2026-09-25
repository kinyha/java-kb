#!/usr/bin/env python3
"""Проверяет связи между реестром, страницами тем и поисковым индексом."""

import contextlib
from collections import Counter
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parent.parent
TOPICS = ROOT / "topics"
ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")


class TopicPage(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.topic_id = None
        self.ids = []
        self.steps = []
        self.cards = []
        self.h1 = 0
        self.refs = 0
        self.lesson = 0
        self.interview = 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        classes = set(attrs.get("class", "").split())
        element_id = attrs.get("id")
        if element_id is not None:
            self.ids.append(element_id)
        if tag == "body":
            self.topic_id = attrs.get("data-topic")
        if tag == "h1":
            self.h1 += 1
        if "refs" in classes:
            self.refs += 1
        if "lesson" in classes:
            self.lesson += 1
        if "interview" in classes:
            self.interview += 1
        if tag == "section" and "step" in classes:
            self.steps.append(element_id)
        if tag == "article" and "q" in classes:
            self.cards.append(element_id)


def read_tree():
    code = """
const fs = require('fs');
const vm = require('vm');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync('assets/topics.js', 'utf8'), context);
process.stdout.write(JSON.stringify(context.window.KB_TREE));
"""
    result = subprocess.run(
        ["node", "-e", code], cwd=ROOT, capture_output=True, text=True, check=True
    )
    return json.loads(result.stdout)


def expected_search():
    script = ROOT / "tools" / "build_search.py"
    namespace = {"__file__": str(script), "__name__": "kb_search_check"}
    exec(compile(script.read_text(encoding="utf-8"), str(script), "exec"), namespace)
    with tempfile.TemporaryDirectory() as temporary:
        target = Path(temporary) / "search.js"
        namespace["OUT"] = str(target)
        with contextlib.redirect_stdout(io.StringIO()):
            namespace["main"]()
        return target.read_bytes()


def main():
    errors = []
    warnings = []
    topics = {}
    folder_count = 0

    def visit(nodes, path):
        nonlocal folder_count
        sibling_ids = set()
        for node in nodes:
            name = node.get("id")
            where = " › ".join(path + [str(name)])
            if not isinstance(name, str) or not ID.fullmatch(name):
                errors.append(f"{where}: некорректный id")
                continue
            if name in sibling_ids:
                errors.append(f"{where}: повторяется id среди соседей")
            sibling_ids.add(name)
            if "items" in node:
                folder_count += 1
                if not isinstance(node["items"], list):
                    errors.append(f"{where}: items должен быть списком")
                else:
                    visit(node["items"], path + [name])
                continue
            if name in topics:
                errors.append(f"{where}: id темы уже используется в {' › '.join(topics[name])}")
            topics[name] = path + [name]
            if node.get("status") not in {"empty", "draft", "ready"}:
                errors.append(f"{where}: неизвестный status")
            if node.get("freq") not in {1, 2, 3}:
                errors.append(f"{where}: freq должен быть 1, 2 или 3")
            if not node.get("title") or not node.get("scope"):
                errors.append(f"{where}: нужны title и scope")
            page = TOPICS / f"{name}.html"
            if node.get("status") == "empty":
                if page.exists():
                    errors.append(f"{where}: status empty, но файл существует")
                continue
            if not page.is_file():
                errors.append(f"{where}: нет файла {page.relative_to(ROOT)}")
                continue
            parsed = TopicPage()
            parsed.feed(page.read_text(encoding="utf-8"))
            if parsed.topic_id != name:
                errors.append(f"{page.relative_to(ROOT)}: data-topic должен быть {name}")
            if parsed.h1 != 1:
                errors.append(f"{page.relative_to(ROOT)}: нужен один h1")
            if not parsed.refs:
                errors.append(f"{page.relative_to(ROOT)}: нет блока .refs")
            if not parsed.cards or not parsed.interview:
                errors.append(f"{page.relative_to(ROOT)}: нет вопросов или задач")
            if not parsed.steps or not parsed.lesson:
                errors.append(f"{page.relative_to(ROOT)}: нет урока из шагов")
            duplicates = [item for item, count in Counter(parsed.ids).items() if count > 1]
            if duplicates:
                errors.append(f"{page.relative_to(ROOT)}: повторяются id: {', '.join(duplicates)}")
            if None in parsed.steps + parsed.cards:
                errors.append(f"{page.relative_to(ROOT)}: у шага или карточки нет id")

    try:
        tree = read_tree()
        if not isinstance(tree, list):
            raise ValueError("KB_TREE должен быть списком")
        visit(tree, [])
        listed = set(topics)
        pages = {file.stem for file in TOPICS.glob("*.html") if not file.name.startswith("_")}
        for name in sorted(pages - listed):
            warnings.append(f"topics/{name}.html: страница сохранена вне реестра")
        if (ROOT / "assets" / "search.js").read_bytes() != expected_search():
            errors.append("assets/search.js устарел: запусти python3 tools/build_search.py")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        errors.append(f"Не удалось проверить базу: {error}")

    for warning in warnings:
        print(f"ПРЕДУПРЕЖДЕНИЕ: {warning}")
    for error in errors:
        print(f"ОШИБКА: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"OK: {len(topics)} тем, {folder_count} папки, {len(pages)} страниц; поиск актуален")
    return 0


if __name__ == "__main__":
    sys.exit(main())
