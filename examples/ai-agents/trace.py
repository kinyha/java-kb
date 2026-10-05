#!/usr/bin/env python3
"""Читает поток событий claude -p --output-format stream-json и печатает цикл агента по шагам.

  python3 trace.py runs/run1.jsonl            # разбор своего прогона
  python3 trace.py --sanitize in.jsonl > out  # оставить только события цикла, без путей и id сессии
"""
import json, re, sys

def short(s, n=110):
    s = re.sub(r'/(private/)?tmp/(agent|workflow)-demo\.\w+', '$DEMO', str(s)).replace('\n', ' | ')
    return s if len(s) <= n else s[:n - 1] + '…'

def sanitize(path):
    for line in open(path, encoding='utf-8'):
        e = json.loads(line); t = e.get('type')
        if t == 'system' and e.get('subtype') == 'init':
            print(json.dumps({'type': t, 'subtype': 'init', 'model': e['model'], 'permissionMode': e['permissionMode'],
                              'tools': e['tools']}, ensure_ascii=False))
        elif t in ('assistant', 'user'):
            m = e['message']; out = {'type': t, 'message': {'content': []}}
            for c in m['content'] if isinstance(m['content'], list) else []:
                if c.get('type') == 'thinking': continue
                c = dict(c); c.pop('signature', None)
                out['message']['content'].append(json.loads(short(json.dumps(c, ensure_ascii=False), 10**6)))
            if 'usage' in m:
                u = m['usage']; out['message']['usage'] = {k: u[k] for k in ('input_tokens', 'cache_creation_input_tokens', 'cache_read_input_tokens', 'output_tokens')}
            if out['message']['content']: print(json.dumps(out, ensure_ascii=False))
        elif t == 'result':
            out = {k: e[k] for k in ('type', 'subtype', 'num_turns', 'total_cost_usd', 'duration_ms', 'result', 'permission_denials')}
            print(short(json.dumps(out, ensure_ascii=False), 10**6))

def trace(path):
    calls, last_usage = 0, None
    denied = set()                                   # отказ гейта — только то, что агент сам записал в permission_denials
    for line in open(path, encoding='utf-8'):
        e = json.loads(line)
        if e.get('type') == 'result': denied = {d['tool_use_id'] for d in e['permission_denials']}
    for line in open(path, encoding='utf-8'):
        e = json.loads(line); t = e.get('type')
        if t == 'system' and e.get('subtype') == 'init':
            print(f"init  model={e['model']} mode={e['permissionMode']} tools={len(e['tools'])}")
        elif t == 'assistant':
            u = e['message'].get('usage')
            if u and u != last_usage:
                calls += 1; last_usage = u
                ctx = u['input_tokens'] + u['cache_creation_input_tokens'] + u['cache_read_input_tokens']
                print(f"call {calls}  context={ctx} tokens (cached {u['cache_read_input_tokens']})")
            for c in e['message']['content']:
                if c.get('type') == 'tool_use':
                    print(f"  -> {c['name']} {short(json.dumps(c['input'], ensure_ascii=False))}")
                elif c.get('type') == 'text':
                    print(f"  text: {short(c['text'])}")
        elif t == 'user':
            for c in e['message']['content']:
                if isinstance(c, dict) and c.get('type') == 'tool_result':
                    tag = 'DENIED' if c.get('tool_use_id') in denied else ('error' if c.get('is_error') else 'ok')
                    content = c['content'] if isinstance(c['content'], str) else ' '.join(x.get('text', '') for x in c['content'])
                    print(f"  <- {tag}: {short(content, 90)}")
        elif t == 'result':
            print(f"result  num_turns={e['num_turns']} cost=${e['total_cost_usd']:.4f} denied={len(e['permission_denials'])}")
            print(f"answer: {short(e['result'], 200)}")

if __name__ == '__main__':
    if sys.argv[1] == '--sanitize': sanitize(sys.argv[2])
    else: trace(sys.argv[1])
