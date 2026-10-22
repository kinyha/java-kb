#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Run the Value Objects demos on a JDK 28 EA build and the migration demo on JDK 21."""
import os
from pathlib import Path
import re
import subprocess
import tempfile

base = Path(__file__).resolve().parent
jdk28 = os.environ.get('JAVA_KB_JDK28')
if not jdk28:
    raise SystemExit('JAVA_KB_JDK28=/path/to/jdk-28 (EA build from jdk.java.net/28) is required')
jdk21 = Path(os.environ.get('JAVA_KB_JDK', Path.home() / '.sdkman/candidates/java/current')) / 'bin'
java, javac, javap = (str(Path(jdk28) / 'bin' / c) for c in ('java', 'javac', 'javap'))
java21, javac21 = str(jdk21 / 'java'), str(jdk21 / 'javac')
PREVIEW = '--enable-preview'


def run(command, expected=0, stdout_only=False):
    result = subprocess.run(command, text=True, capture_output=True)
    assert (result.returncode == 0) == (expected == 0), result.stdout + result.stderr
    return result.stdout if stdout_only else result.stdout + result.stderr


def check(name, actual, expected):
    assert actual == expected, (name, actual)
    print(name + ': OK')


version = run([java, '-version'])
assert 'version "28' in version, version

src = lambda name: str(base / name)

check('SameCode without preview', run([java, src('SameCode.java')]),
      'userId ==    false\nvalueDate == false\npayment ==   false\n'
      'java.time.LocalDate       1,000,003 шт.   16,000,048 байт\n'
      '[Ljava.time.LocalDate;            1 шт.    4,000,016 байт\n2029-09-20\n')
check('SameCode with preview', run([java, PREVIEW, src('SameCode.java')], stdout_only=True),
      'userId ==    true\nvalueDate == true\npayment ==   false\n'
      '[Ljava.time.LocalDate;            1 шт.    8,000,016 байт\n'
      'java.time.LocalDate               4 шт.           64 байт\n2029-09-20\n')

check('Equality', run([java, PREVIEW, src('Equality.java')], stdout_only=True),
      'a == b        true\na == c        false\na.equals(c)   true\n'
      'iban equals   false\niban hash ==  false\n'
      '0.0 == -0.0   true\nRate(0.0) == Rate(-0.0)  false\n'
      'nan == nan    false\nRate(nan) == Rate(nan)   true\n')

check('Identity', run([java, PREVIEW, src('Identity.java')], stdout_only=True),
      'synchronized: Cannot synchronize on an instance of value class Identity$Money\n'
      'wait:         IllegalMonitorStateException\n'
      'WeakReference: Identity$Money is not an identity class\n'
      'IdentityHashMap: {Money[kopecks=150, currency=RUB]=second}\n'
      'new Integer == new Integer: true\n'
      'hasIdentity(fee)   = false\nhasIdentity("RUB") = true\n'
      'Field.set:    Can not set final long field Identity$Money.kopecks to java.lang.Long\n'
      'serialize:    Identity$Ticket; cannot serialize due to final value class or strictly-initialized instance fields\n')

check('RecordThis without preview', run([java, src('RecordThis.java')]), 'bad Payment[id=null, amount=0]\n')
out = run([java, PREVIEW, src('RecordThis.java')], 1)
assert 'reference to this may only appear after an explicit constructor invocation' in out, out
print('RecordThis with preview: expected compilation error')

check('Construction', run([java, PREVIEW, src('Construction.java')], stdout_only=True), 'создан 150 RUB\n')

