# Mutation testing of the rule classes (TODO 6.78)

A mutation tester changes one thing at a time in a class (`>` to `>=`, `==` to `!=`, `&&` to `||`, `true` to `false`, `+` to `-`, a
number by one) and runs the tests. A change that no test notices is a **survivor**: either the tests are missing a case, or the change
makes no difference (an *equivalent* mutant). It checks that the tests would notice if a rule were changed, which a plain pass does not.

## How to run it

- **With Maven (the real tool, PIT), first batch (2026-11-16, the money, date and security classes not swept by hand):** `./mvnw -B clean -Ppit-first test-compile org.pitest:pitest-maven:mutationCoverage`, report in `target/pit-reports-first/`. The whole of `service` and `util` is the profile `pit` below.
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

## The first PIT runs (2026-11-16, the owner's machine)

Two runs of PIT 1.17.4, both `BUILD SUCCESS`:

| Run | Profile | Classes | Mutations | Killed | No test reaches it | Test strength | Time |
|---|---|---|---|---|---|---|---|
| 1 | `pit-first` (36 money, date and security classes) | 36 | 1,136 | 77% | 143 | 88% | 7 min 53 s |
| 2 | `pit` (all of `service` and `util`) | 228 | 7,817 | 69% | 1,576 | 86% | 35 min 9 s |

How to read the numbers, so the next person does not chase the wrong ones:

- **"No test reaches it" is mostly not a missing test.** The profile leaves out the `dao` tests (they need a database and share it
  between runs), so a class whose tests live in `dao` shows as untested: `BirthdayRules`, `ReceiptCost`, `TimeClockRules`,
  `CheckoutService`, `ProductService`. Its checks exist; PIT did not run them.
