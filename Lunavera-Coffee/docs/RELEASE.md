# Before a release, an upgrade or a database change

A short checklist (TODO 6.9). Last brought up to date on 2026-11-06 (see `progress.md`, Session 192; the platform decision is dated in its own block). This is the only copy: `docs/RELEASE.md`.

## What the code already does, and what it does not

The code makes safety copies by itself in these places:

- **Both kinds of shop:** an automatic backup `data/backups/auto-<time>.zip` (database and pictures; only every 7th one holds the pictures, the others are `auto-<time>-db.zip`) at start-up and then
  hourly checks, one at most every 24 hours, newest 14 kept. On MariaDB the zip holds `lunavera.sql` made by `mysqldump`,
  which must be installed on the till computer (`db.mysqldump` in `data/database.properties` if it is not on the PATH).
- **SQLite shop:** every restore first saves `before-restore-<time>.zip` (newest 3 kept). A one-time
  `before-money-migration.db` is made for the oldest databases.
- **MariaDB shop:** also `data/snapshots/lunavera-<time>.db` (a SQLite copy of every table, **without** the pictures,
  hourly, newest 10 kept). The in-app restore does **not** work on MariaDB (TODO 1.8): see step 3.

- **Before a numbered migration step** (TODO 6.9, `PreUpgradeBackup`): when the database already holds accounts and at least one step
  is missing, the app first writes `data/backups/pre-upgrade-v<from>-to-v<to>-<time>.db` (SQLite) or `.sql` (MariaDB, needs
  `mysqldump`). It is never pruned. If the copy cannot be made the upgrade still runs (the reason is in the log), so this does
  not replace step 1 below: that copy is saved off the computer, this one is not.
- **Nothing copies the shop's data before a new version that has no table change**, so do step 1 by hand every time.

## Platform versions: when to move (TODO 6.89)

**Decision of 2026-11-06: staying on JDK 21 and JavaFX 21 (the long-term-support line). Next review: November 2027. Start the move to JDK 25 and JavaFX 25 no later than March 2028.** Read once a year; a new decision replaces this block (keep the date).

What the web said on 2026-11-06 (the sources are named; check them again at the next review, versions and dates change):

