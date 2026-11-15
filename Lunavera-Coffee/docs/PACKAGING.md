# Packaging, where the data goes, and upgrading

TODO 6.15. **Partly tried on 2026-11-03** (see "What was really run" at the end): a real `jlink` image was built and started, and one
upgrade with a backup and a restore was run. `./mvnw javafx:jlink` itself, Windows and MariaDB were **not** tried (the machine had no
access to Maven Central). Everything above that last section is still read from `pom.xml`, not run.

**Session 243 (2026-11-08):** nothing could be built here either (no JavaFX or JDK jmods, no Maven, no network), so the real
`./mvnw javafx:jlink` build and the Windows upgrade are **still not done**. What was added is the check that tells, in seconds and before
that build, whether `jlink` can work at all: `tools/ModuleCheck.java` (section "Before the first build: check the modules"). Reading the
project with it in mind found one **expected** blocker, the MariaDB driver (section "If the check lists an automatic module"). It is an
expectation from the driver's manifest as remembered, not a result: the jar was not available to look at.

**Session 252 (2026-11-08):** the way out chosen for the MariaDB driver is written into the pom as the profile `modular-image` (moditect
gives the driver a module descriptor, moditect links the folder), `release.yml` builds with it and runs `tools/ImageCheck.java` before
the installer. **Still untried:** no Maven, no network and no JDK `jmods` here, so the plugin versions and settings were written from
memory and the profile has never run. See "If the check lists an automatic module" for what to run and what to do when it stops.

## What the project can build today

- `./mvnw clean test` runs the tests (JDK 21; the CI runs it on Ubuntu and Windows).
- `./mvnw javafx:run` starts the app from the project folder (the normal way while the shop is being set up).
- The `javafx-maven-plugin` entry in `pom.xml` also carries jlink settings: launcher name `lunavera`, image folder `lunavera`,
  zip `lunavera`, no man pages, no header files, debug info stripped, main class
  `com.coffeeshop.coffeeshopmanagement/com.coffeeshop.coffeeshopmanagement.LunaveraApplication`. So
  `./mvnw clean javafx:jlink` is expected to produce `target/lunavera/` (but is expected to stop at the MariaDB driver, see below; the profile `modular-image` makes the same folder) (a small private Java runtime plus a launcher in
  `target/lunavera/bin/`) and `target/lunavera.zip`. The pom has no jpackage / installer configuration; a Windows `.msi` is
  built by the GitHub workflow `.github/workflows/release.yml` (TODO 2A.6), which also signs it when a certificate is set up and
  writes SHA-256 fingerprints: see `docs/SIGNING.md`. That workflow has never been run, so the installer is untried.
- jlink only accepts real modules (a jar with a `module-info.class`); an "automatic module" stops the build. `module-info.java` names
  `org.xerial.sqlitejdbc`, `org.mariadb.jdbc` and `org.slf4j`. The 2026-11-03 hand-made image proved it for `sqlite-jdbc` and `slf4j`
  and left `org.mariadb.jdbc` out, so that one was never tried. `tools/ModuleCheck.java` checks all of them in seconds: see
  "Before the first build: check the modules" below. (The `javafx-maven-plugin` 0.0.8 settings in the pom were compared with the plugin's
  parameter names: `mainClass`, `launcher`, `jlinkZipName`, `jlinkImageName`, `noManPages`, `stripDebug`, `noHeaderFiles` are all real
  ones. They sit in the `default-cli` execution, which is the one `./mvnw javafx:jlink` typed on the command line uses.)
- **`javafx.media` (TODO 5.47).** The optional alert sounds use `javafx.scene.media.AudioClip`, so `module-info.java` has `requires javafx.media;`
  and the pom has the `javafx-media` dependency (same version as the other JavaFX jars). jlink takes the module from the `requires` line, so
  the image grows by the media module and its native libraries (on Windows a few MB; check `target/lunavera.zip`). The sounds are two
  files in `src/main/resources/sounds/`; the player copies one to a temporary file before playing it, because JavaFX media cannot open the
  `jrt:` address a resource has inside a jlink image. **Not tried:** a real `javafx:jlink` build with the module, and a sound played from an
  image or on Windows. If the module cannot be had on a machine, the program still starts and stays silent (the player catches the error).

## Where the data goes once packaged

The data folder is **not** inside the packaged app. `DataPaths.dataDir()` picks, in this order:

