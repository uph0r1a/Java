# Before a release, an upgrade or a database change

A short checklist (TODO 6.9). Last brought up to date on 2026-10-17 (see `progress.md`, Session 83). This is the only copy: `docs/RELEASE.md`.

## What the code already does, and what it does not

The code makes safety copies by itself in these places:

- **Both kinds of shop:** an automatic backup `data/backups/auto-<time>.zip` (database and pictures; only every 7th one holds the pictures, the others are `auto-<time>-db.zip`) at start-up and then
  hourly checks, one at most every 24 hours, newest 14 kept. On MariaDB the zip holds `lunavera.sql` made by `mysqldump`,
  which must be installed on the till computer (`db.mysqldump` in `data/database.properties` if it is not on the PATH).
- **SQLite shop:** every restore first saves `before-restore-<time>.zip` (newest 3 kept). A one-time
  `before-money-migration.db` is made for the oldest databases.
- **MariaDB shop:** also `data/snapshots/lunavera-<time>.db` (a SQLite copy of every table, **without** the pictures,
  hourly, newest 10 kept). The in-app restore does **not** work on MariaDB (TODO 1.8): see step 3.

**Nothing makes a copy before an ordinary new version or a new numbered migration** (`SchemaMigrations.SQLITE_STEPS`,
`MariaDbMigrations.STEPS`; the table-layout version is 2 today), so do step 1 by hand.

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

- `./mvnw -B clean test` passes on Windows and Linux (the CI runs both). The project holds 1460 `@Test` methods in 213 classes; the
  SQLite-only tests are skipped on MariaDB, so the number Maven prints can be lower. Write the number you see in the
  "Release status" block of `progress.md`.
  Known until TODO 3.42 is fixed: `DashboardStatsServiceTest.theLastSevenDaysIncludeTodayAndNoNegativeRevenue` can fail
  when it runs after `EdgeCasesTest` in the same JVM (a test order that Maven may or may not use); that is a code gap in
  the 7-day chart, not a broken build. Fix it before tagging.
- Walk through section 0 of `TODO.md`: every check that touches this release is done or consciously skipped.
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
