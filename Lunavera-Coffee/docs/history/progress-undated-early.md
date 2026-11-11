# Lunavera Coffee — Progress history: Sessions without a date in their heading (everything up to Session 56, before 2026-10-17)

> Moved out of `progress.md` by TODO 6.86 (session 184), word for word and in the order they had there. Nothing was rewritten.
> The sections are in the order of the old file, which is the order they were APPENDED in, not always the order of their numbers or dates (parallel branches were merged: the same session number can appear more than once).
> The newest sessions and the index of these files are in `progress.md`.

## Contents (52 sections)

- Session 41 notes — bugs 2.6 to 2.10
- Session 40 notes — bugs 2.1 to 2.5
- Session 39 notes — test isolation fix, 1.5 decision, one bundled font (TODO 1.6)
- Session 35 notes — receipt QR text is plain ASCII (TODO 1.1)
- Session 34 notes — dev-checks converted to tests and deleted
- Session 33 notes — quality of life, items 6-9 (step 4 of the cleanup list)
- Session 32 notes — quality of life, items 1-5 (step 4 of the cleanup list)
- Session 31 notes — bugs fixed (step 3 of the cleanup list)
- Session 30 notes — unused dependencies removed (step 2 of the cleanup list)
- Session 29 notes — dead-code removal (step 1 of the cleanup list)
- Session 28 notes — redesigned-UI branch became the main branch
- Session 27 notes — merging the dine-in-table branch into the money/schema-versioning branch
- Session 26 notes — file logging + product table stock column
- Session 25 notes — money as INTEGER dong + schema versioning (items 6/7, together)
- Session 24 notes — starter menu from menu.zip (one-time, deletion-safe)
- Session 23 notes — order-history paging, and catching up on undocumented work
- Session 22 notes — reproduced real verification here + DAO tests
- Session 21 notes — relative image paths, busy_timeout, and real automated tests
- Session 20 notes — merged a second parallel audit branch, upgraded item 4, closed item 9
- Session 19 notes — third batch of audit fixes (see `TODO.md`)
- Session 18 notes — second batch of audit fixes (see `TODO.md`)
- Session 17 notes — first batch of audit fixes (see `TODO.md`)
- Session 16 notes — read-only audit (no code changed)
- Session 15 notes — admin sales report (with CSV export)
- Session 14 notes — order/invoice history + cancel (refund) order
- Session 13 notes — removed the last fake data from both dashboards
- Session 12 notes — fixed the "dialogs lose typed input" bug everywhere it existed
- Session 11 notes — backlog polish + two real gaps found
- Session 10 notes — Customer Management built (Step 3 done)
- Session 9 notes — verified Session 7's Order/POS build, no bugs found
- Session 8 notes — reconciling a second parallel Product Management implementation
- Session 7 notes — Order Management / POS built, plus a note on this session's starting point
- Session 6 notes — Product Management built
- Session 5 notes — dialog "mouse stuck" fix, attempt 2 (deferred)
- Session 4 notes — dialog "mouse feels stuck" fix
- Session 3 notes — performance fix ("login and buttons feel laggy")
- Session 2 notes
- Session 1 summary
- Session 41 (continued): items 2.11-2.15
- Session 42: the rest of TODO section 2 (2.16 - 2.63, except 2.38, 2.53, 2.56)
- Session 43
- Session 45: merge with the owner's MariaDB branch
- Session 47 (9th pass)
- Session 48 (10th pass)
- Session 49 (11th pass): TODO 6.30 - 6.35
- Session 51 (11th pass, other branch): TODO 6.37 - 6.39
- Session 50 (12th pass): TODO 6.36, 6.40 - 6.45
- Session 52: merge of the two branches (6.30 - 6.45)
- Session 53: second merge check (this tree against the 12th-pass branch)
- Session 54 (13th pass): the small practices, TODO 6.12, 6.16 - 6.19, 6.23, 6.26, 6.28, 6.33
- Session 55 (13th pass, part 2): the medium items I could do without a screen or a server
- Session 56 (13th pass, part 3): MariaDB data rules and the data line on the login screen

---

## Session 41 notes — bugs 2.6 to 2.10
- **2.6/2.7** `config/SchemaMigrations` (schema version 2 = indexes; unique customer phone re-checked every start, skipped with a log
  warning while duplicates exist). `CustomerDAO` turns a UNIQUE violation into a plain message.
- **2.8** `service/BackupService`: zip backups (db + images + qr), automatic daily backup in a background thread started from
  `HelloApplication.init`, pruning of `auto-*.zip` only, restore parked as `data/restore-pending.*` and applied by
  `applyPendingRestore()` before the database is opened (it saves `before-restore-*.zip` first; failures rename the pending file so
  they are not retried). Dashboard got "Khôi phục dữ liệu". Zip-slip safe.
- **2.9** `util/IdleLogout` + `IdleTracker` (15 min default, `-Dlunavera.idleMinutes`), `SessionGuard.idleLogout`.
- **2.10** The staff detail fields were never filled by anything; now in the account dialog and the detail panel; `util/MoneyParser`.
- Tests: 235 here (SchemaMigrationsTest 6, BackupServiceTest 12, IdleTrackerTest 5, MoneyParserTest 4, EmployeeDetailsTest 2).
- Verified on screen (Xvfb + openbox): dashboard buttons, account detail panel, account dialog, idle logout.

## Session 40 notes — bugs 2.1 to 2.5
- **2.1** `DataCleanup` (class, call, `DataCleanupTest`) removed.
- **2.2** Default password: `PasswordChangeDialog` (forced = no way out except "Đăng xuất"; voluntary = sidebar button "Đổi mật khẩu"),
  rules in `PasswordChangeService`; the sidebar button is added to every screen by `SidebarExtras` through `SceneNavigator.setScreenDecorator`.
- **2.3** `DiscountPolicy` (admin unlimited, employee 10% - owner to confirm, reason always required), `orders.discount_reason` column
  (`addColumnIfMissing`), saved by `OrderDAO.insert` and `payExistingOrder`. Not shown anywhere yet (TODO 2.37).
- **2.4** `ScreenFit` (wrapper + `ScreenFitMath`): fixed layouts are scaled to the window, window resizable and fitted to the display.
  Scenes must be created with an explicit size (`ScreenFit.newScene`), or a scene made from the root keeps 1600x1000 inside a smaller window.
  Verified under Xvfb + openbox at 1366x768. Stopgap; real fix is flexible layouts (TODO 2.38).
- **2.5** `HelloApplication.init` catches start-up errors; `start` shows `StartupProblem` text and exits.
- Tests: 205 here (DiscountPolicyTest 10, DiscountReasonDaoTest 4, PasswordChangeServiceTest 7, StartupProblemTest 4, ScreenFitTest 5; DataCleanupTest removed), also green in 5 different orders.

## Session 39 notes — test isolation fix, 1.5 decision, one bundled font (TODO 1.6)
- **Gap:** sessions 36-38 (1.2 images, 1.3 data folder, 1.4 transfer QR) are in TODO.md but have no entry here.
- **Tests wrote into the real database (bug 2.36):** `AddColumnIfMissingTest` loaded `DatabaseConfig` before the temp
  folder existed, so the suite used `<cwd>/data/lunavera.db`. Fixed (see TODO 2.36); `TestDatabaseSupport.ensureReady()`
  now refuses to run if the live DB is the real one; surefire is also pointed at `target/test-data` (unverified, no Maven here).
- **1.5 decision (owner, 2026-10-11): migrate to MariaDB/MySQL with phpMyAdmin.** I had started option A (a SQLite guide
  with tested recipes); stopped and discarded at the owner's request. Plan is in TODO 1.5, nothing built.
- **1.6 font:** Be Vietnam Pro (regular, italic, bold) in `resources/fonts/`, `util/AppStyle` loads it and adds
  `css/app.css` to every window's scene (screens, dialogs, alerts, menus), all per-rule `-fx-font-family` removed from the
  7 style sheets, weights 600/500 -> bold/normal. Checked with real JavaFX under a virtual screen; Windows not verified,
  and the icon symbols rely on font fallback there (section 0 check). `FontBundleTest` added; 176 tests pass here.
- Files: `util/AppStyle.java`, `HelloApplication.java`, `css/*.css`, `fonts/`, `pom.xml`, `TestDatabaseSupport`, `AddColumnIfMissingTest`, `AppLoggingTest`, `FontBundleTest`.

## Session 35 notes — receipt QR text is plain ASCII (TODO 1.1)
- **Why:** scanning the receipt QR showed jumbled text. `QrCode` stores the text as UTF-8 bytes with no character-set
  marker, and some phone scanners read those bytes as Latin-1. The old text `Hóa đơn #12 / Tổng: 1.250.000đ` then shows
  as `HÃ³a Ä‘Æ¡n #12 / Tá»•ng: 1.250.000Ä‘` (reproduced by decoding the old QR image and re-reading its bytes as Latin-1).
- **Change:** `ReceiptQr.payload` is now ASCII only: `Lunavera Coffee` / `Hoa don #12` / `Tong: 1.250.000 VND` /
  `14:03 10/10/2026`. The amount is formatted by `ReceiptQr.amount` (dots between thousands, no locale, no `đ`).
  Still a plain summary, not a payment request. No change to `QrCode` (an ECI marker is not needed for ASCII).
- **Tests (+3, 135 total):** payload is ASCII only and one byte per character, the amount format (0, 999, 1.000,
  25.000, 1.250.000, no total), and the payload is the same under German and Vietnamese default locales.
- **Verified:** rendered the new and old QR with the app's own `QrCode`, decoded both with OpenCV: the new one reads back
  exactly and its bytes are identical in UTF-8 and Latin-1.
- **Not verified:** a real phone scan (I have none). Please scan a receipt on the phone that showed the jumbled text;
  the QR on a receipt that was already printed keeps the old text.

## Session 34 notes — dev-checks converted to tests and deleted

`dev-checks/` (four hand-run programs outside Maven) is gone; its checks now run in `mvn test`:
`MoneyMigrationTest` (old REAL-column database built by hand, migrated to integer dong; values, NULL cost,
links, backup file, second run is a no-op), `MenuSeederTest` (33 products, 4 categories, images and prices; a
deleted item stays deleted after a "restart", simulated by a second connection to the same file because the
seeder's own persistent flag is what matters), `SessionGuardTest` (locked, deleted, promoted and demoted
accounts against a real database). `AppLoggingCheck` was already covered by `AppLoggingTest`, which now also
checks the message really is in the file. `TestDatabaseSupport` is public so the `config` tests can use it.
`pom.xml` comment updated. Tests: 132 pass. **Not verified:** `mvn clean test` on this change.
Note: earlier sessions below still mention `dev-checks/`; that folder no longer exists.

## Session 33 notes — quality of life, items 6-9 (step 4 of the cleanup list)

- **Item 6:** the first-run log line no longer prints the default admin password.
- **Item 7:** the order-history search escapes `%`, `_` and `\` (`LIKE ? ESCAPE '\'`), so they match literally
  instead of matching every order. Tested.
- **Item 8:** `orders.table_number` is now part of the `CREATE TABLE orders` statement. Databases made before
  it existed get it through `DatabaseConfig.addColumnIfMissing`, which checks `PRAGMA table_info` instead of
  catching the error. I did not bump `user_version`: every existing database already goes through that check on
  start-up, so a version number would add nothing. Tested on an old-shaped table.
- **Item 9:** `LoginBackgroung.png` is now `LoginBackground.png`, and `dangnhap.fxml` points to it.
  **Check the login screen background still appears** (a missing image would not fail the build).
- Tests: 124 pass (4 new). The author tested steps 4.1-4.9 by hand on 2026-10-10 and reported all working,
  which closes the cleanup list (steps 1-4). `mvn clean test` has not been re-run since 100 tests (step 3).

## Session 32 notes — quality of life, items 1-5 (step 4 of the cleanup list)

- **Login (item 4):** wrong username and wrong password now give the same message ("Tên đăng nhập hoặc mật
  khẩu không chính xác."); a locked account is only reported to someone who knows its password; an unknown
  username costs the same time as a real one. New `LoginThrottle`: 5 wrong tries in a row for a username
  block it for 30 s (then 60, 120... up to 5 min); it is a delay, not a permanent lock, kept in memory and
  shared by every login screen. `LoginStatus` is now SUCCESS / INVALID_CREDENTIALS / INACTIVE / TOO_MANY_ATTEMPTS.
- **POS customer line (item 5):** under the phone field the POS shows "Khách cũ: name · N điểm" for a known
  phone, or "Khách mới: ..." for a valid unknown phone. Also fixed while there: editing the phone away from
  the attached customer's number used to leave that customer attached (the order could be paid under the
  previous person); it now detaches. Late lookup results for a number that is no longer typed are ignored.
- **Customer find-or-create (item 1):** moved out of the controller into `CustomerService` (same lock, same
  rules: never renames, invalid phone means walk-in) so it can be tested, including 12 threads racing on one phone.
- **Receipt QR (item 3):** now a readable summary (shop, order number, total, time) instead of
  `LUNAVERA|HD12|...`. Decided NOT to put a VietQR payment code on it: the receipt is for an order that is
  already paid, so a payment QR could be paid twice. A real VietQR belongs in the payment dialog and needs
  the shop's bank details and a transfer payment method; listed in TODO.md as an idea. Verified: an independent
  decoder (OpenCV) reads the new payload back exactly, including Vietnamese text and line breaks.
- **Tests (item 1):** 20 new (120 total): `LoginThrottleTest`, `AuthenticationServiceTest`, `CustomerServiceTest`,
  `ReceiptQrTest`; the cleanup (`DataCleanupTest`) and pay-with-new-customer tests came with step 3. Not covered
  (UI only, need a JavaFX toolkit): category dropdown sync and the table-toggle confirm.
- **Docs (item 2):** `TODO.md` rewritten to match the code (it still mentioned things long done).

**Not verified:** the POS and login screens (no JavaFX display here); `mvn` was not run on this change.

## Session 31 notes — bugs fixed (step 3 of the cleanup list)

- **Payment (bugs 2, 3, 7):** `OrderDAO.payExistingOrder(orderId, customerId, ...)` now links the customer
  typed at the till to the order in the same transaction (so they get their loyalty points), only pays an
  order that is still `OPEN` (a second payment or a cancelled order throws and changes nothing), and frees
  the table inside that same transaction. The POS creates/finds the customer in the background task, not
  on the UI thread. `TableDAO.setEmpty` is now one transaction (free table + cancel OPEN order) and throws
  instead of swallowing errors.
- **Autosave (bug 6):** autosaves and the payment run through `Async.runOrdered` (one background thread, in
  submission order), so an older cart cannot overwrite a newer one or arrive after payment;
  `saveOrUpdateOpenOrder` leaves an order alone if it is no longer `OPEN`. A failed autosave is logged and
  the cashier is warned once.
- **Customers (bugs 4, 5):** phone numbers are stored and searched in one form (`ValidationUtil.normalizePhone`:
  `+84901111222`, `84901111222`, `0901 111 222` all become `0901111222`). The POS no longer renames an
  existing customer; the name field shows the stored name instead.
- **Cleanup (bugs 8, 14):** `DataCleanup` writes `~/.lunavera-coffee/backups/before-cleanup-<time>.db` first
  (and skips the cleanup if that fails); a category that still holds products is kept.
- **Other (bugs 9, 10, 11, 13):** lookup/load failures are logged (order load shows an error); the login
  employee lookup runs off the UI thread; order-start logic lives in one method, `startOrderForTable`
  (which also ignores a second click while one is in flight); "(Tầng 1)" removed from the POS.
- Bug 1 confirmed by the user's own `mvn clean test` (2026-10-10): 100 tests, 0 failures, BUILD SUCCESS, JUnit 4
  provider auto-detected. Bug 12 (Linux dialog workaround) closed: the user reports it was solved earlier and
  still works.

Verified: compiles; 100 tests pass (11 new, covering all of the above except the UI wiring).
**Not verified:** `mvn javafx:run` and the POS screens themselves (customer at payment, autosave warning, login) were not run -
no JavaFX display here; `mvn` was not run.

## Session 30 notes — unused dependencies removed (step 2 of the cleanup list)

