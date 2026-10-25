# Lunavera Coffee

A desktop coffee-shop point of sale (JavaFX 21; MariaDB/MySQL with phpMyAdmin by default, SQLite as an option). The screens are in Vietnamese.

## Run and test

```
./mvnw javafx:run   # start the app (run it from this folder; on Windows: mvnw.cmd javafx:run)
./mvnw test         # run the tests
./mvnw verify       # the tests plus a coverage report: open target/site/jacoco/index.html
```

`mvnw` is the Maven wrapper: it downloads the Maven version the project needs on the first run, so a separate Maven install is not required (JDK 21 is). If you do have Maven, `mvn` works the same way.

Needs JDK 21. First start: when the old SQLite data is carried over (`db.import.sqlite`, see below) you sign in with your **old** admin account. On a completely new database the app asks the owner to create the first account (name, username, password); there is no built-in password. Old installs that still have the old default password are made to change it at login. Anyone can change their own later with the "Đổi mật khẩu" button at the bottom of the sidebar.

Every push to GitHub also runs `./mvnw -B clean verify` (the tests and the coverage report) on Linux and Windows (`.github/workflows/ci.yml`); the Linux run keeps the coverage report as a download.

## Language (Vietnamese / English)

The screens, pop-ups, receipts and messages are in Vietnamese or English. Choose at the top right of the login screen; the choice is
remembered per computer in `ui-settings.properties` in the data folder, so each till can use its own language. Nothing the shop typed is
translated (customer and staff names, products and categories the shop added or renamed), and money, dates and the codes stored in the
database (`ADMIN`, `PAID`) are the same in both languages. The **starter menu** has English names (`config/SeedNames`): while English is
chosen, the POS, the dashboard, the revenue report and the receipt show them for the starter products that still have their starter name;
the Products and Categories management screens always show the stored names, because that is where they are edited. **Receipts are always
in English**, on the receipt window and on paper, whatever language the rest of the app is in (`util/ReceiptText`). The language switch is
only on the login screen.

For developers: all text is in `src/main/resources/i18n/messages_vi.properties` (the default and the fallback) and `messages_en.properties`
(UTF-8). Code asks for text with `I18n.t("key")` or `I18n.t("key", arg0, arg1)` (`{0}`, `{1}` are filled with the arguments); FXML files use
`%key` and are always loaded through `SceneNavigator.newLoader(...)`. To add or change a text, change **both** files; `I18nTest` fails when a
key is missing in one, empty, has different placeholders, is used in code or FXML but defined nowhere, or would look like a raw technical error
to `ErrorText` (such a message is replaced by the generic sentence, so never start an English message with "Could not" or "Cannot"). New
Vietnamese text typed straight into a Java string or an FXML attribute fails `UntranslatedTextTest`; see its comment for the baseline file.

## Map of the folders

| Path | What is in it |
| --- | --- |
| `src/main/java/com/coffeeshop/coffeeshopmanagement/controller/` | the screens' controllers (JavaFX); one controller per admin screen (`AdminDashboardController`, `CategoryController`, `ProductController`, `AccountController`) on a shared `AdminScreen` base, `OrderController` the sales screen |
| `src/main/java/com/coffeeshop/coffeeshopmanagement/` | `LunaveraApplication` (start-up) and `Launcher`, then: `model/` (plain data classes), `dao/` (all SQL), `service/` (rules: login, accounts, backup, reports), `config/` (database set-up, schema, migrations, starter menu), `util/` (small helpers) |
| `src/main/resources/fxml/` | the screen layouts |
| `src/main/resources/` | `css/` (look), `fonts/`, `images/` (icons, logo), `seed-menu/` (the starter menu pictures) |
| `src/test/java/` | the tests, in the same packages as the code they test |
| `docs/DATABASE.md` | setting up MariaDB / phpMyAdmin |
| `docs/RELEASE.md` | checklist before a release or an upgrade: back up, try on a copy, test a restore |
| `docs/PACKAGING.md` | building a packaged copy, where its data goes, how an upgrade keeps the database (untried) |
| `TODO.md`, `progress.md` | the to-do list with its notes, and the log of what each work session did |
| `database.properties.example` | the template for `data/database.properties` |

## Pictures and fonts: where they come from

- **Fonts** (`src/main/resources/fonts/`): Be Vietnam Pro (regular, italic, bold), under the SIL Open Font License 1.1; the licence text is
  in `fonts/OFL.txt` and must stay with the font files.
- **Starter menu photos** (`src/main/resources/seed-menu/`, 33 files: 26 `.jfif`, 6 `.jpg`, 1 `.png`): these are the shop's own set of drink and
  cake photos, copied into the project. The repository does **not** yet record who took them or on what terms they may be used.
  **Owner: fill this in before the repository is shared with anyone outside the shop:**

  | Item | Fill in |
  | --- | --- |
  | Who took or made the photos (the shop, a photographer, a designer, a website) | *(to be written)* |
  | Right to use them (own work, a written permission, a licence and its name) | *(to be written)* |
  | May they be published with the source code? (yes / no) | *(to be written)* |

  If the answer to the last row is "no", the pictures cannot go into a shared copy as they are: `MenuSeedData` names every file, so
  leaving them out needs a code change and a test run, not just deleting the folder.