1. the Java property `-Dlunavera.dataDir=...`,
2. the environment variable `LUNAVERA_DATA_DIR`,
3. `data/` in the start-up folder, **only if it already holds a shop** (`lunavera.db` or `database.properties`): a copy installed
   before TODO 3.69, or the project folder with `./mvnw javafx:run`,
4. the folder this computer used last time, remembered in the one-line file `.lunavera-data-folder` in the user's home folder
   (written once the database is open),
5. otherwise a fixed folder of this user: `%LOCALAPPDATA%\Lunavera` on Windows, `~/Library/Application Support/Lunavera` on macOS,
   `~/.local/share/lunavera` elsewhere.

So a shortcut to `target/lunavera/bin/lunavera` with an empty "Start in" no longer starts a second, empty shop: it finds the
remembered one, and on a new computer it uses the fixed folder. The program folder is never where the data goes. If a shortcut sets
1 or 2 to an empty folder while a remembered shop exists, the program asks before it creates a new, empty shop there (default: No,
it closes). To keep the data on another disk, set `LUNAVERA_DATA_DIR=D:\LuneveraData` in the shortcut, and tell the shop that
`D:\LuneveraData` (not the program folder) is what must be backed up. With MariaDB the shop's real data is on the server;
`data/` still holds pictures, logs, backups and `database.properties`.

## How an upgrade keeps the database

- The program never overwrites `data/`. Replacing the program folder (new zip over the old one, or a new `target/lunavera`) leaves
  the data alone as long as `LUNAVERA_DATA_DIR` / `lunavera.dataDir` points at the same place.
- At the next start the new version upgrades the tables by itself: SQLite through `user_version`
  (`MoneyMigration`, then `SchemaMigrations.SQLITE_STEPS`), MariaDB through the `schema_version` table
  (`MariaDbMigrations.STEPS`). A database that is **newer** than the program is refused with a message, so going back to an
  older version needs the old data (see `docs/RELEASE.md`, "If it goes wrong").
- The code does not copy the database before an ordinary upgrade. Back up first: `docs/RELEASE.md`.

## Before the first build: check the modules (2 minutes, JDK only)

```
./mvnw -B dependency:copy-dependencies -DoutputDirectory=target/deps
java tools/ModuleCheck.java target/deps
```

(On Windows: `mvnw.cmd` and `java tools\ModuleCheck.java target\deps`.) The tool reads the `requires` lines of
`src/main/java/module-info.java`, resolves them over the jars in the folder the way the module system does, and prints one line per
jar: `REAL` (has a module descriptor), `AUTOMATIC` (only a name, from its manifest or its file name) or `NO-MODULE`. At the end it says
`OK` (exit code 0) or `BLOCKED` with the names of the automatic modules the program needs, directly or through another module (exit
code 1). `ModuleCheckTest` shows it tells real from automatic, follows a real module that needs an automatic one (as `sqlite-jdbc`
needs `slf4j`), and ignores comments and `java.*` / `jdk.*` lines. It was run here only on small jars made by the test, **not** on the
project's real dependencies.

## If the check lists an automatic module

**Expected, not verified:** `mariadb-java-client` 3.4.1 (the version in the pom). As far as its manifest is remembered, it only
declares `Automatic-Module-Name: org.mariadb.jdbc` and has no `module-info.class`. If `ModuleCheck` prints `AUTOMATIC org.mariadb.jdbc`,
then `./mvnw javafx:jlink` will fail with a message that an automatic module cannot be used with jlink. The 2026-11-03 build did not see this
because it removed the `requires org.mariadb.jdbc;` line. (A newer connector may ship a descriptor of its own: if `ModuleCheck` on a newer
version says `REAL`, the profile below is not needed for it.)

### The profile `modular-image` (session 252, never run)

```
./mvnw -B -DskipTests -Pmodular-image clean package
java tools/ModuleCheck.java target/modules
java tools/ImageCheck.java target/lunavera
```

Build on the operating system the folder is for (the JavaFX jars copied are the ones for the system Maven runs on), with a JDK 21 that
has its `jmods` folder (Temurin has). What the profile does, in this order, all in the `package` phase:

1. `maven-dependency-plugin` copies the runtime jars (JavaFX, `sqlite-jdbc`, `slf4j-api`) to `target/modules/`, **without** the MariaDB jar.
2. `moditect-maven-plugin` `add-module-info` writes the MariaDB jar with a module descriptor to the same folder: the name
   `org.mariadb.jdbc` (what `module-info.java` requires), every package exported, `requires` worked out with jdeps, a `uses` line for every
   `ServiceLoader` the connector calls (its plugins) and the service line `provides java.sql.Driver` (what lets `DriverManager` find it).
