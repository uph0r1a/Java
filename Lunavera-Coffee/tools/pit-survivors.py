#!/usr/bin/env python3
"""Read a PIT mutations.csv and list what is left to look at (TODO 6.78).

    python3 tools/pit-survivors.py target/pit-reports-db/mutations.csv            # all classes
    python3 tools/pit-survivors.py target/pit-reports-db/mutations.csv AccountService BackupService

Prints, per class, how many changes were killed, survived or reached by no test, then every SURVIVED and NO_COVERAGE line as
`line  method  what PIT changed`, sorted by line, so each can be read next to the source and sorted into "needs a test" or
"equivalent" (docs/TESTING-MUTATION.md). Uses only the standard library.
"""
import collections
import csv
import sys

# PIT's CSV has no header: file, class, mutator, method, line, status, killing test
KINDS = {
    "ConditionalsBoundaryMutator": "boundary (< <= > >=)",
    "NegateConditionalsMutator": "condition negated",
    "RemoveConditionalMutator_EQUAL_IF": "condition always true",
    "RemoveConditionalMutator_EQUAL_ELSE": "condition always false",
    "RemoveConditionalMutator_ORDER_IF": "condition always true",
    "RemoveConditionalMutator_ORDER_ELSE": "condition always false",
    "MathMutator": "arithmetic changed",
    "IncrementsMutator": "++/-- changed",
    "InvertNegsMutator": "sign flipped",
    "VoidMethodCallMutator": "call removed",
    "NonVoidMethodCallMutator": "call result dropped",
    "EmptyObjectReturnValsMutator": "returns empty value",
    "NullReturnValsMutator": "returns null",
    "BooleanTrueReturnValsMutator": "returns true",
    "BooleanFalseReturnValsMutator": "returns false",
    "PrimitiveReturnsMutator": "returns 0",
    "ConstructorCallMutator": "new object replaced by null",
    "InlineConstantMutator": "constant changed",
    "ReturnValsMutator": "return value changed",
}


def kind(mutator):
    short = mutator.rsplit(".", 1)[-1]
    return KINDS.get(short, short)


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    wanted = set(argv[1:])
    counts = collections.defaultdict(collections.Counter)
    left = collections.defaultdict(list)
    with open(argv[0], encoding="utf-8", newline="") as f:
        for row in csv.reader(f):
            if len(row) < 6:
                continue
            cls = row[1].rsplit(".", 1)[-1]
            top = cls.split("$")[0]
            if wanted and top not in wanted:
                continue
            counts[top][row[5]] += 1
            if row[5] in ("SURVIVED", "NO_COVERAGE"):
                left[top].append((int(row[4]), row[5], cls, row[3], kind(row[2])))
    for top in sorted(counts):
        c = counts[top]
        total = sum(c.values())
        print(f"\n== {top}: {total} changes, killed {c['KILLED']}, survived {c['SURVIVED']}, "
              f"no test reaches {c['NO_COVERAGE']}, other {total - c['KILLED'] - c['SURVIVED'] - c['NO_COVERAGE']}")
        for line, status, cls, method, what in sorted(left[top]):
            tag = "survived" if status == "SURVIVED" else "no test "
            print(f"  {line:5d}  {tag}  {cls.split('$', 1)[-1] if '$' in cls else '':<14} {method:<34} {what}")
    if not counts:
        print("no matching classes in", argv[0])
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