- **Logo and login background** (`src/main/resources/images/LunaveraLogo.png`, `LoginBackground.png`): their origin is not recorded either;
  answer the same three questions for them.
- **Pictures added later** by the shop through the product dialog are copied into `data/images/` (never into git) under a unique name.

## Where your data is

Everything the shop saves lives in one folder, **`data/`**, next to `pom.xml`:

| Path | What it is |
| --- | --- |
| `data/lunavera.db` | the SQLite database (used only with `db.type=sqlite`; kept as the old copy otherwise) |
| `data/snapshots/` | hourly SQLite copies of the MariaDB data, newest 10 kept |
| `data/images/` | product pictures |
| `data/logs/` | log files (for finding out what went wrong); `security-YYYY-MM.log` is a separate file, one per month, kept two years, that holds only who did what (logins, locks, password changes, refused actions) |
| `data/backups/` | daily `auto-*.zip` (the newest 14 are kept; only every 7th holds the pictures, the others are `auto-*-db.zip`), `before-restore-*.zip` made before every restore (the newest 3 are kept), `before-cleanup-*.zip` made before unused pictures are deleted, and one-time copies before risky changes |
| `data/qr-transfer.png` | the bank-transfer QR picture shown in the payment dialog (set it with the admin button "Mã QR chuyển khoản", or put it there and confirm it once from that button or the payment dialog; the confirmed fingerprint is kept in the database; the picture is part of every backup) |
| `data/restore-pending.zip` | only present while a restore is waiting for the next start; the dashboard shows a banner and a cancel button |
| `data/database.properties` | optional: use a MariaDB/MySQL server instead of SQLite (see below) |

The folder is created on the first start and is **not** part of git (see `.gitignore`): it holds
customer phone numbers and password hashes, so never commit or share it.

