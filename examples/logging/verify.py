#!/usr/bin/env python3
"""Compile the demos in a temporary directory; preserve real logs outside the repo."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", type=Path, default=Path.home() / ".sdkman/candidates/java/current")
parser.add_argument("--maven", default=str(Path.home() / ".sdkman/candidates/maven/current/bin/mvn"))
parser.add_argument("--logs", type=Path, default=Path(tempfile.mkdtemp(prefix="kb-logging-logs-")))
args = parser.parse_args()
args.logs.mkdir(parents=True, exist_ok=True)
source = Path(__file__).resolve().parent
env = dict(os.environ, JAVA_HOME=str(args.java_home))
java = str(args.java_home / "bin/java")
javac = str(args.java_home / "bin/javac")

with tempfile.TemporaryDirectory(prefix="kb-logging-") as directory:
    work = Path(directory)
    shutil.copy(source / "pom.xml", work / "pom.xml")
    dependencies = subprocess.run([
        args.maven, "-B", "-ntp", "-f", str(work / "pom.xml"),
        "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath",
        "-Dmdep.outputFile=" + str(work / "classpath.txt"), "-DincludeScope=runtime",
    ], env=env, text=True, capture_output=True)
    (args.logs / "dependencies.log").write_text(dependencies.stdout + dependencies.stderr)
    dependencies.check_returncode()
    cp = (work / "classpath.txt").read_text().strip()
    subprocess.run([javac, "--release", "21", "-cp", cp, "-d", str(work)] +
                   [str(p) for p in sorted(source.glob("*.java"))], check=True)
    runtime_cp = os.pathsep.join([str(work), cp])

    def run(name, classpath=runtime_cp, flags=()):
        result = subprocess.run([java, *flags, "-cp", classpath, name], text=True, capture_output=True, timeout=25)
        (args.logs / (name + ".out")).write_text(result.stdout)
        (args.logs / (name + ".err")).write_text(result.stderr)
        result.check_returncode()
        print(name + ":")
        print(result.stdout, end="")
        return result

    api = next(p for p in cp.split(os.pathsep) if Path(p).name == "slf4j-api-2.0.17.jar")
    nop = run("ProviderDemo", os.pathsep.join([str(work), api]))
    assert nop.stdout == "factory=NOPLoggerFactory\n"
    assert "No SLF4J providers were found" in nop.stderr
    assert "payment accepted" not in nop.stdout + nop.stderr
    for name in ("RoutesDemo", "LazyDemo", "MdcDemo", "AsyncDemo"):
        run(name)
    encoded = run("JsonDemo", flags=("-Dlogback.configurationFile=" + str(source / "logback-json.xml"),))
    assert not encoded.stderr, encoded.stderr
    assert len(encoded.stdout.splitlines()) == 1, "newline in input must not create another log record"
    event = json.loads(encoded.stdout)
    assert event["level"] == "INFO"
    assert event["mdc"] == {"request_id": "R7"}
    assert event["kvpList"] == [{"payment_id": "P7"}, {"attempt": "2"}]
    assert event["message"] == "bank reply {}"
    assert event["arguments"] == ['ok\n"level":"ERROR"']
    assert event["formattedMessage"] == 'bank reply ok\n"level":"ERROR"'
    assert event["throwable"] is None
    print("JSON parser: one record, INFO, escaped input, numeric KVP is a string: OK")
    print("Logs:", args.logs)
