#!/usr/bin/env python3
"""Печатает итоговый ответ агента из записи прогона: python3 answer.py recorded/step3.jsonl"""
import json, sys
print([json.loads(l) for l in open(sys.argv[1], encoding='utf-8')][-1]['result'])
