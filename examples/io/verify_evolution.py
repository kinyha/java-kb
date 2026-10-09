"""Run writer and readers with separate classpaths. No artifacts left in the repo."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent / 'evolution'
jdk = Path(os.environ.get('JAVA_HOME', Path.home() / '.sdkman/candidates/java/current')) / 'bin'
java = str(jdk / 'java') if (jdk / 'java').exists() else shutil.which('java')
javac = str(jdk / 'javac') if (jdk / 'javac').exists() else shutil.which('javac')

with tempfile.TemporaryDirectory(prefix='java-kb-io-evolution-') as tmp:
    temp = Path(tmp)
    for version, main in [('v1', 'Write'), ('v2', 'Read'), ('incompatible', 'Read')]:
        output = temp / version
        output.mkdir()
        subprocess.run([javac, '--release', '21', '-d', str(output),
                        str(root / version / 'Snapshot.java'), str(root / (main + '.java'))], check=True)
    data = temp / 'snapshot.bin'
    expected = [
        ('v1', 'Write', 'writer: UID=1, count=7'),
        ('v2', 'Read', 'reader: count=7, label=null'),
        ('incompatible', 'Read', 'reader: UID mismatch -> InvalidClassException'),
    ]
    for version, main, text in expected:
        result = subprocess.run([java, '-cp', str(temp / version), main, str(data)], check=True,
                                capture_output=True, text=True)
        print(result.stdout, end='')
        assert result.stdout.strip() == text, result.stdout