Removed from `pom.xml` (and from `module-info.java` where listed there): `junit-jupiter-api` /
`junit-jupiter-engine` (all tests are JUnit 4; with the JUnit 5 engine present and no vintage engine,
Maven's `mvn test` may have discovered 0 tests), `kotlin-stdlib`, `kotlin-test`, `kotlin-maven-plugin`
(there are no `.kt` files) plus `requires kotlin.stdlib`, `tilesfx`, `fxgl`, `javafx-web`, `javafx-media`,
`javafx-swing`, `controlsfx`, `formsfx-core`, `validatorfx`, `ikonli-javafx`, `bootstrapfx-core` plus their
six `requires` lines, and the `junit.version` / `kotlin.version` properties. What remains: `javafx-controls`,
`javafx-fxml`, `sqlite-jdbc`, `slf4j-api`, `junit` 4.13.2 (test). The compiler plugin's explicit
`compile` / `testCompile` executions were left as they are.

Verified: the sources compile as a real module (`module-info.java` with only `javafx.controls`,
`javafx.fxml`, `java.sql`, sqlite and slf4j on the module path) and 89 tests pass. **Not verified:**
`mvn clean test` and `mvn javafx:run` were not run (no Maven Central access here) - run both once, and
check that `mvn test` reports 89 tests.

## Session 29 notes — dead-code removal (step 1 of the cleanup list)

Removed code that nothing calls (checked against all source, FXML and tests, and by compiling):
- `AdminController`: the old alias handlers (`openHome`, `handleHome`, `handleProducts`, `handleOrders`,
  `handleCategories`, `handleAccounts`, `handleLogout`, `handleNavigation`), plus `openInventoryManagement`
  and `openEmployeeManagement`. `EmployeeController`: `openProductManagement`, `openCategoryManagement`,
  `openAccountManagement`, `openInventoryManagement`. `CustomerController` / `OrderController`:
  `openInventoryManagement`, `openEmployeeManagement`; `OrderController.handleAddProductToCart`.
- Orphan `@FXML` fields with no `fx:id` (`rememberCheckBox`, `signInButton`, `customerLabel`, the dashboard
  `recentOrders*` / admin `lowStockList`, `addProductButton`) and their always-null guarded code; about 20
  declared-but-unused button/axis fields.
- Cascade: `DashboardWidgets.fillRecentOrders`, `OrderDAO.findRecent` and the `recentOrders` entry of the
  dashboard stats (a database query that ran on every dashboard load for a list nobody showed).
- `ProductDAO.adjustStock` (bypassed the transactional stock guard), `findByCategory`, `countByCategory`;
  `UserDAO.countByRole`; `TableDAO.findByNumber`; `Pager.nextPage/previousPage`; `DiningTable.setCapacity`,
  `setCurrentOrderId`; the write-only `OrderItem.orderId`; unused imports.
- Tests adjusted: the `findByCategory` test was deleted, `PagerTest`/`TableDAOTest`/`UserDAOTest` use the
  remaining API. 89 tests pass.

**Kept on purpose:** `handleHomeTableClick`, `openEditCategoryDialog`, `openEditProductDialog` and
`openEditDialog` (called through `this::method` references - an earlier scan wrongly marked them dead;
the compiler caught it); `Role.CUSTOMER` (still used by login, accounts screen and dialog);
`DiningTable.getCurrentOrderId` and `QrCode.getVersion/getMask` (only tests call them);
`dev-checks/` (the only checks for `MoneyMigration`, `SessionGuard` and `MenuSeeder` - convert them to
tests before deleting); unverified CSS rules.

## Session 28 notes — redesigned-UI branch became the main branch

The user supplied a parallel branch with a new UI/UX and asked for it to be the base from now on.
Its backend was ~identical to ours (57 identical files incl. `MenuSeeder`, `MoneyMigration`), so the
merge was "new branch + the small things only we had".

**Taken from the new branch (replaces ours):** all FXML/CSS/controllers, the POS (product catalog
grid, 12 dining tables built from the DB, manual table toggle, inline walk-in customer name/phone,
order-history button), and its table model: orders persist as `OPEN` as soon as a table is served;
`TableDAO` computes status from an OPEN order or a stored `OCCUPIED`; `dining_tables.current_order_id`
is a real FK. Our own `DiningTableSeeder`, old `TableDAO` and old `TableDAOTest` were dropped.

**Ported onto it:**
- Dining-table seeding gated by an `app_flags` flag (`dining_tables_v1`) instead of "if table is empty",
  so emptying the table never re-seeds it.
- Dialog Enter/Esc (`AlertUtil.setDefaultButton/setCancelButton`, also Esc = "Không" on confirms) on the
  category, account, product, customer and payment dialogs.
- `TextLengthLimiter` on name/description/username fields and the POS walk-in name/phone fields.
- Dashboard low-stock note shows the real count again (the new branch had a generic label).
- **Bug fixed in the new POS:** toggling an occupied table to "inactive" silently cancelled its OPEN
  order (`TableDAO.setEmpty`) and left a stale cart. Now asks for confirmation and reloads the order panel.
- Tests: `TableDAOTest` rewritten for the new `TableDAO`; `AppLoggingTest` ported.

**Hotfix right after the merge:** the app failed to start on a database that already had dining tables
but no `dining_tables_v1` flag (`UNIQUE constraint failed: dining_tables.table_number`). The seed insert is
now `INSERT OR IGNORE`, so an existing database just gets the flag (and any missing tables). Verified by
re-running `initialize()` on a DB with the flag removed and only 6 of 12 tables left.

**Verified (really compiled with javac against JavaFX 11 jars, not just read):** 74 JUnit tests pass;
dev-checks AppLoggingCheck, MenuSeederCheck, MoneyMigrationCheck, SessionGuardCheck pass; every FXML
`onAction` has a controller handler and every referenced `fx:id` has an `@FXML` field (the only
unreferenced ids are decorative root nodes). **NOT verified:** nothing was run with `mvn javafx:run`
(no JavaFX runtime/display here) - the new screens and the table-toggle confirm still need a human check.
`dev-checks/DiningTableSeederCheck.java` was dropped (it targeted the old seeder).


This file tracks the *actual* implementation status (as verified by reading the code),
not what the UI merely displays. Status legend: ✅ Completed 🟡 Partial ⬜ Not started
⚠️ Known issue/decision.

## Session 27 notes — merging the dine-in-table branch into the money/schema-versioning branch

**The user had two branches that had diverged from the same point** and asked for them merged:
this one (Sessions 25-26: money as INTEGER dong, schema versioning, file logging, a product
stock column) and a separate one that had built real dine-in table tracking plus dialog
Enter/Esc hints and input length limits, independently, on top of an earlier snapshot of this
same codebase. Adopted this branch (the money/schema work) as the base - it's the harder,
riskier, more foundational change - and merged the other branch's work on top, rather than the
reverse.

**Brought over, unchanged (the other branch never touched anything money/schema-related):**
`DiningTable`, `TableStatus`, `TableDAO`, `DiningTableSeeder`, `TextLengthLimiter`,
`TableDAOTest`, `dev-checks/DiningTableSeederCheck.java`. `AlertUtil.java`, `OrderController.java`,
and `CustomerController.java` were replaced wholesale with the other branch's versions - diffed
first to confirm this branch had made zero independent changes to any of the three (confirmed:
every differing line was something the other branch added, nothing this branch would lose).

**Real merges, not wholesale swaps:**
- `Order.java` / `OrderDAO.java`: added the `tableNumber` field and `table_number` column
  support on top of this branch's `Money`-based insert/map (binds as a plain nullable int via
  the existing `setNullableInt` helper - this column was never money, nothing about it changes
  for the INTEGER-dong migration).
- `AdminController.java`: added the other branch's dialog Enter/Esc hints
  (`AlertUtil.setDefaultButton`/`setCancelButton`) and input length limits (`TextLengthLimiter`)
  to all three of its dialogs. **Did not** bring over its stock-column implementation - this
  branch's Session 26 had already built one independently (simpler: inline `setStyle` on a
  `Number`-typed column vs. the other branch's CSS-class-based two-tier version) and the two
  can't coexist on the same column. Keeping this branch's version since it was already
  tested here; noted the other implementation is not lost, just not used.
