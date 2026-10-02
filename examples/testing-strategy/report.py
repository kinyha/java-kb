# Печатает результат каждого теста из отчётов surefire. Для DoublesTest [1] — V1, [2] — V2.
import glob, sys
import xml.etree.ElementTree as ET
classes = sys.argv[1:] or ['DoublesTest']
for c in classes:
    for f in glob.glob(f'target/surefire-reports/TEST-kb.payments.{c}.xml'):
        for tc in ET.parse(f).getroot().findall('testcase'):
            st = 'FAILED' if tc.find('failure') is not None else ('ERROR' if tc.find('error') is not None else 'passed')
            name = tc.get('name') if c == 'DoublesTest' else f"{c}.{tc.get('name')}"
            print(f"{name:{49 if c == 'DoublesTest' else 68}} {st}")
