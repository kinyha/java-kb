#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Run JDK 21 demos and deliberate compiler failures in temporary directories."""
import os
from pathlib import Path
import subprocess
import tempfile

base = Path(__file__).resolve().parent
java_dir = Path(os.environ.get('JAVA_KB_JDK', Path.home() / '.sdkman/candidates/java/current')) / 'bin'
java, javac, javap = (str(java_dir / command) for command in ('java', 'javac', 'javap'))

def run(command, expected=0):
    result = subprocess.run(command, text=True, capture_output=True)
    assert (result.returncode == 0) == (expected == 0), result.stdout + result.stderr
    return result.stdout + result.stderr

outputs = {
    'PatternDemo': 'legacy retry=500\nid=p1, positive=true\nnull instanceof=false\nbook:p1\nmanual\nmanual\nreject:limit\nretry:30\nbad request\nguarded 1=positive\n',
    'HierarchyDemo': 'sealed=true\ndirect=[Paid, Rejected, Temporary]\nexternal sealed=false\nprovider=external\n',
    'EdgeDemo': 'default null=NullPointerException\nObject null=NullPointerException\nnull record=false\ncomponent=null\nnull nested=MatchException\nnested component=null\naccessor=MatchException, cause=IllegalStateException\n',
    'GenericDemo': 'number=42\n',
}
for name, expected in outputs.items():
    actual = run([java, str(base / (name + '.java'))])
    assert actual == expected, (name, actual)
    print(name + ': OK')

with tempfile.TemporaryDirectory(prefix='java-kb-patterns-') as directory:
    work = Path(directory)
    for name, diagnostic in {
        'MissingCase': 'does not cover all possible input values',
        'Dominated': 'dominated by a preceding case label',
        'OrScope': 'cannot find symbol',
        'Unchecked': 'cannot be safely cast',
    }.items():
        result = run([javac, '-d', str(work), str(base / 'negative' / (name + '.java'))], 1)
        assert diagnostic in result, result
        print(name + ': expected compilation error\n' + result)
    run([javac, '-d', str(work), str(base / 'evolution/v1/Outcome.java'), str(base / 'evolution/v1/Client.java')])
    # Replace only the API classes. Client.class stays compiled against v1.
    run([javac, '-cp', str(work), '-d', str(work), str(base / 'evolution/v2/Outcome.java'), str(base / 'evolution/v2/Run.java')])
    result = run([java, '-cp', str(work), 'Run'])
    assert result == 'old client=MatchException\n', result
    print(result, end='')
    bytecode = run([javap, '-c', '-classpath', str(work), 'Client'])
    assert 'java/lang/MatchException' in bytecode, bytecode
    print('Client bytecode: synthetic MatchException branch confirmed')
    result = run([javac, '-cp', str(work), '-d', str(work), str(base / 'evolution/v1/Client.java')], 1)
    assert 'does not cover all possible input values' in result, result
    print('Client recompile: expected missing Retry error\n' + result)
