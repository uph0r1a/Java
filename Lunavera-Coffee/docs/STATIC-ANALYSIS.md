# Bug finder and a verifiable release (TODO 6.79)

Written without being able to run Maven or GitHub: every piece below is **untried until the first run**, see the manual check in `TODO.md`.

## SpotBugs with findsecbugs

- Run it: `./mvnw -B -Pbugs compile spotbugs:spotbugs`. The report is `target/spotbugsXml.xml`; `./mvnw -Pbugs spotbugs:gui` opens it.
  The profile `bugs` in `pom.xml` is not part of the normal build.
- CI: the job `bugs` in `.github/workflows/ci.yml` runs the same and uploads the report as the artifact `spotbugs-report`. It is
  `continue-on-error`: it **reports and never fails the build** at first.
- findsecbugs is in the same run. Read its `SQL_INJECTION*` findings once: the data layer builds statements from constant text (table and
  column names from `SqliteSchema` / `MariaDbSchema`, `SqlStatus`), and values always go through `?`. A finding that is a constant is
  not a bug; add a `<Match>` with the reason to `spotbugs-exclude.xml` so it does not come back. A finding with typed or stored text
  inside the SQL is a real one (and breaks the rule in `GroupByRulesTest`'s neighbours): fix it.
- **When to make it fail the build:** after the first run, when the list is short. Then set `<failOnError>true</failOnError>` and
  `<threshold>High</threshold>` first (null dereference, unclosed resource, ignored result), and lower the threshold later.

## A release file that can be checked

- **SHA-256:** the release workflow writes `SHA256SUMS.txt` with a line for every file (the installer, `lunavera.zip` and the bill of
  materials). Check a download:
  - Windows: `Get-FileHash .\lunavera.zip -Algorithm SHA256`
  - Linux, Mac: `sha256sum lunavera.zip`
  The value must equal the line for that file in `SHA256SUMS.txt`, which you got from the release page, not from the same place as the file.
- **Bill of materials:** `lunavera-bom.json` (CycloneDX) lists every library inside the program and its version. When a warning about
  a library comes out (for example one for the SQLite driver), search this file for its name and version to see whether that release has it.
  The scheduled `dependency-scan.yml` looks at the pom; this file is what was really put in the release.
- **Same bytes twice:** `project.build.outputTimestamp` in `pom.xml` (and, in the release workflow, the time of the commit) makes the
  **jar** the same bytes on every build of the same source. The jlink folder and its zip are **not** claimed to be reproducible (they
  hold files from the JDK build and the zip's own order and times), so compare the jar, not the zip, to prove two builds equal.
  When a release is cut, raise the date in `pom.xml` by hand.
