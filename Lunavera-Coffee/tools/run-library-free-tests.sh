#!/usr/bin/env bash
# TODO 6.73: run the tests that need no JavaFX and no database driver, with nothing but the JDK and the JUnit jars.
# It does not replace ./mvnw test. It stops the cheapest failures from reaching the owner. Run it at the end of every session.
#
#   tools/run-library-free-tests.sh            (from the project folder)
#   JUNIT_JAR=/path/junit-4.13.2.jar HAMCREST_JAR=/path/hamcrest-core-1.3.jar tools/run-library-free-tests.sh
#
# What it does: copies the sources without module-info.java, puts the stand-ins of tools/standins over them (a few lines for
# org.sqlite and AppStyle), compiles the main sources and then the tests, leaving out every file that does not compile without the
# libraries (it prints the count), and runs the rest. A failure that is only "driver or JavaFX missing" is counted as not run; any
# other failure is real and makes the exit code 1.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
find_jar() { find "${HOME}/.m2" /usr/share/java /opt -name "$1" 2>/dev/null | head -1; }
JUNIT_JAR="${JUNIT_JAR:-$(find_jar 'junit-4*.jar')}"
HAMCREST_JAR="${HAMCREST_JAR:-$(find_jar 'hamcrest-core*.jar')}"
[ -z "$JUNIT_JAR" ] && { echo "JUnit 4 jar not found: set JUNIT_JAR (run ./mvnw test-compile once to fill ~/.m2)"; exit 2; }
CP="$JUNIT_JAR:$HAMCREST_JAR"
# LFT_WORK=<folder> keeps the build there (and LFT_BUILD_ONLY=1 stops before the tests run): tools/mutation-sweep.py uses it (TODO 6.78).
if [ -n "${LFT_WORK:-}" ]; then WORK="$LFT_WORK"; mkdir -p "$WORK"; else WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT; fi
mkdir -p "$WORK/main" "$WORK/test" "$WORK/classes"
cp -r src/main/java/. "$WORK/main/"; rm -f "$WORK/main/module-info.java"
cp -r tools/standins/. "$WORK/main/"
cp -r src/test/java/. "$WORK/test/"
JAVAC=(javac -encoding UTF-8 -proc:none -nowarn -Xmaxerrs 100000 -d "$WORK/classes")

# compile a folder, dropping every file that has an error, until what is left compiles
compile_without_failures() { # folder extra-classpath
  local dir="$1" cp="$2" round
  for round in $(seq 1 15); do
    find "$dir" -name '*.java' > "$WORK/files.txt"
    if "${JAVAC[@]}" -cp "$cp" @"$WORK/files.txt" 2> "$WORK/err.txt"; then return 0; fi
    grep -oE "^[^ ]+\.java:[0-9]+: error" "$WORK/err.txt" | sed -E 's/:[0-9]+: error$//' | sort -u > "$WORK/bad.txt"
    [ -s "$WORK/bad.txt" ] || { cat "$WORK/err.txt"; return 1; }
    xargs rm -f < "$WORK/bad.txt"
    echo "  round $round: left out $(wc -l < "$WORK/bad.txt") files that need the libraries"
  done
  return 1
}
echo "Compiling the main sources..."
compile_without_failures "$WORK/main" "$CP" || { echo "Could not compile the main sources."; exit 2; }
echo "Compiling the tests..."
compile_without_failures "$WORK/test" "$WORK/classes:$CP" || { echo "Could not compile the tests."; exit 2; }
( cd "$WORK/test" && find . -name '*Test.java' | sed -e 's|^\./||' -e 's|\.java$||' -e 's|/|.|g' | sort ) > "$WORK/classes.txt"
mkdir -p "$WORK/run"
cp -r src/main/resources/. "$WORK/run/"
RUNNER_DIR="$WORK/runner"; mkdir -p "$RUNNER_DIR"
javac -encoding UTF-8 -proc:none -d "$RUNNER_DIR" -cp "$CP" tools/LibraryFreeRunner.java || exit 2
[ -n "${LFT_BUILD_ONLY:-}" ] && { echo "Built in $WORK"; exit 0; }
echo "Running $(wc -l < "$WORK/classes.txt") test classes..."
java -Xss16m -Djava.awt.headless=true -cp "$RUNNER_DIR:$WORK/classes:$WORK/run:$CP" LibraryFreeRunner "$WORK/classes.txt" 2>&1 | grep -v "^Picked up"
exit "${PIPESTATUS[0]}"
