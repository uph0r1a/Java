# Third-party notices

Lunavera Coffee's own code, tests and documents are under the [MIT licence](LICENSE). The program that is handed out (the zip and the
installer) also contains work by other people, under their own licences. This file names it, says where each licence can be read and
where the source is. It is kept next to `LICENSE` in the repository and in the packaged folder. The list of every library and its exact
version in a given release is the file `lunavera-bom.json` that comes with it (a CycloneDX bill of materials, TODO 6.79).

Written 2026-11-06 from the pom and the licences' public descriptions. **Not legal advice**: it has not been read by a lawyer, and the
points marked *(to confirm)* need one before the program is handed to people outside the shop.

## What is inside the program

| Part | Version | Licence | Source and licence text |
| --- | --- | --- | --- |
| **OpenJFX** (`javafx-controls`, `javafx-fxml`, `javafx-media`, and the `javafx-base` / `javafx-graphics` they bring) | 21.0.6 | GNU General Public License v2 **with the Classpath Exception** | https://github.com/openjdk/jfx — the licence files are in each JavaFX jar and, in a packaged image, in its `legal` folder *(to confirm: not opened here, see below)* |
| **Java runtime** (the private runtime that `jlink` builds into the zip and installer) | JDK 21 (Temurin from the release workflow) | GNU General Public License v2 **with the Classpath Exception** | https://adoptium.net and https://openjdk.org; the image's own `legal` folder holds the notices of the modules in it |
| **MariaDB Connector/J** (`mariadb-java-client`) | 3.4.1 | GNU Lesser General Public License v2.1 | https://github.com/mariadb-corporation/mariadb-connector-j |
| **SQLite JDBC driver** (`sqlite-jdbc`, by Xerial) | 3.46.1.3 | Apache License 2.0 | https://github.com/xerial/sqlite-jdbc |
| **SQLite** (the database engine inside that driver) | the version the driver carries | public domain | https://www.sqlite.org/copyright.html |
| **SLF4J API** (`slf4j-api`) | 2.0.16 | MIT | https://www.slf4j.org/license.html |
| **Be Vietnam Pro** (the three font files in `src/main/resources/fonts/`) | 2021 | SIL Open Font License 1.1 | `src/main/resources/fonts/OFL.txt` (kept with the fonts), https://github.com/bettergui/BeVietnamPro |
| **Common-password list** (`src/main/resources/passwords/common-passwords.txt`, about 4,000 lines, TODO 2A.47) | 2026-11-07 | the 10,000-most-common list is MIT (Copyright (c) 2018 Daniel Miessler); the 4,000 entries taken from the NCSC "100k most used passwords" list contain public sector information under the Open Government Licence v3.0 *(to confirm: the licence of that file as SecLists redistributes it; the list can be shortened to the MIT part alone without changing a line of code)* | https://github.com/danielmiessler/SecLists (`Passwords/Common-Credentials/`); https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/ |

Notes on the rows:

- **OpenJFX.** The Classpath Exception lets a program that uses JavaFX be under another licence (here MIT). The JavaFX natives
  include third-party code under its own licences (for example the media module's GStreamer and GLib, which are LGPL, and the
  graphics module's font and image libraries); OpenJFX lists them in the licence files it ships. Whether `jlink` copies those files
  into the image's `legal` folder was not checked, because no image was built with the media module (`docs/PACKAGING.md`). *(To
  confirm, before a release: open `target/lunavera/legal/` and check it holds a folder for each of `javafx.base`, `javafx.graphics`,
  `javafx.controls`, `javafx.fxml` and `javafx.media`. If not, the release workflow has to copy them from the jars.)*
- **MariaDB Connector/J (LGPL).** The LGPL asks that a person who has the program can get the library's source and replace the
  library with their own build. The source is at the address above and is the exact version in the table. Replacing it is possible
  two ways: run the program from source (`./mvnw javafx:run`, with another version in `pom.xml`), or build a new zip with
  `./mvnw -Pmodular-image package` (`docs/PACKAGING.md`). Replacing it inside an already built image is not offered. *(To confirm: whether that satisfies the licence for a
  program that is handed to other people; an adviser should read the LGPL for this.)*
- **SQLite JDBC.** The Apache licence asks that its licence text and the notices travel with the driver. The driver's jar carries its own
  `LICENSE` and `NOTICE` files; they stay inside the jar and so inside the image.
- **Common-password list.** Only the passwords themselves are used, as a list to refuse; no code of SecLists is in the program. The MIT notice (copyright line and permission text) must travel with the list: the first lines of the file name the source and `LICENSE` of SecLists is at the address above. The program never sends the list anywhere.
- **Be Vietnam Pro.** The font may be bundled with a program and used in it; it may not be sold on its own, and a changed version may not
  use the name "Be Vietnam Pro". The files here are unchanged.

## Used to build and test, not handed out

JUnit 4.13.2 (Eclipse Public License 1.0) and Hamcrest 1.3 (BSD 3-Clause) run the tests; Maven and its plugins, JaCoCo, PIT, SpotBugs and
CycloneDX run on the builder's machine. None of them is in the zip or the installer.

## Made for this program, not covered by the table

- **The two alert sounds** (`src/main/resources/sounds/`) were written for this program as plain generated tones (TODO 5.47) and are under the MIT licence with the code.
- **The starter menu photos, the logo and the login background** are **not** under the MIT licence and have no recorded source: see
  "Pictures and fonts: where they come from" in `README.md`. Three of the photos carry an AI-tool watermark. Until the owner fills that
  in, they must not be handed out as part of an open release.