- `DatabaseConfig.java`, the delicate one: added `dining_tables` (a plain, version-independent
  `CREATE TABLE IF NOT EXISTS` - it's a brand-new table, nothing to migrate) and
  `orders.table_number`. ⚠️ **Deliberately did not fold `table_number` into
  `MoneyMigration`/`CURRENT_SCHEMA_VERSION`**, despite that class's own docstring describing
  exactly that as the intended pattern for future schema changes: a single additive, nullable
  column doesn't need the rename-and-recreate dance a changed column *type* needs, and touching
  the already-verified `MoneyMigration` for an unrelated feature seemed like more risk than
  benefit. Instead, a small `addTableNumberColumnIfMissing()` (checks `PRAGMA table_info`,
  `ALTER TABLE ... ADD COLUMN` if missing) runs unconditionally every startup, after the
  preexisting/fresh branch. This correctly covers all three real starting states: a brand-new
  database (already has the column from the DDL, so this is a no-op), a true v0 database (column
  added right after `MoneyMigration` recreates `orders`), and - the one that actually matters
  most, since it's almost certainly the user's real current database - one already upgraded to
  v1 money before dining tables existed, which `MoneyMigration` itself would otherwise skip
  entirely (it only acts on `version < CURRENT_SCHEMA_VERSION`).
- `quanlydonhang.css`: added back the `.occupied-badge` rule the dining-table branch needed
  (this branch never touched this file, so no real conflict, just a missing addition).
- Added `AppLoggingTest.java` (adapted from the other branch's `LoggingConfigTest.java`, same
  "shared static temp home, never cleaned up mid-run" pattern as `TestDatabaseSupport` - see its
  own javadoc): this branch's file-logging had a two-process dev-check but no fast
  `mvn test`-runnable unit coverage. Genuinely new coverage, not a duplicate.
- Also fixed a stale class-level comment on `OrderDAO` while in the file for an unrelated
  change: it said "order creation (POS checkout) is not wired to any screen yet", which has not
  been true since Session 7.

**Verified far more thoroughly than usual, given the size of this merge** - every regression
check available was run for real, not just the standard full test suite:
- Whole project recompiled clean (83 classes).
- Full test suite: **74 tests, 0 failures** (unchanged count from both parent branches - nothing
  was lost, nothing silently duplicated).
- **`MoneyMigrationCheck` re-run** (builds a real v0-schema fixture database and runs it through
  the actual migration) - all its existing assertions still pass, **and its log output shows
  `addTableNumberColumnIfMissing` firing immediately after `migrateV0ToV1` recreates `orders`**,
  exactly the scenario the design above was reasoning about in the abstract - seeing it actually
  happen in that order, against a real fixture, is real confirmation, not just correct-sounding
  logic.
- `DiningTableSeederCheck`, `MenuSeederCheck`, `AppLoggingCheck`, `SessionGuardCheck` - all
  re-run as two-process (real restart) or single-process checks; all pass.
- **Not run through `mvn javafx:run`.** Given the number of moving parts touched here, a full
  manual pass is worth more than usual this time: log in, confirm the default admin still
  works; open Product Management and confirm prices/stock display correctly (money migration);
  start an order on a table and confirm it shows occupied, pay it, confirm the table empties;
  type something invalid into a dialog and confirm Enter doesn't submit it, Esc closes it, and
  an overly long name is rejected outright rather than accepted and truncated.

## Session 26 notes — file logging + product table stock column

Continued down the quality-of-life backlog after items 6/7.

- ✅ **File logging**: `util/AppLogging.install()` adds a rotating `FileHandler`
  (`~/.lunavera-coffee/logs/app-%u-%g.log`, 1MB x 3 files) to the root `java.util.logging`
  logger - on top of, not instead of, the console output every part of the app already uses, so
  nothing else needed to change. Called first thing in `HelloApplication.init()`, before
  `DatabaseConfig.initialize()`, so even a problem during DB setup/migration lands in the file.
  Falls back to console-only (never crashes startup) if the log directory can't be created.
- ✅ **Product table stock column**: was previously only visible by opening a product to edit it.
  Reuses `ProductDAO.LOW_STOCK_THRESHOLD` (already the dashboard widgets' own definition of
  "low") to bold/redden the cell when stock is at or below it - one shared definition of "low
  stock", not a second one invented for the table.
- **Verified for real**: compiled clean, fx:id/onAction cross-check clean, full 68-test suite
  still passes. Wrote `dev-checks/AppLoggingCheck.java` (installs logging, logs a message,
  confirms it's actually readable from the file on disk - not just that `install()` didn't
  throw) - passes. Re-ran `SessionGuardCheck`, `MoneyMigrationCheck`, and `MenuSeederCheck`
  (both phases) as regression checks on top of that - all clean.
- **Not run through `mvn javafx:run` by a human.** To test: launch the app, do a few things,
  check `~/.lunavera-coffee/logs/` for a log file with real content; open Product Management and
  confirm the new "Tồn kho" column shows stock, with low-stock items highlighted in red.

## Session 25 notes — money as INTEGER dong + schema versioning (items 6/7, together)

Did the one item explicitly saved for its own dedicated session, exactly as asked: started with
a backup plan, treated the migration itself with real-data verification rather than reasoning
about it, and caught a genuine bug doing so.

- ✅ **Schema versioning**: `PRAGMA user_version` (0 = original/pre-versioning schema, 1 =
  current). `DatabaseConfig.initialize()` now distinguishes a *brand-new* database (created this
  call - gets the current schema directly, version set immediately, no migration needed) from a
  *pre-existing* one (checked via "does the `users` table already exist", since `user_version`
  itself defaults to 0 for both cases and can't tell them apart on its own) - only the latter
  ever runs a migration. Future schema changes slot in as `if (version == N) { migrateNToNPlus1
  (...); version = N+1; }` steps in `MoneyMigration.runIfNeeded`, each small and independently
  testable, rather than one big jump.
- ✅ **Money as INTEGER dong**: `employees.salary`, `products.price`/`cost`,
  `orders.subtotal`/`discount`/`total`, `order_items.unit_price`/`line_total` are now `INTEGER`
  (whole dong), not `REAL`. Vietnamese dong has no fractional subunit in real use and every
  amount this app has ever written is already whole, so this is exact and lossless for
  legitimate data. New `util/Money` (`toDong`/`fromDong`/nullable variants) is the one place
  that converts at the JDBC boundary - `EmployeeDAO`, `ProductDAO`, `OrderDAO`, `ReportDAO`,
  `MenuSeeder` updated to use it instead of `setBigDecimal`/`getBigDecimal`. **The Java-level
  type is still `BigDecimal` everywhere else** (models, controllers, `LoyaltyPolicy`, the POS's
  own calculations) - this was a storage-layer change only, not a ripple through the app.
- ✅ **`config/MoneyMigration`**: for a pre-existing database, takes its own automatic timestamped
  backup first (`~/.lunavera-coffee/backups/pre-migration-v1-<timestamp>.db`, via the existing
  `VACUUM INTO`-based `backupTo`) - on top of, not instead of, the user's own manual "Sao lưu dữ
  liệu" backups - then recreates each of the four tables (rename old -> create new with INTEGER
  columns -> copy data with `CAST(ROUND(x) AS INTEGER)` as a safety net against stray
  floating-point noise, never because fractional dong are expected -> drop old), all in one
  transaction. A crash partway through rolls back completely - no half-migrated schema silently
  marked done.
- **Real bug caught and fixed by testing this against actual data, not just reading it twice:**
  first attempt set `PRAGMA foreign_keys = OFF` *after* `connection.setAutoCommit(false)` - SQLite
  only honors a change to that pragma when no transaction is already open, so it was silently a
  no-op, and the migration failed with a foreign-key constraint error on the very first table.
  Moved both `foreign_keys=OFF` and `legacy_alter_table=ON` before the transaction starts; fixed,
  then re-verified.
- **Verified for real, specifically the migration path** (the existing 68 tests all use brand-new
  temp databases, so none of them ever touch `MoneyMigration` at all - confirmed by the fact that
  the pragma-ordering bug above didn't make any of them fail): wrote
  `dev-checks/MoneyMigrationCheck.java`, which builds a database by hand with the *exact* old
  schema (REAL columns) and sample data - including a deliberately noisy float price
  (25000.00000000004) - then runs `DatabaseConfig.initialize()` against it and checks: schema
  version becomes 1; every row survives migration under its original id; the noisy price becomes
  exactly 25000; a `NULL` cost stays `NULL` (not coerced to 0); the automatic backup file exists;
  a second `initialize()` call in the same process is a safe no-op. **16/16 passed.** Also re-ran
  the full 68-test suite and `SessionGuardCheck`/`MenuSeederCheck` as regression checks (all
  clean) and the fx:id/onAction cross-check across all 10 FXML files (clean, though nothing here
  touched any FXML).
- **Not run through `mvn` by a human.** To test on a real pre-existing database: back it up
  manually first regardless (belt and suspenders), launch the updated app, and check Product
  Management/Reports/the receipt still show the right prices - then check
  `~/.lunavera-coffee/backups/` for the new automatic pre-migration copy.

## Session 24 notes — starter menu from menu.zip (one-time, deletion-safe)

Read the uploaded project state first (Session 23's order-history paging + doc correction,
confirmed via progress.md) - no code changes needed there, matched its own notes exactly.

Then: seeded the shop's real menu (33 items, 4 categories, real photos from "menu.zip") as
actual products - **the explicit design requirement was that this must never "come back" for a
shop that deletes some or all of it**, which rules out the obvious-looking "seed if the products
table is empty" approach (delete everything -> table's empty again -> that check fires again).

- ✅ `config/MenuSeedData` (the 33-item table: category, Vietnamese display name, bundled image
  file, a placeholder price) + `config/MenuSeeder` (the one-time import, called from
  `DatabaseConfig.initialize()`). Gated by a small `app_flags(name)` table - checked *before*
  anything else, and the flag row is written in the *same transaction* as the seeded
  categories/products, so a crash partway through leaves nothing seeded and no flag set either
  (retries cleanly next launch, never half-seeds and calls it done).
- ✅ Images bundled under `src/main/resources/seed-menu/` (33 files, ASCII-safe names - Vietnamese
  filenames with diacritics/spaces/em-dashes risk classpath-resource-loading encoding issues, so
  I transliterated the *file names* only; the real Vietnamese names are just DB text in
  `products.name`, which has no such restriction). Seeding copies each into the normal managed
  images directory via the existing `ImageStorage.directory()`/copy pattern - after seeding,
  there is nothing special about these products; they're edited/deleted exactly like any other.
- ⚠️ **Decision:** categories reuse an existing one by name if the shop already created it by
  hand (`findOrCreateCategory`), rather than risking a duplicate or failing on the UNIQUE
  constraint.
- ⚠️ **Decision (menu.zip had no prices):** placeholder prices, round numbers per category
  (25.000-52.000đ, roughly real Vietnamese cafe pricing) - documented in `MenuSeeder`'s javadoc
  as needing shop review. Starting stock is a flat 20/item for the same reason.
- **Verified for real, including the actual requirement, not just the happy path:** compiled
  clean; wrote `dev-checks/MenuSeederCheck.java` and ran it as **two genuinely separate `java`
  processes** against the same database (a single JVM can't test this - `DatabaseConfig`'s
  "already initialized" flag would just skip the second call and mask whether the *persistent*
  flag logic in `MenuSeeder` actually works). Phase 1: seed, confirm 33 products/4
  categories/every item has an image+price, delete "Cafe đen". Phase 2 (fresh process): confirm
  count stayed at 32 and "Cafe đen" is still gone - the exact scenario asked for. Also re-ran the
  full test suite (68 tests) and `SessionGuardCheck` (12/12) as regression checks - both clean.
- **Not run through `mvn javafx:run` by a human.** To test: fresh install (or delete
  `~/.lunavera-coffee/lunavera.db`), launch, open Product Management -> 33 products across 4
  categories with real photos. Delete a couple, quit the app fully, relaunch -> confirm they
  stay deleted. Prices/stock are placeholders - review before real use.

Per the user's explicit direction, **did not touch items 6/7** (money as `REAL`, no schema
versioning) - left for their own dedicated session, starting with a database backup.

## Session 23 notes — order-history paging, and catching up on undocumented work

**Verified Session 22 first, and found its own docs were behind its own code:** its notes said
"50 tests… UserDAO/OrderDAO left for next time", but `UserDAOTest` (7 tests) and `OrderDAOTest`
(7 tests, covering the highest-value gap - `insert()`'s stock+loyalty transaction including the
insufficient-stock rollback, and `cancelPaidOrder()`'s reversal) were already sitting in the
upload, real and passing - 64 tests, not 50. Read both files in full before trusting them (they
were solid), then corrected TODO.md/this file's own record before adding anything, rather than
redoing work that was already done or leaving the docs permanently wrong about it.

Then took the actual next item: **order-history paging beyond 500.** The old
`OrderDAO.findHistory(int limit)` loaded up to 500 rows into memory once and did every filter
(search/status/date-range) client-side over that fixed set - so a date range further back than
the most recent 500 orders silently showed nothing even when matching orders existed further
back, and the window never had a real page boundary. Replaced with `findHistoryPage(filter,
pageSize, offset)` + `countHistory(filter)`, both built from one shared `HistoryFilter` record
and a small `FilterSql` helper that turns it into the same WHERE clause + bind parameters for
both methods (so the count and the page can never silently drift apart into checking different
things). `OrderHistoryWindow` now shows 50 rows/page with prev/next controls, re-queries SQL on
every filter change (any change resets to page 1 - the old page number may not even exist in the
narrower result set), and fetches the count and the page together in one background `Async.run`
call rather than two separate ones (so a concurrent cancel landing between them can't produce a
page/count that don't agree). No more upper bound on total history size.

- **Verified for real, same approach as recent sessions**: recompiled the whole project (71
  classes now, +3 for `HistoryFilter`/`FilterSql`/the window's `HistoryPage`) against real
  JavaFX/sqlite/slf4j jars - clean. Added 4 new `OrderDAOTest` cases exercising the new SQL
  directly (page/count agreement and ordering, status filter excludes the other status, keyword
  matches by order id and by customer name, date range excludes a real order when the range is
  set to only "yesterday"), compiled and ran the **whole suite for real**: 68 tests, 0 failures
  (was 64; +4 here, matching what was actually added).
- **Not run through `mvn javafx:run`.** To test: open Lịch sử đơn hàng from the POS header,
  confirm paging controls appear once there are more than 50 orders, and that a date-range
  filter set far in the past still correctly shows nothing (or something, if real data exists
  there) rather than always showing nothing beyond a fixed row cap.

## Session 22 notes — reproduced real verification here + DAO tests

**This sandbox can do the same real verification the last two sessions did** - confirmed
`archive.ubuntu.com`/GitHub are reachable from here too. Before writing anything: recompiled
the whole merged project (`apt-get download` for JavaFX 11 + slf4j jars, sqlite-jdbc 3.46.1.3
from its GitHub release, `java -m jdk.compiler/com.sun.tools.javac.Main` since this sandbox's
JDK has no `javac` launcher but does have the module) - clean, same lone unchecked-cast note as
always. Ran the existing 34 tests for real (`org.junit.runner.JUnitCore`) - pass. Re-ran
`dev-checks/SessionGuardCheck.java` against a fresh temp DB - 12/12. So Session 21's own
verification checks out independently, not just by reading its notes.

Then took the top item off TODO.md's suggested batch:

- ✅ **DAO tests**: `CategoryDAOTest` (7), `CustomerDAOTest` (4), `ProductDAOTest` (5) - real
  tests against a real temp SQLite database, not mocks. `UserDAO`/`OrderDAO` left for next time
  (noted why in TODO.md).
- **The interesting part - a real testability constraint, not just "write some tests":**
  `DatabaseConfig.DB_URL` is `static final`, resolved once from `user.home` the moment the class
  loads. `dev-checks/SessionGuardCheck` sidesteps this by being its own `java` process per run
  (`-Duser.home` on the command line, before the JVM even starts). A JUnit suite can't do that -
  all test classes share one JVM - so if each DAO test class pointed `user.home` at its *own*
  temp dir, only whichever class happened to run first would actually take effect; the rest
  would silently share its database instead of getting one each. Added
  `dao/TestDatabaseSupport` (test-scope, package-private) as the one shared bootstrap every DAO
  test class calls from `@BeforeClass` - one temp directory, set up exactly once, for the whole
  run. Consequence: tests never assert on absolute table state (no `findAll().size() == 1`) -
  each test creates its own uniquely-named row(s) and asserts only about those.
- Added `CustomerDAO.findById` - didn't exist; the test needed it, and it's an obviously
  reasonable gap to close for a DAO that otherwise had every other single-record lookup pattern.
- **Verified for real**: compiled the 3 new test files against the just-recompiled main classes
  (0 errors), then ran all 9 test classes together - **50 tests, 0 failures** - then re-ran
  `SessionGuardCheck` again as a regression check on the `CustomerDAO` change (unrelated code
  path, but cheap to confirm) - still 12/12.
- **Not run through `mvn test` or `mvn javafx:run` by a human.** To run for real:
  `mvn test` should print `Tests run: 50, Failures: 0` (the JUnit 5 dependency already sitting
  in `pom.xml` from the original project scaffold is unused - all tests are JUnit 4 style; a
  harmless pre-existing oddity, not something this session touched).

## Session 21 notes — relative image paths, busy_timeout, and real automated tests

User confirmed Session 20's SessionGuard/item-9 tests all passed, so continued straight into
TODO.md's "suggested next batch": relative image paths, `busy_timeout`, then automated tests
(reordered slightly - did the two small fixes first since tests could then cover one of them).

- ✅ **Item 10a finished**: product images stored an absolute path (worked, but not portable -
  copying the database to another machine/user broke every image). New `util/ImageStorage`
  centralizes storage: `storeNewFile` now returns a bare file name to store on the product,
  `resolve` turns a stored value back into a real `File` (still understands an old absolute
  path already sitting in an existing database, so upgrading doesn't orphan anyone's pictures),
  `deleteQuietly` replaces the old copy of the same logic that lived in `AdminController`. The
  image cell factory, the picker, and product delete all go through it now instead of each
  having their own inline path handling.
- ✅ **Item 12 finished**: `PRAGMA busy_timeout = 5000` in `DatabaseConfig.getConnection()` -
  without it, two connections writing at once (e.g. the 30s `SessionGuard` background check
  landing mid-payment) got an immediate "database is locked" `SQLException` instead of one of
  them just waiting briefly.
- ✅ **Real automated tests, replacing the "no JUnit in the pom yet" gap.** Added JUnit 4 (test
  scope) to `pom.xml` - chosen over JUnit 5 for one dependency and zero test-engine setup,
  matching the project's "avoid unnecessary complexity" style; Surefire picks up `src/test/java`
  with no extra config needed. Wrote real tests for every pure-logic class that had none:
  `LoyaltyPolicyTest`, `PasswordUtilTest`, `ValidationUtilTest`, `CurrencyUtilTest`, `PagerTest`,
  and `ImageStorageTest` (new this session - uses a real temp directory via a `user.home`
  system-property swap in `@Before`/`@After`, not mocks). 34 tests total, each asserting a real
  behavior the app depends on (e.g. `PasswordUtilTest` checks hashing actually fails closed on a
  corrupted stored value rather than throwing; `PagerTest` checks a shrinking list pulls an
  out-of-range current page back in, the real "admin deletes rows while on page 3" scenario).
  `dev-checks/SessionGuardCheck.java` is kept separate rather than folded in: it needs a real
  SQLite database and is closer to an integration check than a unit test.
- **Verified for real, not just read - same approach as recent sessions, now covers tests too.**
  Compiled the whole project (68 classes now, +1 for `ImageStorage`) against real JavaFX 11 +
  sqlite-jdbc + slf4j jars pulled from `archive.ubuntu.com`/GitHub releases, exactly as before.
  This session additionally pulled a real `junit4` + `hamcrest` from the same Ubuntu mirror,
  compiled all 6 test classes against them, and **ran them with the real JUnit runner**:
  `java -cp <classes>:<jars> org.junit.runner.JUnitCore <test classes>` → all 34 pass. Re-ran
  the existing `SessionGuardCheck` against the freshly recompiled classes too, to make sure the
  `busy_timeout`/`ImageStorage` changes hadn't broken anything it covers - still 12/12. This is
  the strongest verification so far in this project (real compilation + real test execution
  against real dependencies), but it is still not `mvn javafx:run` - nothing GUI-side runs here,
  and this was all done with JavaFX 11 jars rather than the project's real 21 (all that's
  available via `apt-get download` without a login) - a language/API difference at that gap is
  possible even though the code compiled clean.
- **Not run through `mvn javafx:run` or `mvn test` by a human yet.** To test: add a product
  image, close and reopen the dialog to confirm it still shows, then check the file sitting in
  `~/.lunavera-coffee/images/` is named like `<timestamp>_<original name>.png` rather than a
  full path; `mvn test` should print `Tests run: 34, Failures: 0` (plus whatever count Maven
  reports for its own default-generated tests, if any).

## Session 20 notes — merged a second parallel audit branch, upgraded item 4, closed item 9

**Reconciliation:** the upload this session started from was a different, more advanced branch
than the one I'd been working on — it already had TODO items 1 (admin lock-out guard), 2 (atomic
stock check in the order transaction), 3 (`DefaultPasswordPrompt`), 5 (dashboard excludes
cancelled orders), 8 (duplicate-customer phone check) and 10a (image cleanup on replace/delete)
done, none of which my branch had. My branch's own item 4 was a fuller `SessionGuard` (background
watcher, payment-time and admin-action checks, role refresh) versus this branch's simpler
"checked on next navigation only" version, and my branch had item 9 (hidden the dead-end
register link) which this one didn't. Adopted this upload as the base and layered my two pieces
on top, rather than redoing either branch's independent work:

- **Item 4 upgraded**: replaced `SceneNavigator`'s inline `isCurrentAccountActive()` check with
  `util/SessionGuard`, which does the same navigation-time check plus a 30s background
  `Timeline`, a check before `OrderController.handlePayment` opens the payment dialog (a locked
  account can't complete a sale mid-cart), a check inside `AdminController.requireAdmin()`, and
  refreshes `Session`'s role on every check (a promotion/demotion takes effect without
  re-login). Added `UserDAO.findById`; `Session`'s two fields are now `volatile` (read by the
  background check off the FX thread).
- **Item 9 closed**: hid the register prompt on `dangnhap.fxml` the same reversible way
  (`visible="false" managed="false"`, `dangky.fxml`/`RegisterController` untouched) - this
  branch's own `onLoginSuccess` already declined CUSTOMER logins, so the link was exactly the
  dead end the other branch's note described.
