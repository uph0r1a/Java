# Contributing to Lunavera Coffee

This file says how to change the program without breaking it. It only collects what the project already documents; each
point names the place where the full rule is.

## 1. Run the tests

```
./mvnw -B clean test        # the real answer; needs JDK 21 and the libraries (on Windows: mvnw.cmd)
./mvnw -B clean verify      # the tests plus the coverage report and the floor per package
```

Use `./mvnw`, not a bare `mvn`: the wrapper downloads the Maven version the project needs. The tests run on SQLite and need no
database server; `-Dlunavera.test.db=mariadb` runs the database tests on a real MariaDB instead (`docs/DATABASE.md`).

Where Maven cannot run (no network, no libraries), run the **library-free check** instead:

```
tools/run-library-free-tests.sh
```

It needs only the JDK and the JUnit jars, takes about a minute, compiles the sources and runs every test that needs neither JavaFX
nor a database driver. It must end with `0 FAILED`; it also says how many tests it could not run, and you say so in your notes.
It does not replace `./mvnw -B clean test` (`docs/RELEASE.md`, section 0).

## 2. Rules for the code

- **No controller over 900 lines, and one controller per screen.** The admin screens extend `AdminScreen`; new screen code goes in
  that screen's own controller. `AdminScreensTest` fails when this is broken.
- **No DAO in a screen.** A screen asks the person, calls a service inside `Async.run(...)` and shows the result; rules and
  permission checks live in `service/`, SQL lives in `dao/`. `ScreensUseServicesTest` and `DatabaseOffTheScreenThreadTest` check it.
- **Never log customer or staff details.** Log ids and counts, not names, phone numbers, addresses, passwords or reasons.
- **Status values in SQL come from `SqlStatus`; dates and times in the data code come from `AppClock`.**
- **Text goes in both `messages_vi.properties` and `messages_en.properties`** with the same keys; `I18nTest` and `MessagesTest` fail
  on a missing, empty or unused key.
- **A table or column goes in all three of** `SqliteSchema`, `MariaDbSchema` and `SqliteToMariaDbImporter`, (a new column
  also needs a numbered migration step and a version change; a new table does not: `docs/DATABASE.md`).
- **Never commit `data/`, `*.db*` or `database.properties`.**

The full list, with the reason for each, is "Rules when changing the code" in [`README.md`](README.md).

## 3. Every fix comes with a test

Turn each reproduced bug into a test that fails without the fix (`TODO.md`, item 6.32). Tests that need a database use
`TestDatabaseSupport` (`freshDay()`, `ownTable()`) so that they pass in any order; do not claim a day or a dining table that another
test may use.

## 4. Keep the notes true

At the end of every piece of work (`TODO.md`, item 6.8):

- `progress.md`: add a section with a **new, higher** session number, and say what was run and what was not.
- `TODO.md` and `COMPLETE.md`: an open item stays in `TODO.md`; a finished one moves to `COMPLETE.md` with a dated note. Do not tick
  an item that waits for a person to try it: mark it `[~]`. Update the counts in the first paragraph of `TODO.md`.
- `CHANGELOG.md`: one line under *Unreleased* for anything a person at the till or the owner would notice (`### Added`, `Changed`,
  `Fixed`, `Security`, `Removed`).

`NotesConsistencyTest` checks the counts, the session numbers and the files' agreement with each other.

## 5. Open a pull request

`.github/pull_request_template.md` asks what changed, how it was checked, which shared files were touched and what is **not**
done. Fill it in honestly: "not seen in a window" is a fine answer.

## 6. Releasing

Follow [`docs/RELEASE.md`](docs/RELEASE.md): back up first, try the upgrade on a copy, test a restore. Building the installer is in
[`docs/PACKAGING.md`](docs/PACKAGING.md), signing in [`docs/SIGNING.md`](docs/SIGNING.md). Rename *Unreleased* in the changelog and tag
the same commit (`CHANGELOG.md`, "How to add a version").
