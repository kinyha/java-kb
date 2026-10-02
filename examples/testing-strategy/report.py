# Печатает результат каждого теста DoublesTest из отчёта surefire; [1] — V1, [2] — V2.
import xml.etree.ElementTree as ET
r = ET.parse('target/surefire-reports/TEST-kb.payments.DoublesTest.xml').getroot()
for tc in r.findall('testcase'):
    st = 'FAILED' if tc.find('failure') is not None else ('ERROR' if tc.find('error') is not None else 'passed')
    print(f"{tc.get('name'):49} {st}")