- **Verified for real, not just read**: `archive.ubuntu.com` is reachable from this sandbox, so
  `apt-get download openjfx libopenjfx-java libopenjfx-jni libslf4j-java` + `dpkg -x` gives real
  JavaFX (11 - older than the project's 21, but enough to type-check) and slf4j jars, sqlite-jdbc
  3.46.1.3 comes from its GitHub release page, and the runtime already contains the compiler
  module, so `java -m jdk.compiler/com.sun.tools.javac.Main` compiles the **whole project as it
  now stands** (67 classes, clean apart from the pre-existing unchecked-cast note in
  `AdminController`). `dev-checks/SessionGuardCheck.java` then runs the real merged `SessionGuard`
  + `UserDAO` against a temp SQLite DB (`-Duser.home=<empty dir>`, never touches
  `~/.lunavera-coffee`): 12/12 pass, including promote/demote refreshing the session role and a
  deleted ("ghost id") account being rejected. This does not replace `mvn javafx:run` - nothing
  GUI-side (the alert, the redirect, the 30s timer, the POS cart being discarded) runs headless.
- **Not run through `mvn javafx:run`.** To test: log in as an employee, have an admin lock that
  account in Quản lý tài khoản — within ~30s, or on the employee's next click/attempted payment,
  they should see the message and land back on login. Promote/demote a logged-in account and
  confirm admin-only buttons follow on the next click, no re-login needed. Confirm the login
  screen no longer shows the register row.

## Session 19 notes — third batch of audit fixes (see `TODO.md`)

- ✅ **Locked users caught centrally:** `SceneNavigator.switchScene` now re-checks the logged-in
  account's status (one indexed `UserDAO` lookup) before loading any screen except the login
  screen itself; if it's no longer ACTIVE, clears the session, shows a message, and loads the
  login screen instead of the requested one. Covers navigation from any sidebar button/logout on
  every screen (all of them route through this one method) - the one gap is someone sitting idle
  on a single screen without clicking anything, which is unchanged and accepted as low-risk.
- ✅ **Orphaned product images:** picking a new image in the edit dialog now deletes the old file
  after the new one copies successfully; hard-deleting a product now deletes its image file too.
  Both go through a new `deleteImageFileQuietly` helper that never throws - a missing/locked file
  shouldn't block saving or deleting the product record itself.
- ✅ **Order history date filter:** `OrderHistoryWindow` gained from/to `DatePicker`s + a clear
  button, filtering the already-loaded rows by `order_date`'s calendar day (inclusive both ends).
  No DAO change - still the same 500-row cap, so a date range entirely outside those 500 shows
  nothing even if older matching orders exist. Proper date-range paging is still a TODO.
- **Not run through `mvn`.** To test: (a) lock an account (as admin, from another account) then
  try any button on that locked user's currently-open screen -> bounced to login with a message.
  (b) Edit a product's image twice in a row, then check `~/.lunavera-coffee/images/` only has the
  latest file; delete that product and confirm the file is gone too. (c) In order history, set a
  date range that includes/excludes known orders and confirm the count/rows update; clear it.

## Session 18 notes — second batch of audit fixes (see `TODO.md`)

- ✅ **Default password prompt:** logging in with the seeded `Admin@123` now opens a "change
  password" dialog straight after the dashboard appears (`DefaultPasswordPrompt`); it validates
  strength/match and refuses the default itself. "Để sau" is allowed, and it returns on every such
  login. Detection uses the password typed at login (no extra hashing).
- ✅ **Open-cart guard:** leaving the POS via any sidebar button/logout, or closing the window,
  asks first if the cart has items; "yes" clears the cart so the prompt doesn't repeat. The close
  handler is removed when leaving so it can't fire on other screens.
- ✅ **Duplicate phone:** `CustomerDAO.existsByPhone`; checked in the customer dialog (excluding the
  customer being edited) and in self-registration. No unique DB index on purpose.
- ✅ **Backup:** "⤓ Sao lưu dữ liệu" on the admin dashboard writes a consistent copy of the database
  via `VACUUM INTO` (`DatabaseConfig.backupTo`), admin-only. Restoring is manual (documented in the
  success message).
- **Not run through `mvn`.** To test: (a) fresh DB or reset admin password, log in as `admin` /
  `Admin@123` -> prompt appears; weak/mismatched/default passwords are refused; after changing,
  log out/in with the new one and no prompt. (b) POS: add items, click a sidebar button -> confirm;
  "Không" stays, "Có" leaves; also try the window's X. (c) Add two customers with the same phone
  -> second refused; edit a customer keeping its own phone -> allowed; register with a used phone ->
  refused. (d) Backup: save a .db, open it with any SQLite viewer and check the tables/rows.

## Session 17 notes — first batch of audit fixes (see `TODO.md`)

Created `TODO.md` from the Session 16 audit and started on it (checked items there):
- ✅ Last-admin guard (role change / deactivate in the account dialog, and lock button).
- ✅ Negative stock: `OrderDAO.insert` now does `UPDATE products SET stock = stock - ? WHERE id = ?
  AND stock >= ?` for every line inside the order's transaction and fails the whole sale with a
  readable message if a line can't be covered. `OrderController` no longer calls `adjustStock` after
  the insert (that two-step flow could save an order and crash before deducting stock); on a failed
  payment it reloads products. `ProductDAO.adjustStock` is still used elsewhere - unchanged.
- ✅ Dashboard "orders today"/"customers today" exclude cancelled orders.
- ✅ Enter submits login/register; confirm dialogs say Có/Không; receipt caption "Nhân viên:".
- **Not run through `mvn`.** To test: (a) as the only admin try changing your role to Employee and
  unchecking active -> both refused; with a second admin it's allowed. (b) Put 3 of a product in the
  cart, then in Product Management set its stock to 1 and pay -> clear "không đủ tồn kho" error, no
  order saved, stock unchanged; a normal sale still deducts stock. (c) Cancel an order and confirm
  "orders today" drops. (d) Press Enter on login; open any delete confirmation and check Có/Không.

## Session 16 notes — read-only audit (no code changed)

User confirmed all six Session 15 report tests pass, and asked for a scan for bugs/quality-of-life
items *without changing the project*. Only this note was added. Findings (verified by reading
the code unless marked "unverified"), most important first:

### Likely bugs / risks
1. **Admin lock-out (real):** the account dialog lets an admin change *their own* role to
   Employee, and locking is only blocked for one's own account - nothing stops demoting/locking
   the **last remaining admin**. Fix: refuse role change/lock/deactivate when it would leave zero
   active admins (`UserDAO.countByRole` already exists).
2. **Stock can go negative:** the POS checks stock only when adding/incrementing a cart line
   against the list loaded at screen open; `ProductDAO.adjustStock` is a bare
   `stock = stock + ?` with no floor, and `OrderDAO.insert` doesn't re-check. If stock was edited
   elsewhere after the POS loaded, a sale can drive it below 0 (the brief says "never silently
   allow negative"). Fix: re-check/decrement inside the order transaction
   (`UPDATE ... SET stock = stock - ? WHERE id = ? AND stock >= ?`, fail the sale if 0 rows).
3. **Default `admin` / `Admin@123` never forced to change** - password change works now
   (Session 11) but nothing prompts it on first login.
4. **Locked/deactivated users stay logged in** until they log out (status is only checked at
   login). Low risk on a single-till desktop app.
5. **Dashboard "orders today" counts cancelled and open orders too** (`countToday()` has no
   status filter) while revenue counts PAID only - the two numbers can disagree.
6. **Money stored as `REAL`** (price, total, ...). Fine for whole-dong amounts today, but sums of
   REAL can carry float noise; long-term migrate to integer dong or TEXT decimals.
7. **No schema versioning** (`PRAGMA user_version`): any future column change has no migration
   path for the existing `~/.lunavera-coffee/lunavera.db`.
8. **Duplicate customers:** no phone-uniqueness check in Customer add/edit or self-registration.
9. **Self-registered CUSTOMER accounts are a dead end:** they can register but login only shows
   "not supported yet". Either hide the register link, or build a customer screen.
10. **Product image files are copied to `~/.lunavera-coffee/images/` and never deleted**
    (orphans after edit/delete), and the *absolute* path is stored, so moving the data folder
    breaks images.
11. **Open cart is lost silently** if the window is closed (no close-request guard) or on
    logout - the brief lists "application closing during an unfinished order".
12. Unverified: `busy_timeout` isn't set explicitly; async DB threads + FX-thread writes could in
    theory hit "database is locked" (sqlite-jdbc's default timeout probably hides it).

### Quality of life
- **Enter key doesn't log in** (password field has no `onAction`); same for the register form.
- Confirm dialogs use `ButtonType.YES/NO` -> shows "Yes/No" on an English OS, mixed with
  Vietnamese text. Use custom "Có"/"Không" buttons in `AlertUtil.confirm`.
- Register screen's show/hide-password buttons only pop an "unsupported" dialog; login has none.
  Implement real toggle (TextField bound to the PasswordField).
- **Receipt caption bug (confirmed):** `donthanhtoan.fxml` captions that row "Bàn:" but
  `HoaDonController.setInvoiceData` fills it with the *employee name* - the receipt reads
  "Bàn: <employee>". Change the caption to "Nhân viên:" (one-word FXML fix), or pass the real
  table number if it should stay.
- Order history is capped at 500 newest with no date filter; add date range + paging.
- No **backup/restore** of the SQLite file; a "Sao lưu dữ liệu" button (copy the .db) is cheap.
- No file logging (only console) - hard to debug a shop's machine after the fact.
- Product/customer tables: no column sorting affordances or page-size choice; stock column
  missing from the product table (low stock only visible on the dashboard).
- Dialogs: no Enter-to-save / Esc-to-cancel hints; long text isn't length-limited.
- **No automated tests at all** (brief section 25). Cheapest wins: unit tests for
  `LoyaltyPolicy`, `PasswordUtil`, `ValidationUtil`, and DAO tests on a temp SQLite file.
- Still-open optional features: forgot-password, receipt QR code, real table/seating.

Suggested order if time is short: 1 (lock-out), 2 (negative stock), Enter-to-login, Yes/No
labels, 5 (orders-today), 11 (cart guard), then backup button.

## Session 15 notes — admin sales report (with CSV export)

User confirmed all four Session 14 tests pass (history window, receipt re-open, admin cancel
with stock/points/dashboard updates, employee's cancel button disabled).

Next gap against the original brief ("view sales/statistics", "daily sales, reports", "popular
products"): the dashboards only show today + a 7-day chart, with no way to look at any other
period or see best sellers. Added:

- ✅ **"▤ Báo cáo doanh thu" button** on the admin dashboard (`admin-trangchu.fxml`,
  `AdminController.handleShowReports`) opening `ReportWindow` (admin-only; re-checked in code,
  not just by only putting the button on the admin dashboard). Pick a from/to date (defaults:
  1st of this month -> today) and see: paid orders, revenue, average order value, discounts
  given, cancelled orders; **best sellers** (top 20 by units, with revenue); **revenue per day**.
- ✅ **"Xuất CSV"** exports exactly what's on screen (the last loaded range, not whatever the
  pickers were edited to since) as UTF-8 *with BOM* so Excel shows the Vietnamese text
  correctly.
- New: `dao/ReportDAO` (read-only aggregates, three queries, records for results),
  `org.example.controller.ReportWindow`.
- ⚠️ **Decision:** only PAID orders count as sales; a cancelled order (Session 14) is a
  refunded sale, so it's excluded from revenue/best sellers and only counted separately under
  "Đơn đã hủy". Its date is the original sale date (`paid_at`), not the cancellation date -
  there's no cancelled-at column.
- ⚠️ **Decision:** best sellers group by the product name *snapshotted on the order item*, so
  a renamed product's old sales stay under its old name (matches how invoices already work).
- Also fixed (same "never bound to the session" gap as Sessions 1/10): both dashboards' header
  still literally said "Chào mừng, Admin!"/"Employee" whoever was logged in - now shows the
  real display name (`dashboardGreetingLabel`, `dashboardAccountNameLabel`).
- **Verified statically** (all 10 FXML vs their controllers, balance, duplicate fields); **not
  run through `mvn`**. To test: pay a few orders on different products (ideally some with a
  discount), open the report on the admin dashboard: totals should equal the sum of the paid
  orders in Lịch sử đơn hàng, best sellers should match what you sold, cancelling one order
  (Session 14) and pressing "Xem báo cáo" again should drop it from revenue and bump "Đơn đã
  hủy"; export the CSV and open it in Excel; try from > to (should warn, not crash); log in as
  an employee - there is no report button on their dashboard.

## Session 14 notes — order/invoice history + cancel (refund) order

Read the merged upload first (Sessions 12–13 were built in parallel chats): re-ran the full
fx:id/onAction cross-check of all 10 FXML files against their own `fx:controller`, brace/paren
balance on every Java file, duplicate-field check, and read the Session 13 dashboard code
(`DashboardWidgets`, `findLowStock`, `findRecent`, `DashboardStats`). Clean - only decorative
layout ids are unmatched, same as before.

Then closed a real gap against the original brief ("see order in order history", "invoice
history", "cancelling an order"): nothing could list past orders, re-open an old receipt, or
cancel a sale. Added:

- ✅ **"▤ Lịch sử đơn hàng" button** in the POS screen header (`quanlydonhang.fxml`,
  `OrderController.handleShowOrderHistory`) opening `OrderHistoryWindow`: newest 500 orders
  with time, employee, customer ("Khách lẻ" if none), payment method, total, status; search by
  order id/employee/customer; status filter; **"Xem hóa đơn"** (or double-click) re-opens the
  original receipt via the existing `HoaDonController` with the *order's own* employee and
  customer names (not the current login's).
- ✅ **Cancel (refund) a paid order - admin only** (button disabled for employees, and
  re-checked in code): `OrderDAO.cancelPaidOrder()` runs in one transaction - puts stock back,
  takes back the loyalty points that order earned (never below 0), sets status `CANCELLED`
  (dashboard revenue already counts only PAID, and recent-orders already flags "Đã hủy"). The
  order and its items are kept, so history stays intact. Refuses anything not currently PAID.
- ⚠️ **Decision:** the history is a plain *non-modal* window built in code, not a new FXML
  screen - it needs no sidebar, and non-modal avoids the Linux dialog-modality trouble from
  Sessions 4-5. Its only prompts (confirm/errors) go through `AlertUtil` like everywhere else.
- ⚠️ **Decision:** cancel = full refund of the whole order (no partial refunds, no editing a
  paid order). Cancelling doesn't reverse a customer's *tier* separately - the tier is derived
  from points, so it follows automatically.
- New: `OrderDAO.findHistory(limit)` + `OrderDAO.OrderSummary` (single JOIN, no per-row lookups),
  `OrderDAO.cancelPaidOrder(id)`, `org.example.controller.OrderHistoryWindow`.
- **Not run through `mvn`** (no build tools in the authoring sandbox). To test: pay 2 orders
  (one with a customer attached, e.g. 120.000đ = +12 points, stock -N); open Lịch sử đơn hàng;
  double-click an order and check the receipt matches; as admin cancel the customer order and
  confirm: status "Đã hủy", stock restored in Product Management, points back down by 12,
  dashboard revenue/recent orders updated; log in as an employee and confirm the cancel
  button stays disabled.

## Session 13 notes — removed the last fake data from both dashboards

User confirmed Session 12's three test items all work (dialogs stay open with input intact,
password change + re-login, loyalty points/tier after a paid order with a customer).

The original brief says dashboards must show only real data ("do not invent fake statistics"),
but both dashboards still carried mockup content from Session 1: a hardcoded 12-table occupancy
grid (with made-up "Có khách" tables while the stat card above it said 0), and four hardcoded
low-stock rows ("Cà phê Arabica – còn 3/10" etc.) that had nothing to do with the real product
table. Replaced with real data, same look (existing `inventory-*` CSS classes reused, no CSS
edits), on both `admin-trangchu.fxml` and `employee-trangchu.fxml`:

- ✅ **Low-stock card** - real active products at or below `ProductDAO.LOW_STOCK_THRESHOLD`
  (10), lowest first, max 5 rows, with "Hết hàng"/"Sắp hết"(≤5)/"Thấp" badges and an honest
  empty state ("Tất cả sản phẩm đều đủ hàng"). ⚠️ One shop-wide threshold: there is no
  per-product minimum-stock column, and adding one wasn't worth a schema migration yet. Its
  "Xem kho →" button now reads "Xem sản phẩm →" and opens Product Management (where stock is
  actually edited) instead of the "not built" info dialog.
- ✅ **"Trạng thái bàn" card → "Đơn hàng gần đây"** - the 5 newest orders (id, time, payment
  method, total; "Đã hủy" flagged). Real table occupancy can't be reported truthfully (no
  table entity, and orders are paid immediately at the POS, never left open), so rather than
  keep faking it the card now shows something real. This also gives the dashboard the "recent
  orders" item the original brief listed.
- ✅ **"TỔNG SỐ BÀN" stat card → "SẢN PHẨM"** - real product count (`ProductDAO.countAll()`,
  includes inactive ones) with a "Cần nhập thêm: N sản phẩm" note from the same low-stock query.
- New: `ProductDAO.findLowStock()/countLowStock()`, `OrderDAO.findRecent()`, three new fields
  on `DashboardStats`, and `util/DashboardWidgets` (builds the rows once; both dashboard
  controllers call it instead of duplicating UI code). fx:ids renamed to match their new
  meaning (`dashboardProductCountLabel`, `dashboardLowStockNoteLabel`, `recentOrdersNoteLabel`,
  `recentOrdersList`, `lowStockList`) - the old `table*` names would have been actively
  misleading. `tableGrid` on the POS screen is unchanged: that one is deliberately decorative
  table *selection*, not a claim about occupancy.
- **Verified statically**: fx:id/onAction cross-check of every FXML against its own
  `fx:controller` (clean for everything touched; the only unmatched ids project-wide are
  unreferenced decorative layout nodes on the login/register/account screens, untouched),
  brace/paren balance and duplicate-field checks on all controllers. **Not run through
  `mvn javafx:run`.** To test: with a fresh DB the dashboard should say "Chưa có đơn" and list
  low stock honestly; set a product's stock to 3 in Product Management and see it appear with
  "Sắp hết"; pay an order and see it appear under recent orders on both dashboards.

## Session 12 notes — fixed the "dialogs lose typed input" bug everywhere it existed

Verified Sessions 9–11's work first (per the recurring pattern of checking before building):
read the new `CustomerController`, `LoyaltyPolicy`, and the `OrderDAO`/`UserDAO`/`CustomerDAO`
diffs in full, confirmed the loyalty-points update happens inside the same transaction/
connection as the order insert (so it can't be granted for a save that fails), confirmed the
account edit dialog validates the new password *before* writing anything else. All clean.

Then did the concrete item Session 11 flagged as "worth doing next": the event-filter fix for
the known dialog limitation, applied to all five dialogs that had it - category, account,
product (`AdminController`), customer (`CustomerController`), and the POS payment dialog
(`OrderController`, found while doing this - not explicitly called out in the Backlog list, but
same `setResultConverter` pattern, and arguably the worst case of the five to get wrong: it's
the one asking for a *typed number* right before completing a sale, not just a re-selectable
combo box).

Also fixed two stale sections further down this file (the "Architecture notes" and "Next
recommended step" - both still described the pre-Session-7/10 state: said `AdminController`
was shared by 6 screens when it's 4, and listed the Session 11 items as still-to-do when they
were already done).

**Not yet run through `mvn javafx:run`** - please test per the updated "Next recommended step"
below, in particular: type something invalid into each of the five dialogs and confirm it now
stays open with your input intact (previously: category/account/product/customer dialogs and
the POS payment dialog would all silently close and discard everything typed on any validation
failure).

## Session 11 notes — backlog polish + two real gaps found

User confirmed Customer Management (add/search by name and phone/edit) works. Worked the
Backlog list:

- ✅ `reloadCategories()` / `reloadAccounts()` now load via `Async.run(...)` like every other
  screen (previously the last two synchronous DB loads on the FX thread).
- ✅ Account dialog's role dropdown now shows Vietnamese labels ("Quản trị viên"/"Nhân viên")
  via a `StringConverter` instead of raw enum names.
- ⚠️→✅ **Real bug found:** the edit-account dialog's password field said "leave blank to keep
  current password", implying typing one changes it - but the edit path ignored the field
  entirely, so no password could ever be changed (including the default `admin` / `Admin@123`
  seeded account, which Session 1 said to change "after first login" with no way to do so).
  Fixed: added `UserDAO.updatePasswordHash()`; edit now validates (same rule as creation,
  checked *before* any other field is saved so a bad password can't cause a partial save) and
  hashes/stores a new password when the field is non-blank.
- ⚠️→✅ **Real gap found:** nothing ever awarded loyalty points, so the Customer screen's
  "Hạng thành viên" tier column could never change from "Thành viên mới". Added
  `service/LoyaltyPolicy` (1 point per full 10.000đ of the final total - one place to change
  the rule) and `OrderDAO.insert()` now awards points inside the same transaction as the order
  for PAID orders with an attached customer, so points can't be granted for an order that
  failed to save.
- ⬜ Not done, deliberately: `LoginController.onLoginSuccess()`'s single sync
  `employeeDAO.findById` (trivial), forgot-password, table/seating, QR code (all out of scope
  unless requested).
- ⚠️ **Known limitation noticed, not fixed:** all `Dialog` forms (category/account/product/
  customer) use `setResultConverter` + `showAndWait()`, so a validation failure shows its
  warning and then the dialog closes, losing what was typed. Proper fix is an event filter on
  the Save button that consumes the event on invalid input (recipe is in the Session 6 "Step 0"
  notes). Worth doing next, across all four dialogs at once.
- **Verified statically** (brace balance, fx:id/onAction bindings, duplicate fields); not run
  through `mvn`. To test: pay an order with a customer attached (e.g. a 120.000đ order = +12
  points), check the points on the Customer screen; reach 100 points to see "Bạc"; edit an
  account, type a new password, log out and log in with it.

## Session 10 notes — Customer Management built (Step 3 done)

User confirmed the POS flow (Session 7/9) works end-to-end. Built Step 3 per the plan.

- ✅ `quanlykhachhang.fxml` had a fully laid-out table (7 columns, including "Điểm tích lũy"
  and "Hạng thành viên") and search/add UI already sitting there unwired since Session 1 — just
  needed `fx:id`s added, not built from scratch. Also gave the header's "Admin" labels real
  `fx:id`s and bound them to `Session` (every other screen already did this; this one was
  missed originally).
- ⚠️ **Decision: own `CustomerController`, not a 7th `AdminController` screen** — same
  reasoning as `OrderController` (see its javadoc): fresh screen, no reason to add collision
  risk to an already-large shared class. This is the third occurrence of the ~8-method
  navigation-duplication tradeoff (Admin→Order→Customer) — noted in the new controller's
  javadoc as the point to extract a shared base class if a fourth screen ever needs splitting
  out.
- ✅ Real CRUD via `CustomerDAO`: search by name/phone, add, edit. Added `CustomerDAO.update()`
  (didn't exist before — only `insert`/`findAll`/`countAll` did).
- ⚠️ **Decision: no delete, no order-history view.** A customer may be referenced by past
  orders (`orders.customer_id`) or a self-registered login account — safely handling either
  is bigger than this screen needs to unblock, so it's left out rather than half-built. Order
  history would need a new `OrderDAO` query that doesn't exist yet.
- ⚠️ **Decision: "Hạng thành viên" (membership tier) is a computed display value, not a
  stored field** — simple thresholds on real `loyaltyPoints` (≥500 Vàng, ≥100 Bạc, else
  "Thành viên mới"), transparent and derived, never fabricated. Not configurable by the shop
  yet; revisit if that's wanted.
- **Verified**: brace/paren balance, fx:id/onAction cross-check on the new controller+FXML
  pair, and — since this changed which controller a shared FXML points to — re-ran the same
  cross-check across **all 10 FXML files against their own actual `fx:controller`** project-
  wide. Zero mismatches anywhere.
- **Not yet run through `mvn javafx:run`** — please build/test: add a customer, search by
  partial name and by phone, edit one, and confirm the loyalty tier column updates correctly
  at the 100/500 point boundaries.

**With this, all three Step 1–3 features from the Session 6 plan are built.** Per the original
task brief, the project is now functionally at "complete workflow" status: login → role-based
dashboard → manage categories/products/accounts/customers → run a full POS sale → receipt.
Remaining work is entirely in the "Backlog" section further down this file (polish items) plus
whatever testing this session's Customer Management surfaces.

## Session 9 notes — verified Session 7's Order/POS build, no bugs found

Asked to build 25–50% of Step 2 (Order Management/POS). On reading the uploaded state first
(as instructed), found Session 7 had already built it in full — a complete, working POS flow
in a new dedicated `OrderController` (table select → start order → search/add to cart →
adjust quantities → attach customer → pay with discount/change calc → persist via `OrderDAO` →
decrement stock → open the receipt). Rather than duplicate that work blind, spent this session
verifying it thoroughly instead — same standard as every prior session, just applied to code I
didn't write myself:

- Read `OrderController.java` in full (635 lines) and the rewritten `quanlydonhang.fxml` (569
  lines). The logic holds up: stock is checked against the DB-loaded snapshot (not decremented
  in-memory until actual payment, which is correct), the payment dialog's discount/cash/change
  validation is complete, `Async.run(...)` is used correctly for the save+stock-decrement, and
  the "print invoice" honesty check (refuses to fabricate a receipt for an unpaid order) works
  as described.
- Ran the full fx:id/onAction cross-check — this time against **every FXML file matched to its
  own declared `fx:controller`** (all 10 screens across 6 controller classes: AdminController,
  OrderController, EmployeeController, LoginController, RegisterController, HoaDonController),
  not just the one new screen. **Zero mismatches anywhere** — this also re-confirms the
  Session 8 Product Management reconciliation didn't regress anything on the other 5
  `AdminController` screens.
- Checked brace/paren balance and duplicate field/method names on `AdminController.java`
  (now 1216 lines, most churned-on file across all the parallel sessions) and
  `OrderController.java` — both clean.
- Spot-checked the specific bug fix Session 8 described (`applyCategoryFilter()`'s null guard
  for `categoryTable`) — present and correctly reasoned, matches its own description exactly.
- Confirmed the two small model additions (`Customer.toString()`, `Product.toString()`, for
  `ChoiceDialog` display) are clean and appropriately documented as UI-display-only.

**No code changes this session** — genuinely nothing to fix. **Still not run through
`mvn javafx:run` by a human** (per Session 7's own note) — this is the one thing my own
verification can't cover: static cross-checking catches wiring bugs (wrong method called, null
field) but not runtime logic bugs that only surface by actually clicking through a stateful
multi-step flow like this one. Please test the actual POS screen next — pick a table, add a
few products, try adjusting quantities up past available stock, attach a customer, pay with
cash (try entering less than the total first, then enough), confirm the receipt window opens
and shows correct data, and confirm stock actually decreased in Product Management afterward.

## Session 8 notes — reconciling a second parallel Product Management implementation

**Housekeeping again, and this is going to keep happening across separate chats/machines:**
the zip uploaded to start this session was, once again, missing work already delivered - this
time it was missing all of Session 7's Order Management, and it turned out to contain a
*different, independently-written* Product Management implementation (also labeled "Session 7"
in its own progress.md - numbering collides with the entry below because two separate sessions
each picked up from the same Session 6 handoff without knowing about each other).

That independent implementation was **actually better than mine** in real ways, and - this
matters more than anything I can verify myself - **was confirmed working by the user against
the real `mvn javafx:run` build, including clicking through add/edit/delete and the image
picker.** Rather than keep my own unverified version out of pride, adopted theirs as the new
base:
- Real image picker (`FileChooser` → copies into `~/.lunavera-coffee/images/`) - I'd deferred
  this; they built it, including the table's image column actually loading it.
- Real guarded hard-delete (`ProductDAO.countOrderReferences()` blocks deleting a product with
  order history and offers to deactivate instead) rather than my always-deactivate-only choice.
- Product loads via `Async.run(...)` from the start.
- Category quick-tabs filter by substring-matching the real category name rather than my exact
  name-match approach - more forgiving, same "no fake counts" principle.

**One real bug found in it before adopting:** its `handleAddCategory()`/`openCategoryDialog()`
is shared as-is between the real Category screen and the Product screen's own "+ Thêm danh mục"
shortcut (same fx:id, same handler - a deliberate simplification vs. my earlier callback-based
split). `applyCategoryFilter()` touches `categoryTable` with no null guard, and `categoryTable`
is null when this runs from the Product screen (fresh `AdminController` instance per screen,
only that screen's own fields populated) - so using that shortcut from the Product screen would
NPE right after successfully saving the category to the database. Fixed with a 2-line null
guard in `applyCategoryFilter()`, plus made `reloadCategories()` also refresh the Product
screen's own category list when it's the one present, so the new category actually shows up in
the product dialog's dropdown without needing a full screen reload either way.

Also re-applied Session 7's Order Management work on top of this new base (removed the same
dead order-stub methods again, `OrderController.java`/`quanlydonhang.fxml` were untouched by
this upload so needed no changes) and re-ran the full fx:id/onAction cross-check across every
FXML file against both controllers - clean.

**Given how often re-uploads have landed a step behind actual progress:** please try to always
grab the *most recent* zip this chat has produced before starting a new local session, rather
than one from partway through - it's the difference between this session finding one real bug
in otherwise-solid new work vs. potentially losing work outright.

## Session 7 notes — Order Management / POS built, plus a note on this session's starting point

**Housekeeping first:** the zip uploaded to start this session was a step *behind* what Session
6 had already delivered (it was missing all of the Product Management work - same Sessions 1-5
code otherwise, byte-for-byte). Rather than reverting Session 6's work, this session continued
from the more advanced state (nothing else was lost - the diff was exactly the 3 files Session 6
touched). The uploaded zip's own "Session 6" notes described a review pass with a detailed
forward plan for Product/Order/Customer management; useful ideas from it were folded into this
session's decisions below and the Backlog section, but its Step 0 (a big dialog-modality
refactor) was skipped since the user had separately already said to deprioritize that - written
without visibility into that conversation.

**Built**: `quanlydonhang.fxml` (the dine-in table grid + live cart + checkout screen) is now a
real, working POS flow: pick a table → start an order → search/add products to a cart → adjust
quantities → attach an optional registered customer → pay (cash with change calc, or card) →
order + items persist via `OrderDAO` (transactional) → stock decrements via
`ProductDAO.adjustStock` → the receipt window opens automatically via the already-built (and
previously unreachable) `HoaDonController`.

⚠️ **Architecture decision: gave this screen its own `OrderController`, not a 7th screen on
`AdminController`.** That class was ~1300 lines across 6 screens after Session 6 and had
already needed 5 collision renames to add the 6th - this was the natural point to stop. The
FXML's `fx:controller` now points to the new class; the old inert stub handlers for this screen
were removed from `AdminController` rather than left as dead code. Trade-off taken knowingly:
`OrderController` duplicates ~9 one-line navigation methods (open each screen, logout) instead
of sharing them via a base class - a small, low-risk amount of duplication for now. If Customer
management (next) also needs splitting out, that's the point to extract a shared base class for
these (three duplicates is the usual signal to stop duplicating).

⚠️ **Decision: dine-in tables stay decorative, exactly as before.** No table/seating entity
exists in the schema (unchanged from Session 1). Clicking one of the 16 table buttons only sets
local, in-memory UI state (which button is highlighted, the "Bàn N" label) - it's never
persisted and never blocks starting an order. The real thing this screen builds and saves is
the order/cart itself.

⚠️ **Decision: no discount/cash-received fields exist anywhere in the FXML**, so payment
(method choice, an optional flat-amount discount, cash received + live change calculation, all
validated - discount can't exceed the subtotal, cash received can't be less than the total) all
live in a new dialog built the same way the add/edit-category and add/edit-product dialogs are:
programmatically, via `AlertUtil.configure(dialog)`. This keeps the main screen's existing
layout untouched.

⚠️ **Decision: the quantity column doubles as the remove control.** `orderDetailTable` has no
action/remove column in the FXML (only STT/name/qty/price/subtotal) and the qty column is only
60px wide. Rather than restructuring the table, the qty column renders inline "[-] N [+]"
buttons; decrementing past 1 removes the line - a common POS pattern, and avoids needing a
column the design doesn't have room for.

⚠️ **Decision: cart lines are plain `OrderItem`s, not a new "CartLine" model.** `OrderItem`
already snapshots product name + price for exactly this reason (see its own doc comment); reused
directly with `id`/`orderId` left unset until `OrderDAO.insert(...)` actually persists them, per
the plan's own suggestion.

**Other things actually implemented, not just stubbed**: stock is checked (against the
in-memory product list, refreshed after every payment) both when adding a new line and when
incrementing an existing one - never allows a cart quantity to exceed available stock. Product
search that matches more than one active product opens a small `ChoiceDialog` rather than
guessing. "Chỉnh sửa" attaches/detaches a registered `Customer` (search dialog over
`CustomerDAO.findAll()`) for loyalty/history tracking; unattached orders record no customer id
("Khách vãng lai"). "In hóa đơn" re-opens the receipt for the last order *actually paid* in this
screen visit and is honest ("chưa có hóa đơn") if nothing's been paid yet yet - it does not
fabricate a receipt for an unpaid cart. Empty-cart and not-yet-started-order checkout attempts
are blocked with a clear message rather than silently doing nothing.

**Small additive model changes**: added `toString()` to `Customer` and `Product` (matching the
existing pattern already documented on `Category`) so `ChoiceDialog` cells display something
readable instead of the default `Object.toString()`.

**Verified (not just by reading code)**: same fx:id/onAction cross-check as Session 6, run
against every FXML file again (including the 5 still on `AdminController`, to confirm removing
the order stub methods didn't orphan anything) - clean. Not yet run through `mvn javafx:run` -
please build and test; this is the largest, most stateful piece of logic added so far and the
one most likely to have an edge case that only shows up at runtime.

## Session 6 notes — Product Management built

Followed progress.md's own "next recommended step." `quanlysanpham.fxml` is now fully wired to
`ProductDAO`, following the same patterns already established by Category/Account management:

- **Real CRUD**: add/edit via a shared dialog (name, category, price, cost, stock, description,
  active checkbox - all validated: required fields, price > 0, cost ≥ 0 if given, stock ≥ 0
  integer, a category must be selected), search-by-name, status filter (Đang bán/Ngừng bán),
  pagination via `Pager`, and a fully dynamic category filter combo box (populated from
  whatever categories actually exist via `CategoryDAO.findAll()` - not hardcoded).
- ⚠️ **Decision: "delete" = deactivate, not a real delete.** Unlike Category (which really can
  be hard-deleted when unused), `ProductDAO` has no delete method and none was added: a future
  `OrderItem` will need to keep referencing its product, and the task brief is explicit that a
  product with order history shouldn't have that history invalidated by deleting it. The table's
  🗑 action toggles active/inactive instead, with an honest confirm dialog that says so rather
  than implying a real delete.
- ⚠️ **Decision: kept the 3 hardcoded quick-tab shortcuts (Coffee/Cake/Juice), didn't make them
  dynamic.** The FXML already had *two* filtering mechanisms side by side: 3 hardcoded
  quick-tab buttons (top row + a duplicate list in the sidebar) and a `categoryFilter` combo
  box. Rather than ripping out the hardcoded buttons and generating N buttons at runtime (a much
  bigger FXML restructure for a "don't rewrite the UI" project), the combo box was made the
  complete, always-accurate filter (any category, however many exist), and the quick-tabs stay
  as name-matched shortcuts for the shop's anticipated 3 main categories - both write to the
  same underlying filter state and stay visually in sync, so there's no contradiction between
  them, but a renamed/removed "Cà phê" category would just make that one shortcut show 0/select
  nothing rather than silently breaking.
- ⬜ **Deliberately not done**: no image upload/file picker - the `imageColumn` shows a plain
  placeholder glyph for every row (also applied to Category's own image column, which existed
  but was never wired to anything - one-line fix while in the area). Adding real image handling
  is a separate, self-contained feature; wasn't worth blocking core CRUD on it.
- ⚠️ Minor known edge case, not fixed: editing a product whose category has since been
  deactivated shows the category field empty (deactivated categories are intentionally excluded
  from the picker) rather than pre-selecting a hidden category - the admin has to actively
  (re)pick an active category to save. Not a crash, just something to be aware of.

**Real field/method collisions found and fixed** (`AdminController` is shared by 6 screens; this
is the first screen added since the original collisions were last dealt with, and it surfaced 5
new ones): `quanlysanpham.fxml`'s own `addCategoryButton`, `statusColumn`, `previousPageButton`,
`pageOneButton`, `pageTwoButton`, and `nextPageButton` all collided with fields/methods already
claimed by Category/Account management on the same shared controller (same fx:id or onAction
name, different meaning/type - would have caused an injection type mismatch or hijacked the
wrong handler at runtime, not a compile error, so this needed a careful cross-check rather than
just trusting the compiler). Renamed to `addCategoryFromProductButton`, `productStatusColumn`,
`productPreviousPageButton`, `productPageOneButton`, `productPageTwoButton`,
`productNextPageButton` (FXML `fx:id`/`onAction` + matching Java fields/methods) - purely a
rename, no visual/layout change. Also refactored `openCategoryDialog(existing)` into
`openCategoryDialog(existing, onSaved)` so the product screen's own "add category" shortcut
button can reuse the real category-save logic without touching `categoryTable` (which is null
on this screen).

**Verified (not just by reading code)**: cross-checked every `fx:id` and `onAction` in all 6
`AdminController`-backed FXML files against the actual Java fields/methods after this change -
zero mismatches anywhere, including on the untouched screens (Order management's many
`fx:id`s with no Java field are pre-existing/expected - that screen isn't built yet, see
below). Not yet run through `mvn javafx:run` by an actual human - please build and test.

⚠️ **Growing architectural debt, flagged again**: `AdminController` just grew by ~20 fields and
~350 lines to take on a 6th screen, and needed 5 collision renames to do it safely. Customer
management (next) and the Order/POS screen (the big one after that) will add a 7th and 8th.
Strongly recommend splitting into one controller per screen before/while building the next one,
now that the fields section header comments and this session's collision list show exactly
which screen owns which fields - that's the map a split would follow.

## Session 5 notes — dialog "mouse stuck" fix, attempt 2 (deferred)

Session 4's `initOwner(...)` fix resolved one real symptom (dialogs sometimes appearing behind
the main window), confirmed by the user, but not the main one: the mouse still feels confined
to the dialog's bounds and can't move freely around the rest of the screen while open. Tried
setting `Modality.WINDOW_MODAL` explicitly (was relying on the implicit `APPLICATION_MODAL`
default) on all four popup sites in the project (`AlertUtil`'s alerts + the add/edit-category
and add/edit-account dialogs, now sharing one `AlertUtil.configure(Dialog<?>)` method) — user
confirmed this did **not** resolve the cursor confinement either.

Target platforms clarified: **Windows is the actual deployment target**; GNOME, KDE, and
Arch+Hyprland Linux are just the developer's own coding environments, not where this needs to
ship. That's an important data point — Hyprland in particular is a **Wayland** compositor, and
JavaFX still runs there through XWayland (X11 compat) rather than natively; Wayland/XWayland's
pointer-confinement handling for "modal" transient windows is known to be stricter/buggier than
a typical X11 or Windows desktop in exactly this way. So there's a real chance this is specific
to the Linux dev machines' compositors and doesn't reproduce on Windows at all.

**Deliberately deprioritized per explicit user instruction** - a real, understood, minor UX
annoyance (dialogs still correctly block input to the app behind them; only cursor freedom of
movement is affected), not a functional bug and not blocking the main objective. Parking it
here. If it also reproduces on Windows later, the next thing to try is a bigger refactor: show
dialogs non-modally (`Modality.NONE` + `Dialog.show()`) and manually `setDisable(true)` on the
main window's root pane while one is open, sidestepping JavaFX/OS modal grab entirely
(`AlertUtil.confirm()`'s synchronous boolean return would need to become callback-based first).

## Session 4 notes — dialog "mouse feels stuck" fix

Reported after Session 3: alerts (invalid login, "not implemented yet", etc.) made the mouse
feel confined to the dialog's area instead of behaving like a normal modal window. Root cause:
`AlertUtil` created every `Alert` without ever calling `initOwner(...)`. An owner-less modal
dialog still blocks input correctly, but with no owner window, the window manager has no real
window geometry to tie its input grab to - this is a well-documented JavaFX gotcha, not an
actual limitation of modal dialogs. Fixed centrally in `AlertUtil` (one file, no call sites
changed): every alert now attaches itself to whichever open window currently reports focus via
`Window.getWindows()`, so `AlertUtil.error(...)`/`.warning(...)`/etc. behave identically
everywhere they're already called.

Confirmed working by the user (Session 3 perf fixes): `mvn javafx:run` builds and runs
successfully; login and Category/Account search are both noticeably snappier.

## Session 3 notes — performance fix ("login and buttons feel laggy")

Diagnosed and fixed three real causes, all synchronous work running directly on the JavaFX
Application Thread (found by reading the code, not yet re-verified by `mvn javafx:run` on a
real machine — please re-test and report back):

- **Login froze the UI for the PBKDF2 hash.** `AuthenticationService.login()` (via
  `PasswordUtil.matches()`, ~120,000 iterations) ran directly inside the login button's click
  handler. Now runs on a background thread via a new `util/Async` helper (a thin `Task`
  wrapper); the button disables and shows "ĐANG ĐĂNG NHẬP..." while it works, and the existing
  result-handling logic runs unchanged once it's done.
- **Every dashboard load ran ~10 sequential DB queries on the FX thread.**
  `DashboardStatsService.loadStats()` calls 10 DAO methods in a row, each opening its own
  connection; this ran synchronously in `initialize()` on both `AdminController` and
  `EmployeeController`, so every login *and* every click on "Trang chủ" paused the UI briefly.
  Both now load stats via `Async.run(...)` and populate labels/chart once the result is back.
- **Real N+1 query bug in Category and Account management.** Category's product-count column
  and Account's employee-name/phone columns called their DAOs *once per row*, inside the
  cell-value-factory *and* inside the live search-field listener — so every keystroke in either
  screen's search box re-ran a burst of fresh DB connections proportional to the row count.
  Fixed by batch-loading once per screen reload instead: added
  `CategoryDAO.countProductsByCategory()` (one grouped query → `Map<categoryId, count>`) and a
  `Map<employeeId, Employee>` built from `EmployeeDAO.findAll()` in `AdminController`; the
  cell-value-factories and filters now read from these in-memory maps. Typing in either search
  box no longer touches the database at all.

Deliberately *not* done as part of this fix (would be over-engineering for a single-user
desktop app once the N+1 pattern above is gone): connection pooling. Each DAO call still opens
its own short-lived SQLite connection, which is a completely reasonable, standard approach at
this scale now that the call count per screen is O(1) instead of O(rows).

## Session 2 notes
Confirmed working: `mvn clean compile` succeeds (verified by the user, not just by me reading
the code). Two runtime fixes landed this session, both surfaced only by actually running the
build (which I still can't do myself in this sandbox — please keep pasting build/run output,
that's the only way these get caught):
- `AdminController.java` was missing `import javafx.fxml.FXML;` despite using `@FXML`
  throughout — plain oversight, fixed.
- `sqlite-jdbc`'s runtime module dependency on `org.slf4j` (see Phase 2 section below) — fixed.

## Session 1 summary

### Phase 1 — Stabilize
- ✅ Fixed JavaFX/FXML version mismatch: all FXML files normalized to `xmlns="http://javafx.com/javafx/21"`
  to match the `javafx` 21.0.6 Maven dependency (see ⚠️ below for the module-path risk this pass
  could not verify).
- ✅ `EmployeeController` implemented (was missing; `employee-trangchu.fxml` referenced it).
- ✅ `HoaDonController` implemented (was missing; `donthanhtoan.fxml` referenced it).
- ✅ `dangky.fxml` (registration) had **no** `fx:controller` at all — added `RegisterController`.
- ✅ App now boots to `dangnhap.fxml` (login) instead of straight into the admin dashboard.
- ⚠️ **Bumped `maven-compiler-plugin` source/target from 11 → 21** (was briefly 17 in session 1 —
  17 was the *minimum* needed for records/text blocks/arrow-switch, not a real preference; bumped
  to 21 to match the installed JDK and the `javafx` 21.0.6 dependency, per request). The new
  service/model code uses records, text blocks, and arrow-style `switch` (16+/14+, both well
  under 21).
- ⚠️ **Could not run `mvn compile` in the session sandbox** — no network access to Maven Central
  from that environment. Every file was written carefully and cross-checked by hand (every FXML
  `onAction`/`fx:id` reference was grepped against controller methods/fields to confirm they match),
  but **please run `mvn clean compile` (or `javafx:run`) as the first step** and report anything
  that doesn't build — I have not been able to verify this myself yet.

### Phase 2 — Foundation
- ✅ SQLite added (`org.xerial:sqlite-jdbc:3.46.1.3`) — chosen over a client/server DB since this
  is a single-user desktop app. DB file lives at `~/.lunavera-coffee/lunavera.db` (survives `mvn clean`).
  - ⚠️ `module-info.java` now has `requires org.xerial.sqlitejdbc;` for this. That's the module
    name the driver's own manifest declares. **If the build ever fails with a "module not found"
    error for this**, the fallback derived name is `sqlite.jdbc` — swap the line in `module-info.java`.
    I could not verify which one resolves correctly without a working `mvn` in this session.
  - ✅ **Fixed (session 2):** `mvn javafx:run` failed at launch with
    `Module org.slf4j not found, required by org.xerial.sqlitejdbc` — recent `sqlite-jdbc`
    versions ship a real `module-info` that requires `org.slf4j` for internal logging. Added
    `org.slf4j:slf4j-api:2.0.16` to `pom.xml` and `requires org.slf4j;` to `module-info.java`.
    No logging backend is configured, so expect a harmless
    `no SLF4J providers were found, defaulting to no-operation` line on stderr — that's just a
    warning, not a failure.
- ✅ Password hashing: `PasswordUtil` uses PBKDF2WithHmacSHA256 (built into the JDK — no extra
  dependency, so no extra module-path risk on top of the one above).
- ✅ Models: `User, Employee, Customer, Category, Product, Order, OrderItem` + `Role`,
  `AccountStatus`, `OrderStatus`, `PaymentMethod` enums (`model/` package).
- ✅ DAOs: `UserDAO, EmployeeDAO, CustomerDAO, CategoryDAO, ProductDAO, OrderDAO` (`dao/` package),
  all using `PreparedStatement`/try-with-resources, wrapping `SQLException` in `DataAccessException`.
- ✅ `DatabaseConfig` creates the schema on first run and seeds one default admin account.
  **Default login: `admin` / `Admin@123` — change this after first login**, there's no UI for
  that yet (see Remaining).
- ✅ `util/Session` (current logged-in user, static holder — single-user desktop process),
  `util/SceneNavigator` (replaces the 3+ duplicated `switchScene` methods the original controllers
  each had), `util/AlertUtil`, `util/ValidationUtil`, `util/CurrencyUtil`, `util/Pager` (shared
  in-memory pagination for table screens).

### Phase 3 — Authentication
- ✅ Login (`LoginController` + `AuthenticationService.login`): checks username exists, password
  matches (PBKDF2), account not locked; routes ADMIN → admin dashboard, EMPLOYEE → employee
  dashboard by real DB role, not a hardcoded screen.
- ✅ Registration (`RegisterController` + `AuthenticationService.register`): required-field,
  phone format, username format/uniqueness, password strength + confirm-match validation.
  ⚠️ **Decision:** `dangky.fxml`'s fields (full name, phone, username, password — no employee-only
  fields) read as public *customer* self-registration, so it creates a linked `Customer` + `User`
  with role `CUSTOMER`, not an employee account. Employee/admin accounts are created from Account
  Management by an admin instead. Documented here since it's an interpretation of ambiguous scope
  (task section 32).
- ⚠️ **Decision:** there is currently no customer-facing dashboard screen in this project. A
  `CUSTOMER`-role login succeeds but shows an explanatory dialog instead of opening a screen that
  doesn't exist, rather than silently failing or faking one.
- ✅ Logout clears `Session` and returns to login, from both Admin and Employee dashboards.
- ✅ Role permission checks are enforced in code (not just hidden buttons) on the one screen this
  pass wired that way: Account Management's add/edit/lock actions call `requireAdmin()`; an
  Employee can view but not modify.
- ⬜ Forgot-password flow: shows an honest "not implemented, contact an admin" dialog rather than
  faking success.

### Phase 4 — Admin management
- ✅ **Category management** (`quanlydanhmuc.fxml`, real backing via `CategoryDAO`): add/edit
  (shared dialog), delete (blocked with a clear message if products still reference the category —
  no orphaning), search-by-name, real stat cards (total/active/hidden/product count), pagination
  via `Pager`.
- ✅ **Account management** (`quanlytaikhoan.fxml`, real backing via `UserDAO`/`EmployeeDAO`):
  this is also where **Employee management lives** — ⚠️ *decision:* there is no separate
  employee-CRUD FXML in this project (only `employee-trangchu.fxml`, the employee's own
  dashboard), so `openEmployeeManagement()` routes here rather than to a screen that doesn't
  exist. Add employee+login account (with role Admin/Employee), edit, lock/unlock, search,
  filter by role/status, detail panel, pagination. Admin-only actions enforced in code.
- ✅ **Product management** (`quanlysanpham.fxml`): see Session 6 notes above for full detail -
  real CRUD via `ProductDAO`, search, status + dynamic category filters, pagination. Delete is
  deliberately a deactivate-toggle, not a real delete (see Session 6 for why).
- ⬜ **Customer management** (`quanlykhachhang.fxml`): the FXML itself has no `fx:id`/`onAction`
  anywhere — it's pure static markup with nothing to attach a controller to yet. Needs FXML
  work before any controller logic can attach.
- ⬜ **Order management / POS** (`quanlydonhang.fxml`): this is the biggest remaining piece —
  a full dine-in table + cart + checkout UI. Filter tabs are inert (no data loaded, so nothing
  *appears* broken) but no order is ever created, edited, or paid yet. `OrderDAO`/`Order`/
  `OrderItem` are ready for this screen to be built against (insert, revenue/count aggregates
  are already implemented and used by the dashboards).

### Phase 5–8 — POS, payment, invoice, dashboard, polish
- ✅ Dashboard stats (`DashboardStatsService`, used by both Admin and Employee dashboards):
  today's order count, today's revenue, today's distinct customers, paid/open/cancelled order
  counts, 7-day revenue chart — all real DB aggregates, zero when there's no data yet (there
  will be no data until the order-creation screen exists).
- ⚠️ **Decision:** the dashboards' "Bàn" (dine-in table/seating) grid and counts are left as
  the original static placeholder content. There's no table/seating entity in the schema —
  it wasn't in the task's data-model section — so real numbers aren't invented for it. If
  dine-in table tracking is actually wanted, it needs its own schema + DAO first.
- ✅ `HoaDonController` (invoice/receipt popup) fully implemented against the `Order`/`OrderItem`
  models — populates every field, prints via `javafx.print.PrinterJob`. Not yet reachable from
  anywhere in the UI since there's no "complete order" action yet to open it from.
  ⚠️ No QR-code image is generated (no QR library in the project; the `qrCodeImage` slot is
  left blank rather than faked).
- ⬜ Payment workflow (cash/card, change calculation): no screen to attach it to yet — needs
  the POS/order-creation screen first.
- ⬜ Inventory management: `openInventoryManagement()` on both dashboards shows an honest
  "not built, stock is tracked directly on Product for now" message rather than a fake screen.

## Session 41 (continued): items 2.11-2.15

- 2.11 `OrderDAO.findStaleOpenOrders(Duration)`; `DashboardStats` gained `staleOpenOrders`; `staleOrderLabel` on the admin dashboard (12 h, `DashboardStatsService.STALE_OPEN_AFTER`).
- 2.12 `PasswordUtil.ITERATIONS = 600_000`, `needsRehash`, `AuthenticationService.upgradeHashIfOld` after a successful login.
- 2.13 `ProductDAO.existsByNameInCategory`, checked in the product dialog.
- 2.14 `HoaDonController.handlePrint` cancels the job on cancel or failure.
- 2.15 This section rewritten.
- Tests: `PasswordRehashTest` (6), `ProductNameCheckTest` (4), `StaleOrdersTest` (3); 248 in total.

## Session 42: the rest of TODO section 2 (2.16 - 2.63, except 2.38, 2.53, 2.56)

Built on the owner's TODO.md with the 7th-scan entries (2.40 - 2.63). 282 tests pass in a stand-in runner (sorted, reversed and shuffled order, no `data/` folder created); Maven and Windows were not used.

- **Money** (2.16, 2.40): `MoneyParser` in the product, payment and salary boxes; `Money.toDong` refuses huge exponents.
- **Accounts** (2.17, 2.31, 2.32, 2.48, 2.49, 2A.2): new `service/AccountService` (one transaction per create/edit/lock, last-admin rule inside the UPDATE); `UserDAO.findByUsername` fixed order; unique indexes `ux_users_username_nocase` and `ux_employees_phone` (skipped with a log line when duplicates exist).
- **Pictures** (2.18, 2.21, 2.35, 2.58): deletes only inside `data/images`, only after saving; `Thumbnails` cache; logo resized.
- **Receipt** (2.22, 2.27, 2.29, 2.37, 2.44, 2.45, 2.46): `receipt.css`, scrolling window, one-page print, paid time, table, cancelled banner, discount reason.
- **Windows and sessions** (2.30, 2.33, 2.43, 2.61, 2.63): other windows closed on return to login; single history/report window; request counter and 250 ms pause on search; re-validation before refund/report; "Đang mở" filter; change-password throttle.
- **Data safety** (2.23, 2.25, 2.26, 2.41, 2.42, 2.47, 2.50, 2.51, 2.52, 2.62): payment totals validated; `orders.paid_by`; cancel guard; restore swaps pictures last, refuses newer `user_version`, restores the transfer QR, shows a summary, banner + cancel, hourly auto backup; start refuses when the data folder cannot be created.
- **Screen** (2.19, 2.20, 2.24, 2.34, 2.39, 2.54, 2.55, 2.57, 2.59, 2.60): see TODO.
- **Not done:** 2.38 (flexible layouts), 2.53 (table state), 2.56 (owner decision).
- Looked at on screen with Xvfb at 1366x768: receipts (short, long and cancelled), dashboard banner, menu notice, single history window, logout closing windows.

- Session 42, later: sidebar flicker and side strips fixed (TODO 2.64): `SceneNavigator.showScreen` swaps the scene root instead of a new scene + window refit; `ScreenFit.layoutChildren` fills the window.

## Session 43
- 2.56 done: hidden category => its products are not sold at the POS (SellableMenu, SellableMenuTest). Admin table shows "Danh mục đang ẩn". 284 tests pass. Next: 2.38, 2.53.
- 2.38 done: tables use constrained column policy (products, customers, reports), login background covers the window (LoginController.coverBackground). Checked at 2500x1100 in Xvfb. ScreenFit kept as small-screen safety net. 2.53 waits for the MariaDB branch merge (owner's request).
- Section 3 batch 1 (app-level): 3.3/3.33 single-instance lock, 3.9, 3.12, 3.16, 3.19, 3.24, 3.27, 3.29, 3.30, 3.32, 3.34, 3.39 done; 293 tests pass. SQL-heavy items (3.1, 3.2, 3.5, 3.13, 3.22, 3.23, 3.31, 3.35, 3.36, 3.37, 3.41...) left for after the MariaDB merge.
- Section 3 owner decisions applied: 3.6 no foreign phones, 3.15 10-digit mobiles only, 3.8 table emptying limited to opener+admin, 3.5/3.23 pay date rule (OrderDAO.EFFECTIVE_DAY), 3.7 and 3.11 left as is, 3.10 clock warning. 297 tests pass. Touches SQL in OrderDAO/ReportDAO: re-apply EFFECTIVE_DAY after the MariaDB merge (use DATE(COALESCE(paid_at, order_date))).
- Section 3 batch 3: 3.18 (NFC + case-insensitive names), 3.20 (confirm before discarding a form), 3.28 (late autosave warning, OrderDAO.updateIfStillOpen), 3.38 (seed failure cleanup + dialog), 3.40 (legacy copy refused while journal exists). 303 tests pass.
- 3.14 done (SessionGuard no longer pauses for non-modal windows). 305 tests pass. Section 3 now only has DB/schema items and features left.
- Section 2A (security): done 2A.1, 8, 9, 11, 13, 15, 16, 20, 22; partly 3, 4, 6, 17, 21. Open for owner decisions: 5, 7+19, 23, 24, and 3's accounts list; after merge: 10, 18. 318 tests pass (also in reversed and shuffled order).

- Session 44: 2A.5 done (FirstRunDialog, no seeded admin in production), 2A.24 done (admin-only customer phone change, logged), 2A.3 resolved (accounts screen already blocks employees). Open: 2A.7/2A.19 and 2A.23 owner answers. 322 tests pass.
- Session 44 (2): 2A.7 done and 2A.19 partly (option B delay in LoginThrottle, in memory; table after merge), 2A.23 done (no customer login; staff phone login kept). 324 tests pass.
- Session 44 (3): 2.19 fixed properly (clock stops when the order screen leaves the scene; verified with the real FXML under Xvfb). 324 tests pass.
- Session 44 (4): section 4 items that need no schema change: 4.3 thermal receipt layout (ThermalReceipt), 4.4 customer purchase history (HistoryFilter.customerId, OrderHistoryWindow.showForCustomer), 4.17 AppIcon, 4.20 profit, 4.21 CSV export (ExportCsv, admin only), 4.30 payment split in the report. Also: history window columns now fit (constrained). 329 tests pass; rendered under Xvfb (receipt 58/80 mm, report, customers, customer history). Not tried: real printer, Excel, Windows. Left for after the merge: the rest of section 4.
- Session 44 (5): section 5 items that need no schema change: 5.1 quick cash, 5.2 date presets (DatePresets), 5.3 dashboard refresh (AutoRefresh), 5.5 confirm line removal, 5.6 low-stock badge, 5.7 shortcuts, 5.8 product CSV (ProductCsv), 5.9 UiSettings (ui-settings.properties), 5.10 best sellers (ReportDAO.unitsSold), 5.12 OrderNumber, 5.13 StockPolicy, 5.14 TextNormalizer.searchKey, 5.15 ErrorText catches English messages, 5.21 no success box, 5.23 history phone search. Found already done: 5.4, 5.20, 5.22, 5.24. Left for after the merge: 5.11 (TableDAO), 5.17, 5.18, 5.25; partial 5.16, 5.19. 341 tests pass; checked under Xvfb: payment dialog, POS (toggle, badges, Ctrl+F, best-seller order), AutoRefresh stop.

## Session 45: merge with the owner's MariaDB branch
- Merged the owner's database branch (MariaDB default, SQLite option, importer that replaces the first-start data so the old admin carries over) with sessions 42-44. The branch point of the owner's copy was older than my tree, so every file was merged by hand: files where the owner had only an older copy of mine (ScreenFit, SceneNavigator, SessionGuard, PasswordChangeService/Dialog, AuthenticationService, StartupProblem) kept my newer version; DB code came from the owner.
- Ported for both databases: `orders.paid_by` (MariaDbSchema, SqliteSchema.LATER_COLUMNS, importer, MoneyMigration), `AppFlags` via SqlDialect, LIKE escape `!`, `CAST AS CHAR`, "today" queries bound from the app clock, sort enum with dialect collation, `DbTime` for `newestActivity` and account creation, BackupService (`lunavera.sql` entry, restore and inspect refused on MariaDB with the phpMyAdmin help), HelloApplication (SqliteSnapshotService, hostServices), AdminController (phpMyAdmin box, restore help).
- Checked: compiles; 424 tests on SQLite, 395 on MariaDB 10.11 (driver 2.7.6 here, pom says 3.4.1); import of an old SQLite file (admin + customer + starter menu) into an empty database and into one with first-start data; first start shows no first-run dialog; restart with the import line still set changes nothing.
- The owner's earlier `CategoryDAOTest` failure on MariaDB did not reproduce in the merged tree (not diagnosed; may depend on the driver or test order).
- Not checked: Windows, XAMPP/phpMyAdmin, MySQL 8, real driver 3.4.1, several tills. MariaDB has no unique index for the staff phone yet.
- Session 45 (2): staff phone is now unique in the MariaDB database too (`ux_employees_phone` in `MariaDbSchema`; older databases get it on start via `SchemaMigrations.ensureMariaEmployeePhoneKey`, blank phones become NULL, skipped with a warning while two staff share a phone; the importer turns blank staff phones into NULL; `EmployeeDAO` turns the database error into "Số điện thoại này đã thuộc về một nhân viên khác."). New tests `StaffPhoneUniqueTest` (both databases) and `MariaDbStaffPhoneKeyTest` (MariaDB). 427 tests pass on SQLite, 399 on MariaDB. New TODO items 1.8 (in-app restore on MariaDB) and 1.9 (MariaDB account names only protected by the app).
- Session 46 (2 and 2A leftovers, on the owner's updated tree): 2.53 table state from the OPEN order only (`TableDAO.holdsWithoutOrder`, dashboard warning); 2A.18 `users.must_change_password` (via `LATER_COLUMNS`, MariaDbSchema, importer, `AccountService`, `LoginController`, forced dialog wording); 2A.10 session ends after a password change/reset (`Session` baseline hash, `SessionGuard` `PASSWORD_CHANGED`, `PasswordResetSessionTest`); 2A.21 `data/tmp` (`DataPaths.createTempFile/cleanTmp`, used by `BackupService`, cleaned in `HelloApplication`); 2A.17 `TransferQrGuard.replace` + dashboard button "Mã QR chuyển khoản" + start-up warning.
- Checked: compiles; 443 tests OK on SQLite, 415 OK on MariaDB 10.11 (driver 2.7.6). End-to-end: an old MariaDB database gets `must_change_password` on start; a fresh import from an old SQLite file works. Not checked: any JavaFX dialog (never opened), Windows, XAMPP/phpMyAdmin, real driver 3.4.1, several tills.
- Not done: 2A.6 (needs Maven Central); 2A.3 DAO-level admin checks for products/categories and 2A.4 backup passphrase (the 8th-pass notes said so; still open).

## Session 47 (9th pass)
- Section 3 leftovers: 3.26 done (`DatabaseFileCheck`: a 0-byte `lunavera.db` next to backups or shop pictures is refused with a message instead of starting a new shop); 3.22 partly (`OrderDAO.insert` refuses a second OPEN order for a table; no database rule yet). Not done: 3.1, 3.4, 3.13, 3.17, 3.21, 3.25 (new columns), 3.31, 3.36, 3.37, 3.41.
- Section 6: 6.6 SQLite steps are a numbered registry (`SchemaMigrations.SQLITE_STEPS`); 6.7/6.22 the `Ca Phe` merge runs once (`app_flags` `merge_ca_phe_v1`); 6.9 `docs/RELEASE.md`. Not done: 6.3, 6.4, 6.5 (JavaFX), 6.10.
- Checked: the changed non-JavaFX classes compile with the JDK compiler; `DatabaseFileCheckTest` ran with a stand-in runner (5 passed). NOT run: every test that needs the database (no driver here): `OrderDAOTest.aTableCannotHoldTwoOpenOrders`, `SchemaMigrationsTest.everySqliteVersionAfterOneHasAStep`, `LegacyCategoryMergeTest`. Expected `mvn clean test`: 458 on SQLite.
- Section 6, items 11-15: 6.11 `-Xlint:all` on (4 warnings in the non-JavaFX files fixed); 6.12 try/catch around `table_number` removed from `OrderDAO.map`; 6.13 six 1254 px PNG starter pictures became 800 px JPEG, `seed-menu` 12 MB -> 2.8 MB (`MenuSeedData` names updated); 6.15 `docs/PACKAGING.md` (untried). Not done: 6.14 (JavaFX controllers). Nothing new needs the database to run except the `OrderDAO.map` change (the existing order tests cover it).

## Session 48 (10th pass)
- Section 6, items 16-20: 6.17 controllers moved to `com.coffeeshop.coffeeshopmanagement.controller`, `HelloApplication` -> `LunaveraApplication` (package/imports/module-info/FXML/pom/README/docs; checked by script only, not compiled); 6.19 `CurrencyUtil` uses a `ThreadLocal<DecimalFormat>` (+1 test; 5/5 pass in a stand-in runner, but the new test also passes on the old code on this 1-CPU machine); 6.20 `.github/dependabot.yml`; 6.18 `SessionGuardTest` moved to the `util` test package (clock part not done). Not done: 6.16 WAL (reasons in the TODO note).
- NOT run: anything that needs JavaFX or the database driver. First thing to do: `mvn clean test` (expected 459 on SQLite: 458 + the new `CurrencyUtilTest` test), then `mvn javafx:run` and open every screen, because of the package move.

## Session 49 (11th pass): TODO 6.30 - 6.35
- 6.30 done in code: `FxmlControllerCheckTest` (6 tests, plain-text reading of FXML/CSS/controller source). It found five style classes used on a screen with no rule loaded for them (`primary-button` on the dashboard and customers screens, `search-field` on customers, `section-subtitle` on the employee dashboard, `product-area` on products) and 26 `fx:id`s nothing reads; both are listed in the test as known, so only new ones fail. The five classes need a look on screen.
- 6.31 checked (CI already runs Ubuntu and Windows) and 6.34 (stray notes file already gone): boxes ticked.
- 6.32 checked: every listed reproduced bug already has a test; no new ones written.
- 6.33 done in code: `pluginManagement` pins for clean, resources, jar, install and deploy plugins, plus `maven-enforcer-plugin` (JDK 21+, Maven 3.8.5+). Versions are from memory and Maven was not run.
- 6.35 part done: `AsyncOrderTest` (6), `PageMathTest` (8), `TextLengthLimiterTest` (4); new `util/PageMath` and `TextLengthLimiter.accepts` so the logic is testable; `PageBar` now calls `PageMath`. Not done: `PasswordReveal`, `IdleLogout` wiring, receipt paid time/cancelled banner (need a JavaFX toolkit).
- Checked here with a stand-in JUnit (jars and network are not available): the 24 new tests pass (FXML test also checked by breaking the files on purpose); `PageMath`, `TextLengthLimiter` and `Async` compile with `-Xlint:all` against small JavaFX stand-ins. NOT run: Maven, the pom change, `PageBar.java` (needs JavaFX), anything needing the database.
- First thing to do: `./mvnw -B clean test` (24 more tests than before; 498 `@Test` annotations in the tree by count), then open the customers screen, both dashboards and the products screen and look at the five classes above.

## Session 51 (11th pass, other branch): TODO 6.37 - 6.39
- 6.37: `dao/OrderItemDiff` and the autosave in `OrderDAO.updateIfStillOpen` now write only changed lines. 8 DB-free tests pass here; the real-database test `OrderDAOTest.autosaveChangesOnlyTheLinesThatDiffer` is not run.
- 6.38: README was mostly current already; fixed the "12 tables" wording there and in `docs/DATABASE.md`.
- 6.39: `util/AppClock` replaces `now()`/`today()` in the data code; the test bootstrap pins the date for the run. 5 tests pass here.
- Checked here: all non-JavaFX main classes plus the edited tests compile with `-Xlint:all` against small JavaFX/JUnit stand-ins. NOT run: Maven, any database test.
- First thing to do: `./mvnw -B clean test`; if `OrderDAOTest.autosaveChangesOnlyTheLinesThatDiffer` or `UniqueViolationTest` fails, the driver's error code or the `ORDER BY id` assumption is the place to look.

## Session 50 (12th pass): TODO 6.36, 6.40 - 6.45
- **6.36 / 6.40** `dao/UniqueViolation`: a duplicate is recognised by error code (MariaDB 1062; SQLite 2067 / 1555 through `SQLiteException.getResultCode().code`; message text only for the plain SQLite code 19). Used in `CustomerDAO`, `CategoryDAO`, `EmployeeDAO`, and `AccountService.translate` (which now also catches MariaDB duplicates). `UniqueViolationTest`.
- **6.41** receipt layout checked (no demo values); `ReceiptLayoutTest` keeps it that way.
- **6.42** `DatabaseConfig` resolves settings, SQLite file and URL on first use (`Resolved` record, stored only after success) instead of in static initializers; a failure no longer kills the class for the run; `reset()` lets the data folder change. `DatabaseConfigResolveTest`.
- **6.43** stale class comments fixed in `OrderDAO`, `HoaDonController`, `CustomerController`.
- **6.44** README section about fonts and pictures. The owner's table (who made the photos, right to use) is empty on purpose.
- **6.45** `CheckoutService.pay(..., Role actingRole)` enforces `DiscountPolicy.require` before anything is written, with the subtotal taken from the lines; `DiscountPolicy.messageFor/require`; `OrderController` passes the role. 6 tests in `CheckoutServiceTest`, 5 in `DiscountPolicyTest`.
- **Checked here** (JDK 21 only; no Maven, no driver jars, no JavaFX): the changed non-JavaFX classes compile with `-Xlint:all` against small stand-ins for `org.sqlite`; `DiscountPolicyTest` 15 of 15 and the `UniqueViolation` rule pass in a stand-in runner; the fail-twice-then-recover path of `DatabaseConfig` and `reset()` ran for real (folder logic only).
- **NOT run:** Maven; anything needing the database (`UniqueViolationTest` real-driver tests, `DatabaseConfigResolveTest`, the new `CheckoutServiceTest` tests); `OrderController` (JavaFX); `ReceiptLayoutTest` (regex checked by script). New tests: about 9 + 7 + 2 + 2 + 6 + 5 on top of the 498 `@Test` of session 49.
- **First thing to do:** `./mvnw -B clean test`. If `UniqueViolation` does not compile, `SQLiteException.getResultCode()` or its `code` field differs in the real driver: adjust that one line. Then pay one order as an employee with a discount above 10% and check the message.
- **Owner:** fill in the photo table in README (6.44). (6.36 was done with 6.40; 6.37 - 6.39 were done on the other branch, see Session 51.)

## Session 52: merge of the two branches (6.30 - 6.45)
- Merged by three-way merge against the zip both branches started from. Round-1 work (6.30 - 6.35) was identical on both sides and merged without a change.
- 6.36 / 6.40 were done twice (this branch `SqlErrors`, the other `UniqueViolation`). Kept `UniqueViolation` (it reads the driver's typed result code) and removed `SqlErrors` and `SqlErrorsTest`; `CategoryDAO`, `CustomerDAO`, `EmployeeDAO` and `AccountService` use `UniqueViolation`.
- Kept from this branch: 6.37 (`OrderItemDiff`, autosave), 6.38 wording fix, 6.39 (`AppClock`, test date pin). AppClock was re-applied to `CustomerDAO`, `AccountService` and the new `CheckoutServiceTest` test after taking the other branch's versions of those files.
- Kept from the other branch: 6.41 - 6.45 (`DatabaseConfig` resolved on first use with `reset()`, `CheckoutService.pay(..., Role)` with `DiscountPolicy.require`, `ReceiptLayoutTest`, README photo section).
- Interaction checked by reading: `DatabaseConfig` (their `Resolved`/`reset()` and our two `AppClock` lines in the first-admin seed merged without overlap); `CheckoutService` (their role/discount check and our `AppClock.now()` for order and paid time).
- **Fix found while merging:** `PageMathTest` (6.35, from the first round, present on both branches) lacked `import java.util.List`, so it did not compile and would have stopped test compilation for the whole module. Import added.
- **Checked here after the merge** (JDK only, stand-in JUnit/JavaFX/`org.sqlite`): all non-JavaFX main classes and every test file touched by either branch compile; these run and pass: `OrderItemDiffTest` 8, `DiscountPolicyTest` 15, `FxmlControllerCheckTest` 6, `ReceiptLayoutTest` 2, `AppClockTest` 5, `AsyncOrderTest` 6, `PageMathTest` 8, `TextLengthLimiterTest` 4. NOT run: Maven, and every test that needs the database (`UniqueViolationTest`, `DatabaseConfigResolveTest`, `CheckoutServiceTest`, `OrderDAOTest` incl. the autosave test, the other DAO tests).
- **First thing to do:** `./mvnw -B clean test` on the merged tree.

## Session 53: second merge check (this tree against the 12th-pass branch)
- Compared this merged tree with the 12th-pass branch (the one with `UniqueViolation`, `DatabaseConfig.reset()`, `CheckoutService.pay(..., Role)`) and the zip both started from, three-way. Every code and test file of the 12th-pass branch is already in this tree: the merge of each file equals this tree's version, so no code changed in this step. The only difference in a contested file was `AppClock.now()` instead of `LocalDateTime.now()` in `CheckoutServiceTest`; this tree's version is kept.
- Documentation fixes only: `TODO.md` 6.37 and 6.39 were left unticked although their work is in the tree and Session 51 describes it; both are ticked now (6.37 got its note). The stale "Not started: 6.37 - 6.39" line in Session 50 was corrected.
- Still open in section 6 (as far as the notes say): 6.24 - 6.29, 6.32, 6.33, 6.35, 6.44 (waiting for the owner's photo table).
- Not run: Maven or any database test (no Maven, driver jars or JavaFX here). First thing to do: `./mvnw -B clean test`.

## Session 54 (13th pass): the small practices, TODO 6.12, 6.16 - 6.19, 6.23, 6.26, 6.28, 6.33
- **Already done, only unticked** (checked in the files, ticked with a note): 6.13 (seed-menu 2.8 MB; 6.15 stays open: `docs/PACKAGING.md` exists but no real build was made), 6.12 (`OrderDAO.map`), 6.17 (controller package, `LunaveraApplication`), 6.18 (`AppClock` from 6.39), 6.19 (`CurrencyUtil` `ThreadLocal`), 6.33 (pom plugin pins and enforcer).
- **6.16** write-ahead logging: `DatabaseConfig.enableWriteAheadLog()` at the very end of `initialize()`, then `synchronous = NORMAL` on every later connection; falls back quietly if WAL is refused. README, `docs/RELEASE.md`, `docs/DATABASE.md` say how to copy or put back the file by hand. `DatabaseConfigWalTest` (4).
- **6.23** `config/SqlStatus` replaces about 45 hand-typed status/role literals in the SQL of six classes; the generated SQL text is identical (checked by substituting back). `SqlStatusTest` (3) also forbids new literals.
- **6.26** **`.gitignore` was missing** from the project although the notes said it was there. Recreated and checked with `git check-ignore`; `GitIgnoreTest` (2) guards it and `.gitattributes`.
- **6.28** README "Rules when changing the code". Audit of all 69 log calls: `SchemaMigrations` was writing shared **phone numbers** into the log in four places; it now writes a count and the query that lists them.
- **Checked here** (JDK 21 only; no Maven, no SQLite driver, no JavaFX): every touched non-JavaFX class compiles with `-Xlint:all` against stand-ins (the only compile errors are the known JavaFX ones in `ImageStorage`, which the sourcepath pulls in); `SqlStatusTest` and `GitIgnoreTest` run and pass (5), and both fail when I plant a literal / remove `.gitignore`; the SQL equivalence of the five rewritten files was checked by script.
- **NOT run:** Maven; `DatabaseConfigWalTest` (needs the driver); every existing database test after the WAL switch. New tests this pass: 9 (3 + 4 + 2).
- **First thing to do:** `./mvnw -B clean test`. If a test about `lunavera.db` size fails (`BackupSafetyTest`, `BackupServiceTest`), look at WAL first (a checkpoint changes the main file's size). Then the two new checks in section 0 (WAL files on Windows; `git status` shows no `data/`).
- **Not touched** (medium or large, and 6.44 is waiting for the owner): 6.24 CHECK constraints, 6.25 backup/data-folder tests, 6.27, 6.29 CSS, 6.32, 6.35 and the rest of the open list.

## Session 55 (13th pass, part 2): the medium items I could do without a screen or a server
- **6.24 (SQLite, part)** `SchemaMigrations.ensureDataRules`: triggers that make the database refuse negative prices/stock, order lines of 0 pieces, a discount above the subtotal, unknown statuses/roles, negative points. Skipped while old rows break a rule. MariaDB not done. `DataRulesTest` (9).
- **3.22** a partial unique index `ux_orders_one_open_per_table` (SQLite) and a clear message when two tills collide; the code check and `MAX(id)` were already there. MariaDB still relies on the code check.
- **6.25 / 6.27** `backupTo` writes `<target>.part` first so a failed copy cannot destroy an older file; `BackupToTest` (4).
- **3.31** `VN_KEY` SQLite function plus `DatabaseConfig.searchKeySql`; history search ignores case and accents. `VietnameseSearchTest` (5).
- **3.41** dashboard warning for shared customer phones (`CustomerDAO.countSharedPhones`, `DashboardStats.sharedCustomerPhones`, one line in `AdminController`).
- **6.3** `DashboardStatsServiceTest` (10).
- **5.19** tooltips on the two icon-only FXML buttons (login eye, account-detail close).
- **Housekeeping:** the notes of 6.36 and 6.37 had been attached to 3.36 and 3.37 by a merge; removed. Both are still open.
- **Checked here** (JDK 21 only; no Maven, no SQLite driver, no JavaFX): all touched classes and all new tests compile against stand-ins; the data-rule SQL was run in Python's SQLite on the real DDL (normal flows pass, bad writes refused, unique index works, legacy rows detected); the folding rule was mirrored in Python; `DashboardStatsServiceTest` warning tests (5), `FxmlControllerCheckTest` and `ReceiptLayoutTest` (8 in all) were run and pass; both edited FXML files parse.
- **NOT run:** Maven and every test that needs the database (`DataRulesTest`, `BackupToTest`, `VietnameseSearchTest`, the DB half of `DashboardStatsServiceTest`, and all older database tests with the new triggers and WAL in place). New tests this pass: 9 + 4 + 5 + 10 + 0 = 28.
- **Look first if `mvn test` fails:** (1) compile error in `DatabaseConfig.registerSearchKeyFunction` (the `org.sqlite.Function` API was written from memory); (2) an older test that writes a value the new triggers refuse (I searched the tests for raw inserts and found none on the shared database, but a DAO test that stores a discount above a subtotal would trip `orders_money_valid`); (3) the two file-size tests named in session 54.
- **Not done, and why:** 6.6, 6.7, 6.22, 6.11 (each says what is still missing in its own note; needs a second real migration step, a numbered legacy-merge step that raises `LATEST_VERSION`, or Maven Central for SpotBugs); 6.35 (the rest needs a JavaFX test toolkit); 3.1, 3.13, 3.17, 3.21, 3.25 (each needs new order or product columns in both schemas plus a decision for old rows); 3.4 (feature 4.8); 3.36 (the date filters share `EFFECTIVE_DAY` with every report, and the speed gain was never measured); 3.37 (MariaDB commits each `CREATE TABLE` by itself, so a transaction only helps SQLite and cannot be tested here); 5.11, 5.16, 5.17, 5.18, 5.25 (JavaFX screens I cannot load here).

## Session 56 (13th pass, part 3): MariaDB data rules and the data line on the login screen
- **6.24 (MariaDB, rest)** `SchemaMigrations.ensureMariaDataRules`: the same rules as `CHECK` constraints, each tried on its own, existing-row check first, failures logged and never thrown. `DataRuleSqlTest` (4). 6.24 is ticked now.
- **5.25** `DatabaseConfig.locationSummary()` + `LoginController.showDataLocation()` + a `dataLocationLabel` in `dangnhap.fxml` + `.data-location` in `dangnhap.css`; never shows the user or the password. `LocationSummaryTest` (3).
- **Checked here:** everything touched compiles (the only compile errors in any run are JavaFX classes this sandbox lacks: `ImageStorage`, `TextLengthLimiter`); 35 database-free tests were run together and pass (`SqlStatusTest`, `DataRuleSqlTest`, `LocationSummaryTest`, `GitIgnoreTest`, `DiscountPolicyTest`, `ReceiptLayoutTest`, `FxmlControllerCheckTest`); the login FXML parses.
- **NOT run:** Maven; any database test; any MariaDB statement (no server); the login screen on a display. New tests this part: 7 (4 + 3). Over the whole 13th pass: 9 (session 54) + 28 (session 55: 9 + 4 + 5 + 10) + 7 = 44 new tests.
- **Still open (and why):** 6.6 / 6.7 / 6.22 (need an owner decision to delete the run-once job, or a real second migration step; the data rules are deliberately an idempotent ensure and not a numbered step, because a fresh database skips the numbered steps); 6.11 (SpotBugs/Checkstyle need Maven Central); 6.35 (a JavaFX test toolkit); 3.1, 3.13, 3.17, 3.21, 3.25 (new columns in both schemas and a rule for old rows); 3.4; 3.36; 3.37; 5.11, 5.16, 5.17 (open-folder button), 5.18 (JavaFX screens I cannot load).
