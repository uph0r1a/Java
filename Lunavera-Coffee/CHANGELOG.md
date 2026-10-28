# Changelog

What each version of Lunavera Coffee changed, for the owner and for whoever keeps an old version for a week
(`docs/RELEASE.md`, step 4). One section per `git tag`, newest first, in the form [Keep a Changelog](https://keepachangelog.com/).

**No version has been tagged yet** (`pom.xml` still says `1.0-SNAPSHOT`), so everything built so far is under
*Unreleased* below. It is a summary by theme, not a list of commits: the numbered detail (what was done, how it was
checked, what is still open) is in `TODO.md` (the item numbers in brackets) and `progress.md` (one entry per session).
None of it has been run through `./mvnw -B clean test` by the owner or tried on the shop's real tills yet; see the
"Release status" block at the top of `progress.md`.

## How to add a version

1. When a release is ready (`docs/RELEASE.md`, steps 1 to 4), rename *Unreleased* to the version and the date, for example
   `## [0.9] - 2026-11-03`, and put a new empty *Unreleased* heading above it.
2. Use only these headings: **Added**, **Changed**, **Fixed**, **Security**, **Removed**. Leave out the ones with nothing.
3. Write what a person at the till or the owner would notice, in one line each. Say when a version **upgrades the
   database** (`schema_version` / `user_version` changes): an older version then refuses the data (`docs/RELEASE.md`, step 5).
4. Tag the same commit with the same name (`git tag v0.9`).

## [Unreleased]

### Added
- **Expenses and net profit** (4.38): a "Chi phí" window for the owner to write down rent, wages, electricity, supplies and
  repairs; the report now shows "Chi phí" and "Lợi nhuận ròng" (gross profit less waste less expenses). Cash taken out of the
  drawer can be written down when closing the shift and is taken off the cash the drawer should hold. The database gets a table
  and a column for this; nothing needs doing by hand.
- **Lock the till and switch cashier with a PIN** (4.36): "Khóa" in the sidebar hides the screen but keeps the cart; the person
  unlocks with a 4-6 digit PIN (staff set their own under "Mã PIN"), or another cashier takes over with theirs, so each sale
  is recorded under the person who really took the money. A PIN never signs in from the login screen, an administrator's till
  opens only with the password, and five wrong PINs in a row switch the PIN off. The idle timeout now locks first. Two new
  columns on the accounts table; nothing needs doing by hand.
- **One order paid with two methods** (4.32): at the payment box tick "Một phần trả bằng tiền mặt", type the cash part and
  pay the rest by card, transfer or e-wallet. The receipt, the history and the report's payment lines show both; the shift
  close counts only the cash part as cash. The database gets one new table; nothing needs doing by hand.
- **Stock is checked again before the payment box opens** (3.1): if the last latte was sold at another table while this one
  was open, the cashier is told which item ran out before any money is taken, instead of the payment failing afterwards.
- **Merge duplicate customers, anonymise a customer who asks to be forgotten, delete one nothing points at**, on the customer
  screen for the owner (4.8, 3.4). A merge moves the orders and points to the customer you keep and writes a note of it;
  "make anonymous" removes the name, phone and e-mail but keeps every order, so the reports do not change. The database
  gets a column and a table for this; nothing needs doing by hand.
- **Settings screen** for the owner: shop name, address, phone and an optional tax code (printed on the receipts; the
  accountant decides which number goes there, 4.22), number of tables, the shop-wide low-stock level, and the bank
  details of the transfer QR (4.7).
- **Staff list** in the accounts screen: add people who never log in, edit details, mark a person without an account as
  left or back, remove a record that has no account and no order (4.12). Former staff are handled without losing the
  orders they took (4.29).
- **First-run setup** asks for the shop name and the owner's account (4.15); **table management** (4.18); an app icon and
  a proper window title (4.17).
- **Backups:** automatic backups, one-click restore with a safety copy first, a second place for the copies (a USB stick,
  another PC, a synced folder), a limit on how many are kept, and a "database health" line for the owner
  (4.1, 4.23, 4.35, 4.28, 4.24). Several tills at once on a MariaDB server (4.14).
- **Receipts:** a layout for a thermal printer (4.3); a reference kept for bank transfers (4.31); a transfer QR the owner
  can set or replace inside the app (4.26).
- **Take-away orders** (no table): a "Mang đi" button on the till and one button per open take-away order beside the tables; they are paid, printed and listed like any order, and can be cancelled before payment (4.25).
- **Selling:** a note per order line (4.9); a barcode or product code so packaged goods can be scanned (4.42); a reorder
  level per product (4.19); the product cost used in the reports (4.20).
- **Stock and shift:** a stock receiving log (4.5) with waste and breakage as a third kind of line, priced in the report (4.33); shift close with a per-employee report (4.6).
- **Reports and customers:** revenue split by payment method and by e-wallet (4.30, 4.34); busiest hours and weekdays, sales
  by category, comparison with the previous period (4.40); a customer's purchase history, last visit, number of visits and
  total spent (4.4, 4.39); CSV export of orders and customers (4.21).
- **Housekeeping:** clean up pictures nobody uses (4.27); read the security log from the app (4.44).
- Every screen text exists in Vietnamese and English (`messages_vi.properties`, `messages_en.properties`).

### Changed
- One order-number format everywhere: `#12` (the till no longer shows `#DH000012`) (5.12).
- Exported CSV files start with a `sep=,` line after the byte-order mark, so Excel splits the columns in every regional
  setting, including the Vietnamese one; the product import reads that line again (3.49).

### Fixed
- The backup button can no longer overwrite the database it protects: the target always ends in `.zip` and is written
  through a temporary file (2.8, 2.23).
- Product pictures are checked for size and for being a real image before they are kept.
- An account's lock state and its staff record's `active` flag change together and cannot disagree (2.31).

- The best-sellers report no longer selects a column it does not group by, so it also runs on MySQL and on a MariaDB
  server with `ONLY_FULL_GROUP_BY` (error 1055); the MariaDB test job of the CI now runs with that mode on (6.47).

- Refunding a paid order no longer changes an earlier day: the sale stays on the day it was paid and the refund shows as a negative line on the day it was made, in the report, the daily table, the payment split, the dashboard and its chart, and the shift totals (3.13). Orders refunded before this version keep their old treatment (not counted), because their refund day was never stored.

### Security
- Passwords are hashed with PBKDF2 at 600,000 iterations; an older hash is upgraded at the next successful login (2A).

### Removed
- Six unused language keys (`categories.dialog.add`, `categories.dialog.edit`, `categories.label.name`,
  `categories.save.fail`, `categories.val.name`, `money.digits`) from both language files.

### Upgrading
- The tables are upgraded by themselves at the first start of the new version (this one adds `orders.cancelled_at`). Nothing makes a copy first: do
  `docs/RELEASE.md` step 1 by hand.
