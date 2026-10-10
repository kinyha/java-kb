#!/usr/bin/env python3
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--java-home', type=Path, default=Path.home() / '.sdkman/candidates/java/current')
args = parser.parse_args()
source = Path(__file__).resolve().parent
expected = {
    'ToolBoundaryDemo': 'shape=true; naive remaining=10\nDENY task\nDENY owner\nDENY approval\nDENY approval\nREFUND 10 remaining=90\nDENY stale version\neffects=1; approvals remaining=0\n',
    'RetrievalDemo': 'query-only cache for A=[B private note]\nscoped cache for A=[A invoice paid]\nafter ACL version change=[]\n',
    'BudgetDemo': 'turn=1 dispatched; timeout; reserved units=3\nturn=2 dispatched; timeout; reserved units=6\nturn=3 dispatched; timeout; reserved units=9\nturn=4 DENY budget; calls=3 units=9\noversized accepted=false dispatched=0\n',
}
with tempfile.TemporaryDirectory(prefix='kb-llm-risks-') as work:
    subprocess.run([str(args.java_home / 'bin/javac'), '--release', '21', '-d', work,
                    *map(str, source.glob('*.java'))], check=True)
    for name, wanted in expected.items():
        result = subprocess.run([str(args.java_home / 'bin/java'), '-cp', work, name],
                                capture_output=True, text=True, check=True)
        assert result.stdout == wanted, (name, result.stdout)
        print(name + ':\n' + result.stdout, end='')