3. `create-runtime-image` links `target/lunavera/` from `target/modules/` and the program's own jar, with the launcher `bin/lunavera`
   (module and main class), stripped of debug information, man pages and headers.
4. `maven-antrun-plugin` zips the folder to `target/lunavera.zip`.

`ImageCheck` then asks the image's own `bin/java` for its modules and checks that both drivers say `provides java.sql.Driver`, that the
launcher exists, and prints the size. It does not start the program (that needs a screen): step 4 of the list below does.

**What may go wrong, and what to do** (all guesses until it has run):

- *Maven cannot find a plugin version* (`moditect-maven-plugin` 1.2.2.Final, `maven-dependency-plugin` 3.8.1, `maven-antrun-plugin` 3.1.0):
  take the newest from Maven Central and put it in the pom.
- *`ImageCheck` says `NO DRIVER org.mariadb.jdbc`*: the generated descriptor has no `provides` line. In the pom replace the `moduleInfo`
  block of that module with a `moduleInfoSource` holding `module org.mariadb.jdbc { requires java.sql; requires java.naming; exports
  org.mariadb.jdbc; provides java.sql.Driver with org.mariadb.jdbc.Driver; }` plus a `uses` line for each interface the connector loads
  (`jdeps` or `jar tf` on the jar shows them), then build again.
- *`ImageCheck` prints a `WARNING` about `uses`*: the connector may load its plugins with `ServiceLoader` and the descriptor does not
  say so; connecting to MariaDB would then fail. Test a real connection from the packaged program (step 4 below) before trusting it.
- *Moditect rejects a setting* (`jarInclusionPolicy`, `addServiceUses`, the launcher written as `module/class`): remove or change that
  one setting and read the plugin's documentation for the version in use.
- *Moditect cannot be made to work at all:* link by hand from what the profile already prepared (`;` instead of `:` on Windows):
  `jlink --module-path target/modules:target/coffee-shop-management-1.0-SNAPSHOT.jar --add-modules com.coffeeshop.coffeeshopmanagement
  --launcher lunavera=com.coffeeshop.coffeeshopmanagement/com.coffeeshop.coffeeshopmanagement.LunaveraApplication --strip-debug
  --no-man-pages --no-header-files --output target/lunavera`.

The MariaDB connector is LGPL (`THIRD-PARTY-NOTICES.md`): the image holds its jar with one added file, `module-info.class`; the library's
own code is unchanged.

### If the profile cannot be made to work

2. **Leave MariaDB out of the packaged image:** remove `requires org.mariadb.jdbc;`, so the packaged program works with the local SQLite
   file only (TODO 5.40 already offers the file when no server is set up), and a shop that needs MariaDB keeps starting the program with
   `./mvnw javafx:run`. Quick, but it is a decision about the product: MariaDB is the default database today.
3. **Run from the class path** (jpackage with `--input` and the jars, no module path for the drivers). Not looked at in any detail.

If the check lists another automatic module (a new dependency), the same ways apply to it.

## First real build and first real upgrade (still to do on the shop's Windows machine)

1. Make a backup of the shop's data (`docs/RELEASE.md`, step 1).
2. Run the module check above (on `target/deps`) and read its last line: it is expected to say `BLOCKED` for `org.mariadb.jdbc`, which is what the profile in the next step works around.
3. `./mvnw -B -DskipTests -Pmodular-image clean package` on a Windows machine with JDK 21, then `java tools/ModuleCheck.java target/modules` and
   `java tools/ImageCheck.java target/lunavera`; note any error and the size of `target/lunavera.zip`.
4. Unzip somewhere else, set `LUNAVERA_DATA_DIR` to a **copy** of the data, start `bin/lunavera`, and check: login, one report,
   pictures show, one test sale, the data folder named on the screen is the copy.
5. Install the previous version's package and the new one side by side, start the previous one on a copy of the data, then the
   new one on the same copy: it must open and upgrade without a message about a newer version.

## What was really run (2026-11-03, TODO 6.15)

**Machine:** Linux, OpenJDK 21.0.12 with its `jmods`, a virtual screen (Xvfb, software rendering). No Maven Central, no JavaFX 21
jmods, no MariaDB driver, no Windows. So this is the same `jlink` idea done by hand, not the pom's `javafx:jlink`.