**Who can read the folder (TODO 2A.4).** On Linux and macOS the program sets `data/` to the owner only each time it starts.
On Windows it cannot do that from plain Java, and a folder made directly under `C:\` (for example `C:\Lunavera\data`)
inherits rights that let **every Windows account on that PC** read the database, the backups and the password hashes. Keep the
program under your own user folder, or close the folder once with this command in a Command Prompt (it keeps you, the system
and the administrators, and removes everyone else; it uses the standard account ids, so it works on any Windows language):

```
icacls "C:\Lunavera\data" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" "%USERNAME%:(OI)(CI)F"
```

On a SQLite shop, backups can also be **locked with a passphrase** (dashboard, "Sao lưu dữ liệu": type a passphrase twice; the
file becomes `.lvbak`). "Khôi phục dữ liệu" asks for the passphrase. Without it the file cannot be opened, so write it down.
Set a **second place** for the automatic backups with the dashboard button "Nơi sao lưu thứ hai" (a USB stick, a folder on another PC or a synced folder; a folder inside `data/` is refused): each automatic backup is copied there as well, the newest 14 are kept, and the dashboard shows "Bản sao lưu ngoài máy gần nhất" in red after 3 days or when a copy failed. It is a setting of that till only.

The daily automatic backups in `data/backups/` stay unlocked (the program cannot ask for a passphrase at night). A MariaDB shop
restores by hand, so its backup zip stays a normal zip: keep it on a BitLocker drive or lock it with a zip tool (7-Zip, AES-256).

After a backup is saved, the box has an **Mở thư mục** button that opens the folder. When an administrator logs out or closes the program and has saved no backup by hand outside `data/` for more than 7 days (or never, on this computer), the program asks once per start whether to back up now; the automatic daily backups do not count, because they sit on the same disk as the data.

**Old versions** kept the data in `~/.lunavera-coffee` (on Windows `C:\Users\<name>\.lunavera-coffee`).
The first time a new version starts with no `data/lunavera.db`, it **copies** the database, pictures and
backups from there into `data/`. The old folder is left as it was. **Close the old app first:** if the old
database still has a `lunavera.db-journal` file beside it, the old app is open and the copy is refused until it is closed.

### The database: MariaDB / MySQL with phpMyAdmin (default)

The app uses a MariaDB/MySQL server, so the data can be edited in a web page (phpMyAdmin) and several tills can share one database.
Install the server once (XAMPP is the easy way on Windows), create the database and a user, and copy `database.properties.example` to
`data/database.properties` with the server details. Step by step, with security advice and backups: **[docs/DATABASE.md](docs/DATABASE.md)**.
The first start with a new database carries over the old SQLite data (`db.import.sqlite=data/lunavera.db`, see the guide), or, without it, creates the database tables, 12 starter dining tables (the owner can add, rename and remove them under "Quản lý bàn" on the dashboard) and the starter menu and asks the owner to create the first administrator.
While the shop runs on MariaDB the app also writes an **hourly SQLite snapshot** to `data/snapshots/` (the newest 10 are kept).
To use a local SQLite file instead of a server, put `db.type=sqlite` in `data/database.properties`.

### Keep the data safe

- `data/` is found relative to the folder the app is **started from**. Start it from the project folder
  (`./mvnw javafx:run`). For an installed or packaged copy, or an IDE run configuration that starts elsewhere,
  say where the data is: `-Dlunavera.dataDir="D:\LuneveraData"` or the environment variable `LUNAVERA_DATA_DIR`.
- `./mvnw clean`, switching git branches and rebuilding do **not** delete `data/`.
- **Do not keep the program or `data/` inside OneDrive, Dropbox or Google Drive.** A sync program can copy, lock or rename the database and
  the backups while the shop is running. Keep the program in a folder that is not synced, for example `C:\Lunavera`, and copy the backup
  `.zip` files into the cloud folder instead. The program shows a warning at start-up when the data folder is inside one of them.
- **CSV files** written by the program (reports, orders, customers, products) start with a line `sep=,` so that Excel splits the columns at
  the comma on a Windows set to the Vietnamese number format. Another program may show that line as one extra row; delete it there.
- Updating by extracting a new zip: extract it **over** the old project folder, or copy your `data/` folder
  into the new one. A new empty project folder starts with a new empty `data/`.
- **Backups:** the admin dashboard has "Sao lưu dữ liệu" (saves one `.zip` with the database **and** the product pictures) and
  "Khôi phục dữ liệu" (pick a backup; it is applied the next time the app starts, after the current data is saved as
  `data/backups/before-restore-*.zip`). The app also makes one automatic backup a day in `data/backups/` (`auto-*.zip`, the
  newest 14 are kept; the pictures are in every 7th one, so after a restore from a copy without pictures the pictures stay as they are). All of these are on the same disk as the data: copy a backup to a USB stick or another computer now and then.
  A backup holds customer phone numbers and password hashes, so keep it private. Restoring a backup also brings back the accounts and passwords as they were then; the message after a restore names the ones that changed back, so lock or change them again.
- **Who can read the data:** on Linux and macOS the app sets the `data/` folder to owner-only (`rwx------`) every time it starts. On Windows it replaces the folder's access list with one entry for your own user (inherited by everything inside), so other Windows users cannot open it.
- **While the app runs, the database has two helper files** next to it, `lunavera.db-wal` and `lunavera.db-shm` (SQLite's write-ahead log, so the
  dashboard can read while a sale is being saved). They disappear when the app is closed normally. Never copy `lunavera.db` by hand while
  the app is open (use "Sao lưu dữ liệu"), and when you put a database file back by hand, delete any `lunavera.db-wal` / `lunavera.db-shm`
  beside it first. The restore button does this itself.
- **Auto logout:** after 15 minutes without any mouse or keyboard use the app logs out (`-Dlunavera.idleMinutes=30` to change, `0` to turn off).

## Rules when changing the code

- **Never log customer or staff details.** Log ids and counts, never names, phone numbers, addresses, e-mail, passwords, hashes or discount
  reasons (the `data/logs/` folder is readable by anyone who can open the project folder, and it is not backed up with care). Wanted details
  are shown on screen or read from the database on demand. The one exception is the security log (`SecurityLog`), which records login
  names on purpose so the owner can see who did what; it never records passwords (a text typed into the user-name box that is not an account is logged only as its length and first two characters, TODO 2A.39). Read every new `LOGGER` or `SecurityLog` call against this
  rule before committing.
- **Status and role values in SQL come from `SqlStatus`** (`SqlStatus.PAID`, `SqlStatus.OPEN`, ...), not from hand-typed `'PAID'`. `SqlStatusTest` fails
  the build if a literal comes back.
- **Dates and times in the data code come from `AppClock`**, not `LocalDate.now()`, so tests can pin the day.
- **Never commit `data/`, `*.db*` or `database.properties`.** The `.gitignore` covers them; `GitIgnoreTest` fails if it goes missing.
- **One controller per screen.** The admin screens extend `AdminScreen` (shared sidebar navigation and helpers); put new screen code in that screen's own controller. `AdminScreensTest` fails if a controller grows past 900 lines or repeats the navigation.
- **Rules go in a service, and a service runs off the screen thread.** Permission checks and rules like "never delete a product that an order uses" live in `service/` (`CategoryService`, `ProductService`, `AccountService`, `CheckoutService`, `Cart`); a screen asks the person, calls the service inside `Async.run(...)`, and shows the result.
- **New text goes in `messages_vi.properties`** (`Messages.get("screen.what")`) for any screen that has been moved to it; `MessagesTest` fails on a missing or unused key. Screens not yet moved still hold their own text.
- **No dead CSS.** A class or id in a style sheet must be used by an FXML file or a Java string; `CssRulesTest` fails on a rule nothing can match.

