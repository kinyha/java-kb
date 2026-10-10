#!/usr/bin/env python3
"""Run the stack in a fresh copy; deliberately fail only isolated mutation builds."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", type=Path, default=Path.home() / ".sdkman/candidates/java/current")
parser.add_argument("--maven", default=str(Path.home() / ".sdkman/candidates/maven/current/bin/mvn"))
parser.add_argument("--work-dir", type=Path)
args = parser.parse_args()
work = args.work_dir or Path(tempfile.mkdtemp(prefix="kb-java-testing-")) / "run"
if work.exists():
    raise SystemExit("work-dir must not exist: " + str(work))
source = Path(__file__).resolve().parent
shutil.copytree(source, work)
logs = work / "logs"
logs.mkdir()
env = dict(os.environ, JAVA_HOME=str(args.java_home))
java = str(args.java_home / "bin/java")

def maven(name, directory, goals, success=True):
    result = subprocess.run([args.maven, "-B", "-ntp", *goals], cwd=directory, env=env,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (logs / (name + ".log")).write_text(result.stdout)
    assert (result.returncode == 0) == success, (name, result.returncode, result.stdout[-4000:])
    for line in result.stdout.splitlines():
        if "Tests run:" in line or "BUILD " in line or line.startswith(("lifecycle:", "beforeEach", "body ", "afterEach", "PER_CLASS", "postgres:")):
            print(line, flush=True)
    return result.stdout

def reports(path):
    roots = [ET.parse(p).getroot() for p in path.glob("TEST-*.xml")]
    return roots, sum(int(r.get("tests", "0")) for r in roots)

unit = maven("unit", work, ["test"])
roots, count = reports(work / "target/surefire-reports")
assert count == 27, count
assert not (work / "target/failsafe-reports").exists(), "mvn test must not execute IT"
assert all(r.get("failures") == "0" and r.get("errors") == "0" and r.get("skipped") == "0" for r in roots)

maven("verify", work, ["verify"])
roots, count = reports(work / "target/failsafe-reports")
assert count == 3 and all(r.get("failures") == "0" and r.get("errors") == "0" and r.get("skipped") == "0" for r in roots)

maven("classpath", work, ["org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath",
                         "-DincludeScope=test", "-Dmdep.outputFile=" + str(logs / "classpath.txt")])
cp = (logs / "classpath.txt").read_text().strip().split(os.pathsep)
# API and launcher exist, engine deliberately removed. No external bank/Docker is involved.
no_engine = [p for p in cp if not Path(p).name.startswith("junit-jupiter-engine-")]
result = subprocess.run([java, "-cp", os.pathsep.join([str(work / "target/test-classes"), str(work / "target/classes"), *no_engine]),
                         "kb.javatesting.DiscoveryProbe"], capture_output=True, text=True, check=True)
(logs / "no-engine.out").write_text(result.stdout)
assert "at least one TestEngine" in result.stdout, result.stdout
print(result.stdout, end="")

mutant = work / "mutant"
shutil.copytree(source, mutant)
fee = mutant / "src/main/java/kb/javatesting/FeePolicy.java"
text = fee.read_text()
assert text.count("amount / 50 + (amount % 50 == 0 ? 0 : 1)") == 1
fee.write_text(text.replace("amount / 50 + (amount % 50 == 0 ? 0 : 1)", "amount / 50"))
maven("mutant-weak", mutant, ["test", "-Dtest=FeePolicyTest#weakOracleAcceptsTheRoundingBug"])
maven("mutant-strong", mutant, ["test", "-Dtest=FeePolicyTest#roundsUpWithoutOverflow"], success=False)
roots, count = reports(mutant / "target/surefire-reports")
assert count == 7
assert sum(int(r.get("failures", "0")) for r in roots) == 5
print("Mutation: weak test passed; five of seven exact boundary cases failed")
print("Verified logs and XML reports:", work)
