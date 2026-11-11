# Mutation testing of the rule classes (TODO 6.78)

A mutation tester changes one thing at a time in a class (`>` to `>=`, `==` to `!=`, `&&` to `||`, `true` to `false`, `+` to `-`, a
number by one) and runs the tests. A change that no test notices is a **survivor**: either the tests are missing a case, or the change
makes no difference (an *equivalent* mutant). It checks that the tests would notice if a rule were changed, which a plain pass does not.

## How to run it

- **With Maven (the real tool, PIT):** `./mvnw -B -Ppit test-compile org.pitest:pitest-maven:mutationCoverage`. Report in
  `target/pit-reports/`. Not part of the normal build, takes a few minutes. Not run yet: it needs Maven Central, which this
  session could not reach, so the profile in `pom.xml` is untested.
- **Where Maven cannot run:** `tools/mutation-sweep.py` (JDK and JUnit only):
  ```
  export JUNIT_JAR=... HAMCREST_JAR=...
  LFT_WORK=/tmp/lunavera-build LFT_BUILD_ONLY=1 tools/run-library-free-tests.sh
  python3 tools/mutation-sweep.py /tmp/lunavera-build service/RefundMath service/GiftCardRules
  ```
  About one minute per 80 changes. It runs only the tests that mention the class and need no database or JavaFX, so a survivor can
  still be caught by `GiftCardTest`, `ReservationDaoTest` and the like on the owner's computer: look before writing a test.

## The first run (2026-11-06, the six money classes)

| Class | Changes that compiled | Survivors before | After the new tests | What is left |
|---|---|---|---|---|
| `RefundMath` | 60 | 21 | 4 | `>` vs `>=` when the amount equals the room, a loop condition, a return value and the guard's `return 0` (the difference of two equal numbers): equivalent |
| `LoyaltyPolicy` | 51 | 17 | 13 | all equivalent (see below) |
| `DiscountPolicy` | 28 | 2 | 1 | `<= 0` vs `< 0` on a zero subtotal: 0 either way |
| `MoneyParser` | 58 | 4 | 1 | scale `<= 0` vs `<= 1`: every shorthand word multiplies by a thousand or a million, so the scale is never 1 |
| `VoucherRules` | 79 | 21 | 1 | `left.signum() <= 0`: a zero amount left gives 0 either way |
| `GiftCardRules` | 43 | 34 | 6 | the sum of the spent figures with a negative one (the hand-out repairs the difference), and the rounding hand-out loop: it repairs itself, so starting one card later or stopping one step later gives the same result |

What the first run found, all now covered by tests:

- `GiftCardRules` had **no test that runs without a database** (its tests were inside `GiftCardTest`): now `GiftCardRulesTest`.
- `RefundMath`: a denominator of exactly 1, the "last pieces bring the total up when rounding came out short" rule, an adjustment
  that must reach the first line or take a line down to zero, and the second step of a line (`RefundMathEdgesTest`).
- `LoyaltyPolicy`: spending exactly the balance, a one-point redemption, the tier of "no customer" (`LoyaltyPolicyEdgesTest`).
- `VoucherRules`: every limit of a new voucher on both sides, the characters of a code at the ends of their ranges, once-per-customer
  (`VoucherRulesEdgesTest`).
- `MoneyParser`: exactly 15 digits in all three ways of writing, and a letter that must never count as a separator (`MoneyParserEdgesTest`).

## Equivalent mutants in `LoyaltyPolicy` (why they are left)

Under the shop's rules the change gives the same answer: a balance of 0 or a zero bill gives 0 points anyway (`<= 0` vs `< 0`); the
`scale` of a `FLOOR` division (0 vs 1) cannot change a floor of a floor; the `Integer.MAX_VALUE` caps are never reached because the
balance is an `int` too; `points < 0` after `points == 0` has returned; `tierOf` for no customer gives `NEW` for 0 and for 1.

## Rules for the next run

Run it after changing a rule class, on that class only. A new survivor that is not equivalent becomes a test; an equivalent one is
written here with its reason. Do not chase 100%: a test written only to kill an equivalent mutant tests the code, not the rule.

## A second sweep with six more test classes (session 225)

A separate run on the same six classes (a small runner that applies one change at a time to a copy of the source, compiles that one file
and runs the tests that passed beforehand; 365 changes) found changes that the first sweep's tests did not notice: 10 of the 45 that
survived there. Six test classes now pin them, `RefundMathBoundaryTest`, `LoyaltyPolicyBoundaryTest`, `DiscountPolicyBoundaryTest`,
`VoucherRulesBoundaryTest`, `GiftCardRulesBoundaryTest` and `MoneyParserBoundaryTest` (46 tests, no database). Examples: the money that is
left when the rounding of a refund is adjusted (`return target - diff`), every Vietnamese word for a thousand and for a million, a bill
too big for a `long`, a customer with no points when the owner lets one point make a silver customer, and the 5,000,000 and 255 limits.
Constants such as `COLUMN_MAX_LENGTH` are read through reflection in these tests, because a `static final int` is copied into the test
when it is compiled and a change to the class would never be seen. Survivors left after that run (20 of 360) were read one by one and are
equivalent: the changed code gives the same result for every input (for example a guard whose constant cancels in a difference, or `>=`
for `>` at a value that gives the same answer either way).

## Random tests of the money rules (TODO 6.76)

A mutation run asks "would a test notice if the rule were changed?"; the random tests ask the other way: "does the rule hold for inputs
nobody thought of?". Six classes, `RefundMathPropertyTest`, `SplitBillRulesPropertyTest`, `LoyaltyPolicyPropertyTest`,
`VoucherRulesPropertyTest`, `GiftCardSharesPropertyTest` and `DiscountPolicyPropertyTest` (14 tests, no database, about a second), feed
the rules thousands of random bills, discounts, balances and refunds and check what must always be true (the parts add up, nothing is
negative, nothing is more than the bill or the balance). The loyalty and discount tests also pick random shop rules inside what Settings
allows, so a rule that only holds at the factory numbers is found.

- **Fixed seed.** `RandomCases.DEFAULT_SEED`, so the build is the same every day. A failure prints `seed=... case=...` and the command that
  runs that one case again: `./mvnw -B test -Dtest=RefundMathPropertyTest -Dlunavera.propertySeed=<seed> -Dlunavera.propertyCase=<case>`.
- **Looking for new mistakes.** Run with another seed (`-Dlunavera.propertySeed=12345`); the same seed always gives the same cases.
- **When one fails for real,** copy the printed input into an ordinary test next to the rule, so it stays fixed whatever the seed.
- **A new money rule** gets a property test the same way: write the one sentence that must always be true, generate inputs with
  `RandomCases.between`, and wrap the check in `RandomCases.run`.
