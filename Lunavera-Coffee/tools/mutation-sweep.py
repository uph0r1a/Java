#!/usr/bin/env python3
"""TODO 6.78: a small mutation tester for the rule classes, with nothing but the JDK and JUnit (PIT needs Maven; see pom.xml profile "pit").

It changes ONE thing at a time in a class (> to >=, == to !=, && to ||, true to false, + to -, a number by one), compiles that one file
over the build of tools/run-library-free-tests.sh and runs the tests that mention the class. A change no test notices is a SURVIVOR.
Many survivors are harmless (the change gives the same behaviour); read each one. Tests that need a database or JavaFX are not run
here, so a survivor may still be caught by them on the owner's computer: check before writing a new test.

  export JUNIT_JAR=... HAMCREST_JAR=...
  LFT_WORK=/tmp/lunavera-build LFT_BUILD_ONLY=1 tools/run-library-free-tests.sh      # once
  python3 tools/mutation-sweep.py /tmp/lunavera-build service/RefundMath util/MoneyParser   # classes under src/main/java/.../
"""
import os, re, subprocess, sys, tempfile, shutil
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = "com/coffeeshop/coffeeshopmanagement/"
SRC = os.path.join(ROOT, "src/main/java", PKG)
TESTS = os.path.join(ROOT, "src/test/java")

# (regex for the place, replacement). Applied inside code only: comments, strings and annotations are blanked before searching.
OPERATORS = [
    (r"(?<![<>=!])>=(?!=)", ">"), (r"(?<![<>=!-])>(?![>=])", ">="),
    (r"(?<![<>=!])<=(?!=)", "<"), (r"(?<![<>=!])<(?![<=])", "<="),
    (r"==", "!="), (r"!=", "=="), (r"&&", "||"), (r"\|\|", "&&"),
    (r"\btrue\b", "false"), (r"\bfalse\b", "true"),
    (r"(?<=[\w)\]] )\+(?= [\w(])", "-"), (r"(?<=[\w)\]] )-(?= [\w(])", "+"),
    (r"(?<=[\w)\]] )\*(?= [\w(])", "/"),
    (r"(?<![\w.\"])(\d+)L?(?![\w.\"])", None),   # numbers: n -> n + 1 (0 -> 1)
]

def blank_non_code(line):
    out, i, in_str = [], 0, False
    while i < len(line):
        c = line[i]
        if in_str:
            if c == "\\": out.append("  "); i += 2; continue
            if c == '"': in_str = False; out.append('"'); i += 1; continue
            out.append(" "); i += 1; continue
        if c == '"': in_str = True; out.append('"'); i += 1; continue
        if line.startswith("//", i): out.append(" " * (len(line) - i)); break
        out.append(c); i += 1
    return "".join(out)

def mutants(path):
    lines = open(path, encoding="utf8").read().split("\n")
    in_block = False
    for n, raw in enumerate(lines):
        stripped = raw.strip()
        if in_block:
            if "*/" in raw: in_block = False
            continue
        if stripped.startswith("/*"):
            if "*/" not in raw: in_block = True
            continue
        if not stripped or stripped.startswith(("*", "//", "import", "package", "@")) or "static final" in raw and "(" not in raw:
            continue
        code = blank_non_code(raw)
        for pattern, repl in OPERATORS:
            for m in re.finditer(pattern, code):
                if repl is None:
                    value = int(m.group(1)); new = str(value + 1) + ("L" if m.group(0).endswith("L") else "")
                else:
                    new = repl
                yield n + 1, raw[:m.start()] + new + raw[m.end():], raw.strip(), m.group(0), new

def tests_for(work, simple):
    out = []
    for name in open(os.path.join(work, "classes.txt")).read().split():
        f = os.path.join(TESTS, name.replace(".", "/") + ".java")
        if os.path.exists(f) and re.search(r"\b" + simple + r"\b", open(f, encoding="utf8").read()):
            out.append(name)
    return out

def run_one(work, rel, lines, n, new_line, tests, jars):
    tmp = tempfile.mkdtemp(prefix="mut")
    try:
        src = os.path.join(tmp, "src", PKG + rel + ".java"); os.makedirs(os.path.dirname(src))
        body = list(lines); body[n - 1] = new_line
        open(src, "w", encoding="utf8").write("\n".join(body))
        out = os.path.join(tmp, "out"); os.makedirs(out)
        cp = f"{work}/classes:{jars}"
        if subprocess.run(["javac", "-encoding", "UTF-8", "-proc:none", "-nowarn", "-d", out, "-cp", cp, src],
                          capture_output=True).returncode != 0:
            return "invalid"
        listing = os.path.join(tmp, "t.txt"); open(listing, "w").write("\n".join(tests))
        try:
            r = subprocess.run(["java", "-Xss16m", "-Djava.awt.headless=true", "-cp",
                                f"{out}:{work}/runner:{work}/classes:{work}/run:{jars}", "LibraryFreeRunner", listing],
                               capture_output=True, timeout=90)
        except subprocess.TimeoutExpired:
            return "killed"
        return "killed" if r.returncode != 0 else "SURVIVED"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

def main():
    work, targets = sys.argv[1], sys.argv[2:]
    jars = os.environ["JUNIT_JAR"] + ":" + os.environ["HAMCREST_JAR"]
    for rel in targets:
        path = SRC + rel + ".java"; simple = rel.split("/")[-1]
        lines = open(path, encoding="utf8").read().split("\n")
        tests = tests_for(work, simple)
        todo = list(mutants(path))
        print(f"\n== {rel}: {len(todo)} changes, {len(tests)} test classes", flush=True)
        with ThreadPoolExecutor(max_workers=int(os.environ.get("MUT_JOBS", "2"))) as pool:
            results = list(pool.map(lambda m: run_one(work, rel, lines, m[0], m[1], tests, jars), todo))
        killed = results.count("killed"); survived = [(m, r) for m, r in zip(todo, results) if r == "SURVIVED"]
        valid = len(todo) - results.count("invalid")
        print(f"   killed {killed} of {valid} that compile; {len(survived)} survived", flush=True)
        for (n, _, text, old, new), _r in survived:
            print(f"   SURVIVOR {simple}:{n}  '{old}' -> '{new}'   in: {text[:110]}", flush=True)

if __name__ == "__main__":
    main()
