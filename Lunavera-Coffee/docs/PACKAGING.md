# Packaging, where the data goes, and upgrading

TODO 6.15. **Written by reading `pom.xml`, `module-info.java` and the data-folder code. No package was built for this note
(the machine it was written on has no Maven and no network), so every command below is untried: do the "first real build"
steps once and correct this file with what really happened.**

## What the project can build today

- `mvn clean test` runs the tests (JDK 21; the CI runs it on Ubuntu and Windows).
- `mvn javafx:run` starts the app from the project folder (the normal way while the shop is being set up).
- The `javafx-maven-plugin` entry in `pom.xml` also carries jlink settings: launcher name `app`, image folder `app`,
  zip `app`, no man pages, no header files, debug info stripped, main class
  `com.coffeeshop.coffeeshopmanagement/com.coffeeshop.coffeeshopmanagement.LunaveraApplication`. So
  `mvn clean javafx:jlink` is expected to produce `target/app/` (a small private Java runtime plus a launcher in
  `target/app/bin/`) and `target/app.zip`. There is **no jpackage / installer** configuration: a Windows `.exe` or `.msi`
  installer does not exist yet, only this folder or zip.
- jlink only accepts real modules. `module-info.java` names `org.xerial.sqlitejdbc`, `org.mariadb.jdbc` and `org.slf4j`;
  if the build stops with "automatic module" or "module not found", that is the place to look (the comment in
  `module-info.java` about the SQLite driver's module name says the same).

## Where the data goes once packaged

The data folder is **not** inside the packaged app. `DataPaths.dataDir()` picks, in this order:

1. the Java property `-Dlunavera.dataDir=...`,
2. the environment variable `LUNAVERA_DATA_DIR`,
3. otherwise `data/` **relative to the folder the program is started from**.

So a shortcut to `target/app/bin/app` with an empty "Start in" creates a brand-new empty `data/` somewhere else, and the
shop thinks its data is gone (TODO 3.32 shows a "new database" message at least). For a packaged copy always set one of the
first two, for example a shortcut whose target sets `LUNAVERA_DATA_DIR=D:\LuneveraData` first, and tell the shop that
`D:\LuneveraData` (not the program folder) is what must be backed up. With MariaDB the shop's real data is on the server;
`data/` still holds pictures, logs, backups and `database.properties`.

## How an upgrade keeps the database

- The program never overwrites `data/`. Replacing the program folder (new zip over the old one, or a new `target/app`) leaves
  the data alone as long as `LUNAVERA_DATA_DIR` / `lunavera.dataDir` points at the same place.
- At the next start the new version upgrades the tables by itself: SQLite through `user_version`
  (`MoneyMigration`, then `SchemaMigrations.SQLITE_STEPS`), MariaDB through the `schema_version` table
  (`MariaDbMigrations.STEPS`). A database that is **newer** than the program is refused with a message, so going back to an
  older version needs the old data (see `docs/RELEASE.md`, "If it goes wrong").
- The code does not copy the database before an ordinary upgrade. Back up first: `docs/RELEASE.md`.

## First real build and first real upgrade (do these once, then fix this file)

1. Make a backup of the shop's data (`docs/RELEASE.md`, step 1).
2. `mvn clean javafx:jlink` on a Windows machine with JDK 21; note any error and the size of `target/app.zip`.
3. Unzip somewhere else, set `LUNAVERA_DATA_DIR` to a **copy** of the data, start `bin/app`, and check: login, one report,
   pictures show, one test sale, the data folder named on the screen is the copy.
4. Install the previous version's package and the new one side by side, start the previous one on a copy of the data, then the
   new one on the same copy: it must open and upgrade without a message about a newer version.
5. Write what you did, the dates and the sizes here.
