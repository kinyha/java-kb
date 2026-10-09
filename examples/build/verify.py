"""Run the real Maven/Gradle builds in an isolated copy. Keep logs with --work-dir."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

SOURCE = Path(__file__).resolve().parent
JAVA_HOME = Path(os.environ.get("JAVA_HOME", Path.home() / ".sdkman/candidates/java/current"))
JAVA = str(JAVA_HOME / "bin/java")
JAVAC = str(JAVA_HOME / "bin/javac")
JAR = str(JAVA_HOME / "bin/jar")
ENV = dict(os.environ, JAVA_HOME=str(JAVA_HOME))
MAVEN = os.environ.get("MAVEN_CMD", "mvn")
GRADLE = os.environ.get("GRADLE_CMD", "gradle")


def main(work):
    project = work / "project"
    shutil.copytree(SOURCE, project, ignore=shutil.ignore_patterns("target", "build", ".gradle", "fixture-repo", "__pycache__"))
    logs = work / "logs"
    logs.mkdir()

    def run(name, cmd, cwd=project, success=True):
        result = subprocess.run(cmd, cwd=cwd, env=ENV, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (logs / (name + ".log")).write_text(result.stdout)
        assert (result.returncode == 0) == success, (name, result.returncode, result.stdout[-6000:])
        return result.stdout

    java_version = run("java-version", [JAVA, "-version"])
    assert 'version "21.' in java_version, java_version
    run("maven-version", [MAVEN, "-version"])
    run("gradle-version", [GRADLE, "--version"])
    repo = project / "fixture-repo"
    artifacts = {}
    for source, artifact, version, dependency in [
        ("v1", "money-api", "1.0", None), ("v2", "money-api", "2.0", None),
        ("legacy", "legacy-adapter", "1.0", "1.0"), ("new", "new-adapter", "1.0", "2.0"),
    ]:
        classes = work / ("classes-" + source)
        classes.mkdir()
        args = [JAVAC, "--release", "21", "-d", str(classes)]
        if dependency:
            args += ["-cp", str(artifacts["money-api", dependency])]
        args += [str(p) for p in (project / "fixtures" / source).rglob("*.java")]
        run("compile-" + source, args)
        folder = repo / "demo/fixture" / artifact / version
        folder.mkdir(parents=True)
        jar = folder / f"{artifact}-{version}.jar"
        run("jar-" + source, [JAR, "--create", "--file", str(jar), "-C", str(classes), "."])
        dep = "" if not dependency else f"""<dependencies><dependency>
          <groupId>demo.fixture</groupId><artifactId>money-api</artifactId>
          <version>{dependency}</version></dependency></dependencies>"""
        (folder / f"{artifact}-{version}.pom").write_text(f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion><groupId>demo.fixture</groupId>
          <artifactId>{artifact}</artifactId><version>{version}</version>{dep}</project>""")
        for artifact_file in folder.iterdir():
            artifact_file.with_suffix(artifact_file.suffix + ".sha1").write_text(hashlib.sha1(artifact_file.read_bytes()).hexdigest())
        artifacts[artifact, version] = jar

    mvn = [MAVEN, "-B", "-ntp", f"-Dmaven.repo.local={work / 'm2'}", "-Dstyle.color=never"]
    build = run("maven-build", mvn + ["-pl", "app", "-am", "verify"])
    assert build.index("Building domain") < build.index("Building app"), build
    report = ET.parse(project / "domain/target/surefire-reports/TEST-demo.FeeTest.xml").getroot()
    assert report.attrib["tests"] == "2" and report.attrib["failures"] == "0"
    tree_without_domain = run("maven-tree", mvn + ["-pl", "app", "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree", "-Dverbose", "-Dincludes=demo.fixture:*"])
    assert "POM for demo.build:domain:jar:1.0 is missing" in tree_without_domain
    # tree can print a partial graph; a build needs the actual domain jar.
    missing = run("maven-app-alone", mvn + ["-o", "-pl", "app", "verify"], success=False)
    assert "demo.build:domain:jar:1.0" in missing
    run("maven-install", mvn + ["install"])
    tree = run("maven-tree-installed", mvn + ["-pl", "app", "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree", "-Dverbose", "-Dincludes=demo.fixture:*"])
    assert "omitted for conflict with 1.0" in tree, tree
    cp_goal = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath"

    def maven_start(name, options=(), expected=True):
        run(name + "-cp", mvn + ["-pl", "app", cp_goal, "-DincludeScope=runtime", "-Dmdep.outputFile=target/runtime.cp"] + list(options))
        cp = str(project / "app/target/app-1.0.jar") + os.pathsep + (project / "app/target/runtime.cp").read_text().strip()
        return run(name, [JAVA, "-cp", cp, "demo.App"], success=expected)

    broken = maven_start("maven-default", expected=False)
    assert "NoSuchMethodError" in broken and "MoneyApi.minorUnits()" in broken
    aligned = maven_start("maven-aligned", ["-Palign"])
    reversed_order = maven_start("maven-reversed", ["-Dfirst.adapter=new-adapter", "-Dsecond.adapter=legacy-adapter"])
    assert aligned == reversed_order == "fee=3\nlegacy=EUR\nnew=2\n"
    jar = project / "app/target/app-1.0.jar"
    with zipfile.ZipFile(jar) as archive:
        assert "demo/App.class" in archive.namelist()
        assert "demo/Fee.class" not in archive.namelist()
    thin = run("thin-jar", [JAVA, "-cp", str(jar), "demo.App"], success=False)
    assert "NoClassDefFoundError: demo/Fee" in thin
    run("maven-compiler-goal-only", mvn + ["-pl", "domain", "clean", "org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile"])
    assert (project / "domain/target/classes/demo/Fee.class").exists()
    assert not (project / "domain/target/surefire-reports").exists()
    assert not (project / "domain/target/domain-1.0.jar").exists()

    gradle = [GRADLE, "--console=plain", "--no-configuration-cache", "--no-build-cache"]
    run("gradle-build", gradle + ["build"])
    selected = run("gradle-app-dry-run", gradle + [":app:build", "--dry-run"])
    assert ":domain:jar" in selected and ":domain:test " not in selected
    with zipfile.ZipFile(project / "app/build/libs/app-1.0.jar") as archive:
        assert "demo/Fee.class" not in archive.namelist()
    run("gradle-insight", gradle + [":app:dependencyInsight", "--dependency", "money-api", "--configuration", "runtimeClasspath"])
    normal = run("gradle-default", gradle + ["-q", ":app:run"])
    ordinary = run("gradle-ordinary", gradle + ["-q", ":app:run", "-Pordinary"])
    assert normal == ordinary == aligned
    strict = run("gradle-strict", gradle + [":app:run", "-Pstrict"], success=False)
    assert "NoSuchMethodError" in strict
    repeated = run("gradle-second-build", gradle + ["build"])
    assert "> Task :app:compileJava UP-TO-DATE" in repeated
    assert "> Task :domain:test UP-TO-DATE" in repeated
    ship = run("gradle-ship", gradle + ["-q", "ship"], cwd=project / "lifecycle")
    audit = run("gradle-audit", gradle + ["-q", "audit"], cwd=project / "lifecycle")
    assert ship == "INIT settings\nCONFIG build\nEXEC prepare\nEXEC ship\n", ship
    assert audit == "INIT settings\nCONFIG build\nEXEC audit\n", audit
    hidden = run("gradle-implementation", gradle + ["-q", ":client:paths"], cwd=project / "visibility")
    exposed = run("gradle-api", gradle + ["-q", ":client:paths", "-Pexpose"], cwd=project / "visibility")
    assert hidden == "compile money-api=false\nruntime money-api=true\n", hidden
    assert exposed == "compile money-api=true\nruntime money-api=true\n", exposed
    # --release protects the JDK API surface; -source/-target alone don't.
    release_source = work / "ReleaseProbe.java"
    release_source.write_text('import java.util.List; class ReleaseProbe { Object first() { return List.of("EUR").getFirst(); } }')
    run("source-target-17", [JAVAC, "-source", "17", "-target", "17", str(release_source)])
    failed = run("release-17", [JAVAC, "--release", "17", str(release_source)], success=False)
    assert "cannot find symbol" in failed and "getFirst" in failed
    print("Maven default: money-api 1.0 → NoSuchMethodError")
    print("Maven reversed / aligned: money-api 2.0 → new=2")
    print("Thin jar: NoClassDefFoundError (demo/Fee)")
    print("Maven reactor: payments → domain → app; JUnit: 2 passed")
    print("compiler:compile alone: no tests, no jar")
    print("Gradle default / ordinary constraint 1.0: money-api 2.0 → new=2")
    print("Gradle strict 1.0: NoSuchMethodError")
    print("Gradle second build: compileJava / domain:test UP-TO-DATE")
    print("Gradle :app:build: domain:jar required, domain:test absent")
    print("implementation → client compile=false/runtime=true; api → true/true")
    print("dependsOn: prepare runs; mustRunAfter: prepare absent")
    print("JDK 21 -source/-target 17: compiled; --release 17: rejected getFirst()")
    print("Logs:", logs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, help="New directory to preserve builds and logs")
    args = parser.parse_args()
    if args.work_dir:
        args.work_dir.mkdir(parents=True, exist_ok=False)
        main(args.work_dir.resolve())
    else:
        with tempfile.TemporaryDirectory(prefix="java-kb-build-") as folder:
            main(Path(folder))