with tempfile.TemporaryDirectory(prefix='java-kb-value-') as directory:
    work = Path(directory)
    out = run([javac, '--release', '28', PREVIEW, '-d', str(work), src('negative/EarlyThis.java')], 1)
    assert 'reference to normalize(String) may only appear after an explicit constructor invocation' in out, out
    print('EarlyThis: expected compilation error')

    out = run([javac, '--release', '28', PREVIEW, '-d', str(work), src('negative/Rules.java')], 1)
    for diagnostic in ['The identity type Account cannot be a supertype of the value type Card',
                       'cannot inherit from final Fee', 'cannot assign a value to final variable n']:
        assert diagnostic in out, out
    assert '3 errors' in out, out
    print('Rules: expected 3 compilation errors')

    run([javac, '--release', '28', PREVIEW, '-d', str(work), src('Construction.java'), src('Larval.java')])
    money = run([javap, '-v', '-p', '-cp', str(work), 'Construction$Money'])
    assert 'flags: (0x0010) ACC_FINAL\n' in money, money                      # без ACC_IDENTITY (0x0020)
    assert money.count('ACC_PRIVATE, ACC_FINAL, ACC_STRICT_INIT') == 2, money
    assert money.index('// Field currency') < money.index('// Method java/lang/Object."<init>"'), money
    larval = subprocess.run([javap, '-v', '-p', '-cp', str(work), 'Larval'], text=True, capture_output=True).stdout
    assert 'frame_type = 246 /* early_larval */' in larval and 'unset_field = #NameAndType currency' in larval, larval
    print('javap: ACC_STRICT_INIT, putfield before super(), early_larval frame')

    # Класс собран без preview, а запущен с --enable-preview: Integer уже value-класс
    run([javac, '-d', str(work), src('Prepare.java')])
    out = run([java, PREVIEW, '-cp', str(work), 'Prepare'], 1)
    assert 'IdentityException: Cannot synchronize on an instance of value class java.lang.Integer' in out, out
    print('Prepare on JDK 28 with preview: IdentityException')

    layout = run([java, PREVIEW, '-XX:+UnlockDiagnosticVMOptions', '-XX:+PrintFieldLayout', src('Layout.java')])
    for line in ['@8 FLAT 8/8 "fee" LLayout$Fee;', '@16 FLAT 8/8 "rate" LLayout$Rate;',
                 '@24 FLAT 8/8 "valueDate" Ljava/time/LocalDate;', '@32 REGULAR 4/4 "amount" LLayout$Money;',
                 '@8 FLAT 16/4 "at" Ljava/time/LocalDateTime;', '@24 FLAT 9/8 "amount" LLayout$Money;']:
        assert line in layout, line
    money_block = layout[layout.index('Layout of class Layout$Money'):]
    assert 'Instance size = 24 bytes' in money_block.split('---')[0], money_block
    print('Layout: OK')

    order = run([java, PREVIEW, '-XX:+UnlockDiagnosticVMOptions', '-XX:+PrintFieldLayout', src('OrderLayout.java')])
    for line in ['@8 FLAT 8/8 "quantity" Ljava/lang/Integer;', '@16 FLAT 8/8 "deliveryDate" Ljava/time/LocalDate;',
                 '@24 REGULAR 4/4 "total" Ljava/lang/Long;', '@28 REGULAR 4/4 "createdAt" Ljava/time/LocalDateTime;',
                 '@32 REGULAR 4/4 "due" Ljava/lang/Comparable;']:
        assert line in order, line
    print('OrderLayout: OK')

    out = run([javac, '-d', str(work), src('negative/SyncDate.java')])
    assert '[identity] attempt to synchronize on an instance of a value-based class' in out, out
    out = run([javac, '--release', '28', PREVIEW, '-d', str(work), src('negative/SyncDate.java')], 1)
    assert 'required: identity class' in out and 'found:    LocalDate' in out, out
    print('SyncDate: warning without preview, error with preview')

check('Arrays', run([java, PREVIEW, src('Arrays.java')], stdout_only=True),
      '[Ljava.lang.Integer;              1 шт.    8,000,016 байт\n'
      '[Ljava.lang.Long;                 2 шт.    4,001,056 байт\n'
      '[Ljava.time.LocalDate;            1 шт.    8,000,016 байт\n'
      'java.lang.Integer         1,000,280 шт.   16,004,480 байт\n'
      'java.lang.Long            1,000,256 шт.   16,004,096 байт\n5041\n')

# Аллокации зависят от момента JIT-компиляции: проверяем только прогретый последний раунд
for flags, identity in [([], '15,625'), (['-XX:CompileCommand=quiet', '-XX:CompileCommand=dontinline,*::plus'], '31,250')]:
    out = run([java, PREVIEW] + flags + [src('Scalarization.java')], stdout_only=True)
    last = out.splitlines()[3]
    assert re.fullmatch(r'раунд 4: identity ' + identity + r' КБ, value +0 КБ', last), out
    print('Scalarization', ' '.join(flags) or 'default', ':', last)

check('Predict', run([java, PREVIEW, src('Predict.java')], stdout_only=True), 'true\nfalse\ntrue\n')
check('JdkClasses', run([java, PREVIEW, src('JdkClasses.java')], stdout_only=True),
      ''.join('%-14s %s\n' % pair for pair in [
          ('Integer', 'value'), ('Long', 'value'), ('Double', 'value'), ('Boolean', 'value'), ('Optional', 'value'),
          ('LocalDate', 'value'), ('Instant', 'value'), ('Duration', 'value'), ('String', 'identity'),
          ('BigDecimal', 'identity'), ('UUID', 'identity'), ('ZoneOffset', 'identity'), ('List12', 'identity')]))

out = run([java, PREVIEW, '-XX:+UnlockDiagnosticVMOptions', '-XX:-ValueTypePassFieldsAsArgs', '-XX:CompileCommand=quiet',
           '-XX:CompileCommand=dontinline,*::plus', src('Scalarization.java')], stdout_only=True)
assert out.splitlines()[3] == 'раунд 4: identity 31,250 КБ, value 46,875 КБ', out
print('Scalarization -ValueTypePassFieldsAsArgs: value objects are buffered again')

out = run([java21, '-XX:+UnlockDiagnosticVMOptions', '-XX:DiagnoseSyncOnValueBasedClasses=2', src('Prepare.java')])
assert 'Synchronizing on object' in out and 'of klass java.lang.Integer' in out, out
print('Prepare on JDK 21: valuebasedclasses log')