**What differed from the real build (so a result here is not a promise for the real one):**
JavaFX **11** modules (`javafx.base/controls/fxml/graphics`) instead of 21.0.6 (the log says "FXML document with JavaFX API of
version 21 by JavaFX runtime of version 11"; two 21-only lines in `AlertUtil` were switched off in the build copy); `slf4j-api` 1.7.36
with a `module-info.class` added by hand (the 2.0.16 jar the pom uses has its own); the `requires org.mariadb.jdbc;` line removed in the
build copy (the driver was not available), so MariaDB was not tried; the JavaFX native libraries were handed to the image with
`-Djava.library.path` (the Maven JavaFX jars carry them inside; here they come from the Ubuntu package).

**Build (worked first time, 2 seconds):**

```
javac --module-path <javafx jars>:sqlite-jdbc.jar:org.slf4j.jar -d app $(find src/main/java -name '*.java')   # with module-info.java
cp -r src/main/resources/. app/
jlink --module-path $JAVA_HOME/jmods:<javafx jars>:sqlite-jdbc.jar:org.slf4j.jar:app \
      --add-modules com.coffeeshop.coffeeshopmanagement \
      --launcher lunavera=com.coffeeshop.coffeeshopmanagement/com.coffeeshop.coffeeshopmanagement.LunaveraApplication \
      --no-man-pages --no-header-files --strip-debug --output lunavera
```

Result: `lunavera/` **130 MB**, zipped **51 MB**; modules: the app, `org.xerial.sqlitejdbc`, `org.slf4j`, four JavaFX modules and
`java.base, java.desktop, java.logging, java.naming, java.prefs, java.scripting, java.sql(.rowset), java.xml, jdk.unsupported` and
friends. No "automatic module" error with `sqlite-jdbc` 3.46.1.3 (it ships a real descriptor). `bin/lunavera` starts, the login screen
shows its pictures and fonts (so `resources/` is inside the image), the footer names the data folder and "cấu trúc v5".

**Things found:**
1. **The packaged app needs `data/database.properties` with `db.type=sqlite` (or a MariaDB server).** MariaDB is the default
   (`DatabaseSettings`), so on a brand-new data folder the first start shows the "cannot open the database" box with
   "No suitable driver" / "connection refused". Its text blames a file locked by another program, which is not the cause here
   (Since TODO 5.40 the box also offers the local file when there is no `database.properties` and no server.)
2. **A bug of the 2.99 dialog helper, found only by looking at the real window and fixed:** the long start-up message was white
   on the light dialog (the scroll box set `-fx-background: transparent`, from which the theme works out the text colour).
   `DialogFit.scrolling` now makes only the box and its viewport transparent.
3. Started from another folder with no `LUNAVERA_DATA_DIR`, the image made a new `data/` in that folder (as written above).

**Upgrade with a backup (SQLite, data made by the previous version's own code):**
the previous version = the 6.5 sources, the new one = the sources with 2.96, 4.49, 2.99 and 4.48. Each as its own image.
1. Previous image's code made a data folder: owner account, 33 starter products, 12 tables, 3 paid orders (one at a table), 1 open
   take-away order (4 orders, 480.000 đ), then `BackupService.createBackup` wrote `before-upgrade.zip` (2.7 MB).
2. Previous image started on a copy: login screen, "cấu trúc v3", an automatic backup written.
3. New image started on the same copy: login screen, no message. Afterwards the database had the same 4 orders and 480.000 đ,
   the new `orders.channel` column and the new `stock_takes` table; `user_version` still 3 (these changes only add columns and
   tables, `addColumnIfMissing` / `CREATE TABLE IF NOT EXISTS`).
4. Restore: two orders were deleted from a copy, `BackupService.scheduleRestore(before-upgrade.zip)` was run, the new image
   started: "Restored from the pending backup", the 4 orders were back, the new columns were added again on top, and the
   data from before the restore was kept in `backups/before-restore-....zip`.
5. The previous image started on the **upgraded** data: it opened with no error (nothing it reads was removed). That will not hold
   for a change that removes or renames a column; this list does not say so for later versions.

**Not tried:** `./mvnw javafx:jlink` and the javafx-maven-plugin, Windows (the launcher is `bin/lunavera.bat` there), JavaFX 21,
the MariaDB driver in the module path, MariaDB upgrade steps, a login and a sale in the packaged window (the screens were looked
at, not clicked), an installer (none exists), the size with the real JavaFX jars (their natives are bundled, so larger).