- **The JavaFX window classes of `util` have no unit tests on purpose** (`AlertUtil`, `DashboardWidgets`, `ThermalReceipt`,
  `ShiftSheetPage`, `PageBar`, `SceneNavigator`, `ScreenFit`, `DialogFit`, `PasswordReveal`, `ClockOffer`, `AppStyle`, `IdleLogout`,
  `SessionGuard`'s timer): they need a toolkit and a screen. `ScreenFitCheck` and the by-hand screen tour cover them.
- **A changed call to the security log** (`SecurityLog.warnLimited(...)`, `SecurityLog.event(...)`) changes nothing a test can see;
  the survivors of that kind in `TransferQrGuard`, `LoginThrottle` and `BackupService` are left as they are.
- **Wiping a password array after use** (`Arrays.fill(chars, ...)` in `PasswordUtil`, `BackupCrypto`) cannot be seen from outside;
  left as it is.
- **A boundary that gives the same answer on both sides** (`remaining <= 0 ? 0 : (remaining + 999) / 1000` in `LoginThrottle`
  and `GiftCardLookupGuard`: at exactly zero both sides give 0) is an equivalent change; left as it is.

Real gaps found in the first batch and closed the same day, each with the edge value that the older tests never used:

- `SplitBillRulesMessagesTest`: which sentence each kind of problem gives, the exact whole-line edges of a split, every field of a
  split-off copy (the old tests only asked "is there a problem").
- `PasswordRulesEdgesTest`: the common-password list is the full one, the eleven/twelve-character edge of the keyboard-run rule, four
  letters, `a` and `z` as letters of a run, four different characters, two letters behind a common word, the three-character edge
  of a username, eight and 128 characters, the phone cleaning.
- `CounterEdgesTest`: the exact millisecond at which the gift-card window, the gift-card pause and the till PIN counter forget or
  close.
- `ParseAndMoneyEdgesTest`: minutes up to 59 and the 11:59 edge of the day start, eighteen against nineteen digits and the scale
  1000/1001 edge of `Money.toDong`.

Second round, the same day (the rest of the first batch):

- `ExpenseClockSplitEdgesTest`: the sentence of every `ExpenseRules` and `ClockCheck` problem, the edges of an expense (the biggest
  amount, the earliest day, today, a part of a dong), and the rule that an amount typed for another payment method must be a number
  while the main method's own box is ignored (`SplitPaymentRules`).
- `LoginThrottleEdgesTest`: two free wrong passwords, the exact end of a wait, a name forgotten after MORE than a quarter of an
  hour and not at it, and which name a full table pushes out (the one that failed longest ago, the one with fewer failures, never
  one that is waiting).
- `HashAndPictureEdgesTest`: the work that is still to be padded after a quick password check, the iteration range of a stored hash
  (1,000 to 5,000,000) and of a backup header (100,000 to 5,000,000; the two highest counts take a few seconds each), the size limits
  and the sentence of every problem of the transfer-QR picture, the 10,000-byte account text.
- `ClockCostBirthdayRulesTest` (20 tests): `TimeClockRules`, `ReceiptCost` and `BirthdayRules` as plain rules, with no database: the
  hour a clock-in looks forgotten, 24 hours exactly, the 200th character of a reason, the rounding of an average cost, 29 February,
  the 120th birthday, the badge and the exact birthday discount.

What the first batch still shows after that, and why it stays:

- `SessionGuard` (42 changes no test reaches), `IdleLogout` (27), `DatePresets.buttons` (7) and `ShopZone` (1): window timers and
  buttons that need a JavaFX toolkit. `IdleCheckTest` and `SessionGuardTest` cover the decision they make; the timers are tried by
  hand.
- A changed `SecurityLog` call (`Permissions`, `LoginThrottle`, `TransferQrGuard`): no test can see a log line.
- In `BackupCrypto`: `updateAAD(header)` and the "last chunk" flag. Every byte of the header already changes the key, the IV or the
  checks, and the flag is flipped on both the writing and the reading side, so the file behaves the same either way. Left as it is.
- In `SplitPaymentRules`: `lines(...)` returning an empty list instead of an empty copy of it, and `gift.signum() > 0` against
  `>= 0` (a gift of zero leaves the same answer). Equivalent.

Third round (2026-11-17, the larger classes of the second run):

- `QrCodeStandardTest` (6 tests) checks `QrCode` against the QR standard written out independently in the test: the penalty scores
  that choose the mask (four texts were found for which two masks tie or the share-of-dark rule decides), every mask pattern on its
  own, the colours of the alignment and finder patterns, the timing lines and the fixed dark module. Of 17 changes tried by hand on a
  copy of the class, 15 are caught; the other two are equivalent (counting the light instead of the dark modules gives the same
  deviation from 50 %; adding a constant to the share penalty keeps the ranking of the masks).
- `ProductCsvEdgesTest` (18 tests): the separator guess, UTF-16 byte-order marks, `sep=` lines, quotes, the 5,000-row and the name,
  description and stock limits, the on-sale words, the cost-above-price warning, extra and missing cells, the leading apostrophe,
  line numbers, duplicates, and every column found when it is the first one. Of 30 changes tried by hand, 28 are caught; the other
  two are equivalent (a new `Product` already has stock 0 and is not active).
- `JpegMetadataEdgesTest` (21 tests): orientation 1 to 8 in both byte orders, the entry and offset edges, fill bytes, segments
  longer than 255 bytes, the end marker, a cut file, and what is dropped and kept by `withoutMetadata`. Of 36 changes tried by hand,
  31 are caught; the other five are equivalent (an Exif block of exactly 14 bytes, `ifd + 2` against `>=`, the first entry when the
  value is already 1, a block of exactly six bytes that is cut off).
- `FitFix` (17 survivors, 15 of them unreached): it works on live JavaFX labels; no unit test can reach it. `FitFixTest` and
  `ScreenFitCheck` cover it.
- `AccountService`, `BackupService`, `CheckoutService`, `ProductService`: most of their survivors are database statements (the name
  of a renamed employee in refunds, expenses and the time clock, audit rows, bound values). Their checks are in database tests of
  `dao` (`AuditHooksTest`, `StaffAnonymiseScrubTest`, ...), which the first two profiles leave out, so these are numbers PIT never
  measured, not proven gaps. The profile `pit-db` runs them with the `dao` tests included:
  `./mvnw -B clean -Ppit-db test-compile org.pitest:pitest-maven:mutationCoverage`, report in `target/pit-reports-db/`. Run it, read
  `mutations.csv`, and write a test only for what still survives.

What the 16 November run (profile `pit`, no `dao` tests) showed for these classes, as the number to compare the `pit-db` run with:

| Class | Killed | Survived | No test reaches it |
|---|---|---|---|
| `AccountService` | 103 | 49 | 16 |
| `BackupService` | 215 | 46 | 20 |
| `CheckoutService` | 61 | 12 | 44 |
| `ProductService` | 7 | 4 | 27 |
| `OrderService` | 26 | 1 | 2 |
| `RefundService` | 0 | 0 | 4 |

`tools/pit-survivors.py` lists what is left of a `mutations.csv` (`python3 tools/pit-survivors.py target/pit-reports-db/mutations.csv`,
or with class names after the path): per class the counts, then every survivor and every unreached change with its line, method and
kind of change, to be read next to the source. Where a survivor is a database statement, look first in `src/test/java/.../dao/` for the
test that should see it before writing a new one.

The `pit-db` run (2026-11-21, the owner's machine, PIT 1.17.4, `BUILD SUCCESS`, 17 min 52 s, 850 tests examined, 7,846 test runs):

| Classes | Mutations | Killed | No test reaches it | Survived (by subtraction) | Test strength |
|---|---|---|---|---|---|
| the six of `pit-db` | 635 | 502 (79%) | 56 | 77 | 87% |

Against the `pit` run of 16 November (637 changes in the same six classes) that is 412 killed, 86 unreached and 112 survived before: the `dao` tests
kill 90 more and reach 30 more. The weakest kinds are boundary changes (12 of 22 killed), true returns (13 of 24), empty-object returns (19 of 33) and
null returns (30 of 47); negated conditions are at 91%. **Only the totals are known:** `target/pit-reports-db/` was deleted by the
`./mvnw -B clean test` that the owner ran fifteen minutes later (`clean` empties `target/`), so no `mutations.csv` exists and the 77 survivors and
56 unreached changes cannot be named yet. Copy `target/pit-reports-db/` somewhere else before any `clean`.

**The report of the second `pit-db` run** (`mutations.csv`, 637 changes): killed 505 (79%), survived 76, no test reaches 56.

| Class | Killed | Survived | No test reaches it |
|---|---|---|---|
| `AccountService` | 147 | 5 | 16 |
| `BackupService` | 216 | 45 | 20 |
| `CheckoutService` | 96 | 15 | 6 |
| `ProductService` | 19 | 11 | 8 |
| `OrderService` | 27 | 0 | 2 |
| `RefundService` | 0 | 0 | 4 |

What was done with them (session 276; the tests were written without a compiler and have not been run, see the owner's status document):

- **Equivalent, no test:** `ProductService.save`/`delete`/`deactivate` calls of `Permissions.requireAdmin` do the same as the check in `ProductDAO`, so removing the call changes nothing a caller sees *except* the order of the answers: for `save` and `delete` that order is now tested (a non-administrator is told "no permission", not "name taken" or "false"); for `deactivate` the answer is identical, so nothing is tested. `CheckoutService.pay` line 265 (`setSubtotal`): `OrderDAO.insert` works out the subtotal itself (line 204). Lines 245, 247 and 248 (the once-per-customer voucher check before the payment): the payment checks again, so only the moment of the refusal differs.
- **New tests, written, not run:** `ProductServiceFieldsTest` (every field of a new and of an existing product; the title of "name taken" and of "code taken"; permission before duplicate; the picture file goes with the last product that uses it and not before; `findAll`, `findById`, `existsByNameInCategory`, `countUsingImage`, `insertAll`, `move`), `AccountServiceFieldsTest` (full name, position, address and salary written by `update`; a phone another employee has; the four lookups; the last-administrator message), `CheckoutServicePaidFieldsTest` (cash handed over, time of payment and the freed table of a bill made at the till; an order object without a saved id is paid as a new order).
- **Still open (not read yet):** the 45 survivors and 20 unreached changes of `BackupService` (most in `applyPendingRestore`, `createEncryptedBackup`, `stampIsAfter`, `isDue`, `sizeOf`, `pendingIsOlderThan`); `CheckoutService.payComplimentary` (the open-order path, the table, the `Give` checks) and `paySplit` line 168; `AccountService` `createFirstAdmin`, `phoneTaken` on create, `insertUser`, `translate`; the four `RefundService` methods and `OrderService.lastOrderOf`/`autosave`.

Next: run the three new test classes, then `pit-db` again and compare with the table above.

**The third `pit-db` run** (2026-11-21, with the new test classes; `mutations_1.csv`, 637 changes): killed 531 (83%), survived 62, no test reaches 44.

| Class | Killed | Survived | No test reaches it | Second run (killed / survived / unreached) |
|---|---|---|---|---|
| `AccountService` | 154 | 2 | 12 | 147 / 5 / 16 |
| `BackupService` | 214 | 47 | 20 | 216 / 45 / 20 |
| `CheckoutService` | 99 | 12 | 6 | 96 / 15 / 6 |
| `ProductService` | 37 | 1 | 0 | 19 / 11 / 8 |
| `OrderService` | 27 | 0 | 2 | 27 / 0 / 2 |
| `RefundService` | 0 | 0 | 4 | 0 / 0 / 4 |

- `ProductService`: the one survivor (line 95, `requireAdmin` in `deactivate`) is the equivalent one described above.
- `AccountService.update`: the two survivors were `setPhone` and `setHireDate` (line numbers of the first list were read one line off): the test changed neither. It now does (session 278, not run yet).
- `CheckoutService.pay` line 288 (`tableDAO.setEmpty` after `orderDAO.insert`): equivalent, `OrderDAO.insert` frees the table itself (line 336). Line 277 (`setPaymentLines`): open. `paySplit` line 168 (an order with id 0): a test was added (session 278, not run yet).
- `BackupService` changed by a few changes between two runs of the same code (`copyCapped`, `createBackup` line 145, `sizeOf`): a change that is killed in one run and survives in the next is a timing effect of the test run, not a gap; it is not worth a test.
- Open: `BackupService` (the main part), `payComplimentary`, `RefundService` (4), `OrderService` (2), `AccountService` `createFirstAdmin`/`insertUser`/`translate`/`phoneTaken` on create.

**The fourth `pit-db` run** (2026-11-21, after the widened tests; `mutations_6727af.csv`, 637 changes): killed 539 (85%), survived 54, no test reaches 44. The three new classes pass (19 tests, `BUILD SUCCESS`).

| Class | Killed | Survived | No test reaches it | Third run (killed / survived / unreached) |
|---|---|---|---|---|
| `AccountService` | 156 | 0 | 12 | 154 / 2 / 12 |
| `BackupService` | 219 | 42 | 20 | 214 / 47 / 20 |
| `CheckoutService` | 100 | 11 | 6 | 99 / 12 / 6 |
| `ProductService` | 37 | 1 | 0 | unchanged |
| `OrderService` | 27 | 0 | 2 | unchanged |
| `RefundService` | 0 | 0 | 4 | unchanged |

- `AccountService`: nothing survives. The 12 unreached are: `createFirstAdmin` (needs a database with no account at all, which the shared test database never is), the null-employee branch of `insertUser`, the blank-phone branch of `phoneTaken`, and `translate` (the message for a database failure that the earlier checks make almost impossible); left on purpose.
- `CheckoutService`: the `paySplit` boundary is killed. Left: the three equivalent ones (245, 247, 248), 265 and 288 (equivalent), 277 (`setPaymentLines`), and `payComplimentary`.

**`BackupService`, session 280 (tests written without a compiler, not run yet).** Three new classes, 31 tests: `BackupDueAndPruneEdgesTest` (9, no database: exactly one interval old is due, the newest of several copies decides, the picture rule does not depend on the order the folder lists files, names too short or unreadable as a time, the lock on a backup), `BackupServiceRestoreEdgesTest` (15: the text `describe()` gives, the parked restore seen, aged and cancelled, a locked backup looked at and scheduled, an employee refused before a locked backup is opened, the safety copies made and tidied, the automatic backup started on its own thread and the one awaited on leaving, the transfer QR, the failed-restore tidying) and `BackupServiceApplyRestoreEdgesTest` (7: an earlier broken restore's leftovers swept, a file that is no database refused, the old pictures put back when the restore fails after the swap, the exact answer text, the tidying of `restore-failed-*` after a success and after a failure).

Left on purpose, and why:
- **Free-space checks** (`createBackup` lines 130 and 131, `createEncryptedBackup` line 221, `sizeOf` line 268): a test would need a nearly full disk.
- **`verifyOrDelete` (line 145) and the read-back of a locked backup (line 234):** they only act when a just-written zip is damaged, which the program's own code never produces; a test would have to damage the disk.
- **`writeZip` line 314** (`closeEntry`): `putNextEntry` closes the previous entry itself; equivalent. **`zipsWithPrefix` line 469** and **`accountsBroughtBack` line 975** return an empty list where it is already empty; equivalent. **`copyCapped` and `copyCappedToNowhere`** (`read > 0` or `>= 0`): a read never returns 0 for a non-empty buffer; equivalent.
- **`extractImages` lines 1023 and 1024:** the cap on all pictures together is 2 GiB.
- **`foldLeftoverLogInto` line 929** (the copy of the log fails) and **`applyPendingRestore` line 807** (MariaDB): need a failing disk or another database.
- **Thread plumbing** (`setDaemon`, restoring the interrupt flag, lines 359, 370 and 375): no behaviour a test can see.