| Part | Pinned in `pom.xml` | What exists | Support / security |
| --- | --- | --- | --- |
| JDK | `<release>21</release>`, CI on JDK 21 | JDK 25 (LTS, September 2025) is the newest long-term release | Eclipse Temurin supports 21 "at least until December 2029" and 25 until September 2031 ([Adoptium](https://adoptium.net/support)) |
| JavaFX | 21.0.6 | 21.0.13 (October 2026 patch update, LTS line), 25.0.5 (LTS), 27.0.1 (current, not LTS), 17.0.21 (last 17) ([Gluon](https://gluonhq.com/news/2026-10-20-oct-2026-critical-patch-update/)) | JavaFX 21 supported until September 2028, JavaFX 25 until September 2030 ([Oracle roadmap](https://www.oracle.com/java/technologies/jvp-support-roadmap.html)). JavaFX 25 needs JDK 23 or newer and, on Linux, GTK 3.20 ([OpenJFX 25](https://openjfx.io/highlights/25/)). |
| sqlite-jdbc | 3.46.1.3 | 3.54.1.0 (29 October 2026) | No release note since 3.46 names a security fix; the one known CVE (2023, fixed in 3.41.2.2) does not touch 3.46.1.3 ([releases](https://github.com/xerial/sqlite-jdbc/releases)). 3.53.2.1 changed how `DATETIME` columns are typed: run the SQLite tests before moving. |
| mariadb-java-client | 3.4.1 | 3.4.5, 3.5.11, 4.0.0 | **3.4.1 is affected by CVE-2026-55857** (fixed in 3.4.3, 3.5.9): a hostile server can ask for the "dialog" (PAM) login and get the password in clear text when `sslMode` is `DISABLE`, which is the default of this program (`db.ssl=false`). Not reachable when the shop's server uses normal password login, a verified TLS connection or a local socket (CVSS 5.9, medium) ([CVE page](https://hol.org/guard/security/cves/CVE-2026-55857-mariadb-connectorj-cleartext-transmission-of), [3.5.9 notes](https://newreleases.io/project/github/mariadb-corporation/mariadb-connector-j/release/3.5.9)). |

**Why stay on 21 for now:** JDK 21 and JavaFX 21 both have years of fixes ahead of them, the program runs on them, and a move to 25 changes the minimum JDK (a rebuilt installer, TODO 6.15, tested on a clean Windows PC) for no feature the shop needs. **Why not later than March 2028:** JavaFX 21 stops getting fixes in September 2028, and the move needs a month of testing.

**To do now, on a computer that can run Maven (this decision was written without a build, so `pom.xml` was not touched):**

1. Raise to the newest patch of the same line: JavaFX 21.0.6 to 21.0.13 and mariadb-java-client 3.4.1 to 3.4.5 (the second closes CVE-2026-55857; the 3.4 line has no API change). Dependabot proposes both (`.github/dependabot.yml`: patch and minor only for JavaFX).
2. sqlite-jdbc 3.46.1.3 to 3.54.1.0 is a bigger jump: run `./mvnw -B clean test` (the SQLite tests) and open one old shop's database before merging.
3. Whichever shops use MariaDB over a network (not the same computer): set `db.sslMode=verify-full` or `verify-ca` in `data/database.properties` (see `DatabaseSettings`), which removes the exposure whatever the driver version.

**After any move of JDK or JavaFX:** run the whole suite on both the old and the new JDK, build the installer (`docs/PACKAGING.md`) and try it on a clean Windows PC, then change this block.

## 0. Run the checks before handing anything over (TODO 6.73)

1. `./mvnw -B clean test` on a computer that has the libraries (the real answer; CI runs it too).
2. Where Maven cannot run, `tools/run-library-free-tests.sh` (about a minute, JDK and JUnit only). It must end with `0 FAILED`. It skips the tests that need JavaFX, SQLite or MariaDB and says how many; say so in the notes when a change touches code those tests cover.

## 1. Back up first

1. Close the app on every till.
2. SQLite shop: open the admin dashboard and press "Sao lưu dữ liệu" **before** closing, save the `.zip` somewhere that is
   not the `data/` folder (a USB stick or another computer). Or copy the whole `data/` folder while the app is closed
   (never while it is open: the database then also lives in `lunavera.db-wal`).
3. MariaDB shop: "Sao lưu dữ liệu" writes a `.zip` with `lunavera.sql` (a `mysqldump`) and the pictures; save it off the
   computer too. If you also export from phpMyAdmin because the shop trusts that, copy `data/images` as well: the
   pictures are not in the database.
4. Check the file is not empty and its size is about what the last backup was.

## 2. Try the new version on a copy, not on the shop's data

1. Copy the `data/` folder (or restore the backup into a scratch MariaDB database) and start the new version against the
   copy: `java -Dlunavera.dataDir=D:\LunaveraCopy ...` (or `LUNAVERA_DATA_DIR`).
2. Log in, open the dashboard, the history, one report and one receipt; open each admin screen (home, products, categories,
   accounts: they are separate controllers since the structural pass, Session 66); make and pay one test order, then cancel it.
3. Only then start the new version on the real data.

## 3. Test a restore at least once (before you need it)

A backup that was never restored is a guess. Once, and again after any change to the backup code:

1. **SQLite:** on the copy from step 2, press "Khôi phục dữ liệu" and pick the backup from step 1.
   **MariaDB:** restore the `.zip` by hand into a scratch database: unzip `lunavera.sql`, import it in phpMyAdmin, copy the
   zip's `images` folder into `data/images` (the steps are in `docs/DATABASE.md`, section 6).
2. SQLite: restart the app (the restore is applied at the next start). Check that the orders, customers, products **and the
   product pictures** are the same as before.
3. A restore also brings back the accounts and passwords of that day: check the accounts list afterwards.
4. Note the date you did this in the "Release status" block at the top of `progress.md`.

The SQLite restore code is covered by `BackupServiceTest`; this step proves it on the shop's real files.

## 4. Release

- `./mvnw -B clean test` passes on Windows and Linux (the CI runs both). The project holds 1632 `@Test` methods in 231 classes; the
  SQLite-only tests are skipped on MariaDB, so the number Maven prints can be lower. Write the number you see in the
  "Release status" block of `progress.md`.
  Known until TODO 3.42 is fixed: `DashboardStatsServiceTest.theLastSevenDaysIncludeTodayAndNoNegativeRevenue` can fail
  when it runs after `EdgeCasesTest` in the same JVM (a test order that Maven may or may not use); that is a code gap in
  the 7-day chart, not a broken build. Fix it before tagging.
- Walk through section 0 of `TODO.md`: every check that touches this release is done or consciously skipped.
- Before the shop opens for real: the owner tried the till for a week, so open Settings and press "Bắt đầu bán thật" ("Start selling for real"). It takes a backup (`before-opening-...` in the backups folder), asks for the shop name, then removes the test orders, payments, refunds, stock log, expenses, shift closes, clock-ins, bookings, gift cards and audit log, and keeps the menu, tables, staff and settings (`docs/DATABASE.md`, last section). Do it once, with every other till closed, and check the stock numbers afterwards: they are not reset.
- Fill in `CHANGELOG.md` first: rename its *Unreleased* section to the version and the date (the file says how), and say there if the version upgrades the database.
- Tag the commit, for example `git tag v0.9`.
- Keep the previous version's zip until the new one has run for a week.

## 5. If it goes wrong

Close the app, put the backup from step 1 back and go back to the previous version:

- SQLite: the restore button, or replace `data/lunavera.db` with the one inside the zip **and delete `data/lunavera.db-wal`
  and `data/lunavera.db-shm` if they exist** (a leftover log file belongs to the other version of the database).
- MariaDB: import the old `lunavera.sql` again in phpMyAdmin (step 3; the import replaces the tables) and put the old
  `images` folder back.

A database that a newer version has already upgraded is refused by an older one
("thuộc về một phiên bản Lunavera mới hơn"), so the old version needs the **old data**, not the upgraded copy.

## The version number

The program shows the `<version>` of `pom.xml` on the login screen and in Settings and writes it in the log (TODO 5.41). Raise it by hand when a release is cut, together with `project.build.outputTimestamp` (the build date shown comes from that), and tag the same number. `1.0-SNAPSHOT` is what a build says until then. Run from an IDE the program says `dev`.
