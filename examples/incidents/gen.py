# Генерирует тест promtool: probe — пустые ожидания (promtool покажет, что сработало),
# table — таблица из ответа promtool, expect — тест с ожиданиями, равными найденному.
import re, sys
SCEN = {   # счётчик ошибок в минуту (нотация promtool: старт+шаг x число шагов)
    'blip':  '0+0x1500 300+0x300',              # 1 минута: 300 ошибок (30% запросов этой минуты)
    'spike': '0+0x1500 0+20x60 1200+0x240',     # 60 минут: 2% ошибок
    'smolder': '0+0x1500 0+10x240 2400+0x60',    # 4 часа: 1% ошибок
    'leak':  '0+3x1800',                        # всё время: 0.3% ошибок
}
ALERTS = ['ErrorRateAbove1Percent', 'ErrorBudgetFastBurn', 'ErrorBudgetSlowBurn', 'ErrorBudgetTicket']
TIMES = ['25h3m', '25h10m', '25h50m', '26h5m', '29h']
SEV = {'ErrorBudgetTicket': 'ticket'}

def parse(path):
    txt = open(path).read(); got = {}
    for m in re.finditer(r'alertname: (\w+), time: ([\ddhms]+),.*?got:\[(.*?)\n\s*\]', txt, re.S):
        got[(m.group(2), m.group(1))] = sorted(set(re.findall(r'job="(\w+)"', m.group(3))))
    return got

def norm(t):   # 25h3m -> 1d1h3m, как печатает promtool
    h, m = re.match(r'(\d+)h(?:(\d+)m)?', t).groups(); h = int(h); m = int(m or 0)
    s = (f'{h//24}d' if h >= 24 else '') + (f'{h%24}h' if h % 24 else '') + (f'{m}m' if m else '')
    return s

def test(expect):
    L = ['rule_files: [alerts.yml]', 'evaluation_interval: 1m', 'tests:', '  - interval: 1m', '    input_series:']
    for j, e in SCEN.items():
        L += [f"      - series: 'requests_total{{job=\"{j}\",code=\"200\"}}'", "        values: '0+1000x1800'",
              f"      - series: 'requests_total{{job=\"{j}\",code=\"500\"}}'", f"        values: '{e}'"]
    L.append('    alert_rule_test:')
    for t in TIMES:
        for a in ALERTS:
            L += [f'      - eval_time: {t}', f'        alertname: {a}']
            jobs = expect.get((norm(t), a), []) if expect is not None else []
            if not jobs: L.append('        exp_alerts: []')
            else:
                L.append('        exp_alerts:')
                for j in jobs: L.append(f"          - exp_labels: {{ job: {j}, severity: {SEV.get(a, 'page')} }}")
    print('\n'.join(L))

mode = sys.argv[1]
if mode == 'probe': test(None)
elif mode == 'expect': test(parse(sys.argv[2]))
else:
    got = parse(sys.argv[2])
    print('== which alerts fire\n   (jobs: blip = 1 min of 30% errors, spike = 60 min of 2%, smolder = 4 h of 1%, leak = 0.3% all day)')
    print('   time    ' + '  '.join(f'{a.replace("ErrorBudget","").replace("ErrorRateAbove1Percent","Rate>1%"):<11}' for a in ALERTS))
    for t in TIMES:
        print(f'   {t:<7} ' + '  '.join(f'{",".join(got.get((norm(t), a), [])) or "-":<11}' for a in ALERTS))
