#!/usr/bin/env python3
"""Run version-dependent behavioral probes without editing or generating repository files."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--java21-home', type=Path, default=Path.home() / '.sdkman/candidates/java/current')
parser.add_argument('--java25-home', type=Path, required=True)
parser.add_argument('--evidence-dir', type=Path)
args = parser.parse_args()
source = Path(__file__).resolve().parent
if args.evidence_dir:
    args.evidence_dir.mkdir(parents=True, exist_ok=True)

def call(label, home, tool, options, succeeds=True, contains=None):
    p = subprocess.run([str(home / 'bin' / tool), *map(str, options)], capture_output=True, text=True, timeout=30)
    assert (p.returncode == 0) == succeeds, (label, p.returncode, p.stderr)
    if contains:
        assert contains in p.stdout + p.stderr, (label, p.stdout, p.stderr)
    if args.evidence_dir:
        (args.evidence_dir / (label + '.txt')).write_text(p.stdout + p.stderr)
    return p

def say(label, value):
    print(label + ': ' + value)
    if args.evidence_dir:
        (args.evidence_dir / (label + '.out')).write_text(value + '\n')

expected21 = {
    'VarSwitchDemo': 'overload=ArrayList / List\ndiamond=[10, note]\nreceipt=route=CARD|fee=15\n',
    'DataPatternsDemo': 'sent=2; safe=1\nfailed=timeout\n',
    'SequencedDemo': 'original=[P1, P2, P3]\nreverse=[P3, P2, P1]\nsnapshot=[P1, P2]\nsnapshot mutation=UnsupportedOperationException\n',
    'HttpClientDemo': 'sync=500; async=500; body=bank unavailable\n',
    'VirtualThreadsDemo': 'completed=12; max resource users=2; all virtual=true\n',
}
expected25 = {
    'ScopedValuesDemo': 'outer=A\ninner=B\nrestored=A\nplain child bound=false\nafter bound=false\n',
    'Java25Syntax': 'rejected before base=true\nname=P7; base calls=1\nmonth=SEPTEMBER\n',
    'CompactReceipt': '[P2, P1]\n',
}
with tempfile.TemporaryDirectory(prefix='kb-java-versions-') as work:
    root = Path(work)
    for home, release, expected in [(args.java21_home, '21', expected21), (args.java25_home, '25', expected25)]:
        classes = root / release
        call('compile' + release, home, 'javac', ['--release', release, '-d', classes,
             *[source / (name + '.java') for name in expected]])
        for name, wanted in expected.items():
            got = call(name, home, 'java', ['-cp', classes, name]).stdout
            assert got == wanted, (name, got)
            say(name, got.rstrip('\n'))
    call('source-target8', args.java21_home, 'javac', ['-source', '8', '-target', '8', '-d', root / 'st8', source / 'ApiFloor.java'])
    call('release8-api', args.java21_home, 'javac', ['--release', '8', '-d', root / 'r8', source / 'ApiFloor.java'], False, 'cannot find symbol')
    say('API floor', 'source/target 8: compiled; --release 8: rejected isBlank')
    bad_var = root / 'VarFloor.java'
    bad_var.write_text('public class VarFloor { void test() { var value = 1; } }\n')
    call('release8-syntax', args.java21_home, 'javac', ['--release', '8', str(bad_var)], False, 'cannot find symbol')
    say('syntax floor', '--release 8: rejected var')
    for folder, options, major, ok in [('default25', [], 69, False), ('release21', ['--release', '21'], 65, True)]:
        classes = root / folder
        call(folder + '-compile', args.java25_home, 'javac', [*options, '-d', classes, source / 'RuntimeFloor.java'])
        data = (classes / 'RuntimeFloor.class').read_bytes()
        assert int.from_bytes(data[6:8], 'big') == major
        result = call(folder + '-run21', args.java21_home, 'java', ['-cp', classes, 'RuntimeFloor'], ok,
                      'notifications ready' if ok else 'UnsupportedClassVersionError')
        say(folder, 'class major=' + str(major) + '; Java 21: ' + ('notifications ready' if ok else 'UnsupportedClassVersionError'))
    preview = root / 'preview'
    call('preview-disabled-compile', args.java21_home, 'javac', ['--release', '21', '-d', preview, source / 'Preview21.java'], False, 'preview feature')
    assert not (preview / 'Preview21.class').exists()
    call('preview-enabled-compile', args.java21_home, 'javac', ['--enable-preview', '--release', '21', '-d', preview, source / 'Preview21.java'])
    data = (preview / 'Preview21.class').read_bytes()
    assert (int.from_bytes(data[6:8], 'big'), int.from_bytes(data[4:6], 'big')) == (65, 65535)
    call('preview-no-runtime-flag', args.java21_home, 'java', ['-cp', preview, 'Preview21'], False, 'UnsupportedClassVersionError')
    call('preview-runtime21', args.java21_home, 'java', ['--enable-preview', '-cp', preview, 'Preview21'], True, 'preview ran')
    call('preview-runtime25', args.java25_home, 'java', ['--enable-preview', '-cp', preview, 'Preview21'], False, 'UnsupportedClassVersionError')
    stable25 = root / 'stable25'
    call('stable25-compile', args.java25_home, 'javac', ['--release', '25', '-d', stable25, source / 'Preview21.java'])
    data = (stable25 / 'Preview21.class').read_bytes()
    assert (int.from_bytes(data[6:8], 'big'), int.from_bytes(data[4:6], 'big')) == (69, 0)
    call('stable25-runtime', args.java25_home, 'java', ['-cp', stable25, 'Preview21'], True, 'preview ran')
    say('preview', 'compile without flag: rejected\nclass=65.65535\nJava 21 without flag: rejected\nJava 21 with flag: preview ran\nJava 25 with flag: rejected old preview class\nrecompiled class=69.0; Java 25 without flag: preview ran')
