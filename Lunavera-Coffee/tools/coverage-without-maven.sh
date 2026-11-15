#!/usr/bin/env bash
# TODO 6.59: a JaCoCo line-coverage table per package without Maven (JDK, JUnit and the JaCoCo jars of the Debian/Ubuntu
# package libjacoco-java, or any JaCoCo 0.8.x agent + cli).  NOT RUN where it was written (that session had no javac): check it once.
#
#   tools/coverage-without-maven.sh
#   JACOCO_AGENT=/path/org.jacoco.agent-runtime.jar JACOCO_CLI=/path/org.jacoco.cli-nodeps.jar tools/coverage-without-maven.sh
#
# It builds with tools/run-library-free-tests.sh (LFT_BUILD_ONLY), runs the same test classes under the JaCoCo agent, writes
# target/coverage/jacoco.exec, report.csv and a per-package table (target/coverage/packages.txt, also printed).
# It measures only what that build runs: classes and tests that need JavaFX or a database driver are left out, so the numbers are
# lower than a full ./mvnw verify for the packages that use them. The figures kept in the 6.59 note come from a full-suite run.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
find_jar() { find "${HOME}/.m2" /usr/share/java /opt -name "$1" 2>/dev/null | head -1; }
JUNIT_JAR="${JUNIT_JAR:-$(find_jar 'junit-4*.jar')}"
HAMCREST_JAR="${HAMCREST_JAR:-$(find_jar 'hamcrest-core*.jar')}"
JACOCO_AGENT="${JACOCO_AGENT:-$(find_jar '*jacoco*agent*runtime*.jar')}"
[ -z "$JACOCO_AGENT" ] && JACOCO_AGENT="$(find_jar '*jacocoagent*.jar')"
JACOCO_CLI="${JACOCO_CLI:-$(find_jar '*jacoco*cli*nodeps*.jar')}"
[ -z "$JACOCO_CLI" ] && JACOCO_CLI="$(find_jar '*jacoco*cli*.jar')"
[ -z "$JUNIT_JAR" ] && { echo "JUnit 4 jar not found: set JUNIT_JAR"; exit 2; }
[ -z "$JACOCO_AGENT" ] && { echo "JaCoCo agent jar not found: set JACOCO_AGENT (the runtime/agent jar)"; exit 2; }
[ -z "$JACOCO_CLI" ] && { echo "JaCoCo cli jar not found: set JACOCO_CLI (the nodeps cli jar)"; exit 2; }
export JUNIT_JAR HAMCREST_JAR
OUT="$ROOT/target/coverage"; WORK="$OUT/build"
rm -rf "$OUT"; mkdir -p "$OUT"
LFT_WORK="$WORK" LFT_BUILD_ONLY=1 tools/run-library-free-tests.sh || { echo "The build failed."; exit 2; }
CP="$JUNIT_JAR:$HAMCREST_JAR"
echo "Running the tests under the JaCoCo agent..."
java -Xss16m -Djava.awt.headless=true \
  -javaagent:"$JACOCO_AGENT"=destfile="$OUT/jacoco.exec",includes='com.coffeeshop.*' \
  -cp "$WORK/runner:$WORK/classes:$WORK/run:$CP" LibraryFreeRunner "$WORK/classes.txt" 2>&1 | grep -v "^Picked up" | tail -5
[ -s "$OUT/jacoco.exec" ] || { echo "No coverage data was written."; exit 2; }
# only the program's own classes (not the test classes that sit in the same folder)
mkdir -p "$OUT/main-classes"
( cd "$WORK/main" && find . -name '*.java' | sed -e 's|^\./||' -e 's|\.java$||' ) | while read -r c; do
  for f in "$WORK/classes/$c.class" "$WORK/classes/$c"\$*.class; do
    [ -f "$f" ] && { mkdir -p "$OUT/main-classes/$(dirname "${f#$WORK/classes/}")"; cp "$f" "$OUT/main-classes/${f#$WORK/classes/}"; }
  done
done
java -jar "$JACOCO_CLI" report "$OUT/jacoco.exec" --classfiles "$OUT/main-classes" --sourcefiles src/main/java \
  --csv "$OUT/report.csv" --html "$OUT/html" >/dev/null || { echo "The report step failed."; exit 2; }
python3 - "$OUT/report.csv" > "$OUT/packages.txt" <<'PY'
import csv, sys, collections
missed, covered = collections.Counter(), collections.Counter()
for row in csv.DictReader(open(sys.argv[1], encoding="utf8")):
    pkg = row["PACKAGE"].split(".")[-1]
    missed[pkg] += int(row["LINE_MISSED"]); covered[pkg] += int(row["LINE_COVERED"])
tm, tc = sum(missed.values()), sum(covered.values())
print("Line coverage per package (JaCoCo, tools/coverage-without-maven.sh)")
for pkg in sorted(missed):
    t = missed[pkg] + covered[pkg]
    print(f"  {pkg:<12} {100.0 * covered[pkg] / t if t else 0:5.1f}%   ({covered[pkg]} of {t} lines)")
print(f"  {'all':<12} {100.0 * tc / (tm + tc) if tm + tc else 0:5.1f}%   ({tc} of {tm + tc} lines)")
PY
cat "$OUT/packages.txt"
echo "HTML report: $OUT/html/index.html"
