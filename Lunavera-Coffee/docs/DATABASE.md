# The database: MariaDB/MySQL with phpMyAdmin (default) or SQLite

The app works with **one of two databases**, and **MariaDB/MySQL is the default**:

| | MariaDB / MySQL (default) | SQLite |
| --- | --- | --- |
| Setup | install a database server once (section 1) | none: one file, `data/lunavera.db` |
| Edit the data by hand | **phpMyAdmin** in the browser | an external tool (DB Browser for SQLite) |
| Several tills on one database | yes (all point at the same server) | no |
| If the computer with the data is off | the tills cannot work (the server is the shop) | that till cannot work |

Without `data/database.properties` the app looks for a server on this computer (`127.0.0.1:3306`, database `lunavera`); if there is none the start-up box
tells you what to do and, when there is no settings file at all, has a button **"Use the local file on this computer"** that writes `db.type=sqlite` for you (TODO 5.40). To use the old SQLite file by hand instead, put `db.type=sqlite` in that file.
While the shop runs on MariaDB the app also keeps an **hourly SQLite snapshot** of the data as a safety copy (section 6).

## 1. Install the server (Windows, simplest: XAMPP)

1. Install **XAMPP** (it contains MariaDB and phpMyAdmin). In its Control Panel start **MySQL** (that is MariaDB). Start **Apache** only when you want phpMyAdmin.
2. Open <http://localhost/phpmyadmin> in the browser on that computer.
3. A plain MariaDB installer plus phpMyAdmin (or HeidiSQL, DBeaver) works the same way.

## 2. Create the database and a user that is not `root`

In phpMyAdmin open the **SQL** tab and run (change the password; use a long one):

```sql
CREATE DATABASE lunavera CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lunavera'@'localhost' IDENTIFIED BY 'a-long-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER, LOCK TABLES ON lunavera.* TO 'lunavera'@'localhost';
FLUSH PRIVILEGES;
```

Why not `ALL PRIVILEGES`: the app reads and writes every day (`SELECT, INSERT, UPDATE, DELETE`) and needs `CREATE, ALTER, INDEX, REFERENCES, TRIGGER, LOCK TABLES` only when it starts and creates or upgrades its tables. It never needs `DROP`, `GRANT OPTION`, `CREATE USER` or any right on another database. With the narrow grant, a stolen till or a leaked `database.properties` cannot drop the tables. `DROP` (and a restore from phpMyAdmin) stays with your own administrator account, not the app's user.
This list was tried on a real MariaDB 10.11 server (TODO 2A.36): the app created every table on an empty database, upgraded an older one, saved a sale and made a backup with this user and nothing else; `DROP`, `GRANT`, `CREATE USER` and any other database were refused. It was not tried on MySQL 8 or on a till on another computer.
If an upgrade of the app ever stops with "command denied" for a table, run `SHOW GRANTS FOR 'lunavera'@'localhost';`, add the missing right once, and tell the developer (it means the app needs something this list does not have).

The app creates its own tables the first time it connects (it needs the right to create and alter tables in this one database; it needs nothing on any other).
For other tills on the shop network create the user as `'lunavera'@'192.168.1.%'` (your network) as well, with the same `GRANT` line for that name, and see "Security" below.

## 3. Tell the app

Copy `database.properties.example` (project root) to **`data/database.properties`** and fill it in:

```
db.type=mariadb
db.host=127.0.0.1
db.port=3306
db.name=lunavera
db.user=lunavera
db.password=a-long-password
```

The file is read **literally** (since TODO 2.82): the password is everything after the first `=`, exactly as typed, so it may contain a backslash, `#`, `!`, `=`, `:` or spaces, and nothing in it is an escape. Do not put a comment on the same line as a setting (`db.password=abc # old` is a password with a comment in it); put comments on their own line, starting with `#`.

Optional keys for a server on another computer: `db.ssl=true` (encrypt the connection, see section 7), `db.sslServerCert=` (the certificate file of a self-signed server), `db.sslMode=` (disable, trust, verify-ca or verify-full, instead of `db.ssl`) and `db.socketTimeoutMs=` (how long a call waits for the server, default 30000, `0` = for ever).

Every key can also be given on the command line as `-Dlunavera.db.name=...` (a command-line value wins over the file).
Start the app. The first start creates the tables, 12 starter dining tables (changeable under "Quản lý bàn" on the dashboard) and the starter menu, then asks the owner to create the first administrator (there is no built-in `admin` password), exactly like a new SQLite database. If `db.import.sqlite` is set (section 4), the old accounts come over instead and no account is asked for.

## 4. Bring the existing data over (once)

The shop's old data is in the SQLite file `data/lunavera.db` (products, categories, customers, orders, accounts, tables). To carry it into MariaDB,
make sure `data/database.properties` contains this line (it is already in `database.properties.example`):

```
db.import.sqlite=data/lunavera.db
```

and start the app. It copies **everything** into MariaDB, keeping every id (so each order still points at its products and customer),
converts the dates, and checks that each table has the same number of rows. The product pictures are plain files in `data/images`, which stay where they are.
The accounts come over too, so you sign in with your **old** admin password (the default `admin` account of a fresh start is replaced).

When it runs:
- The first start on a new MariaDB database creates the database tables, the 12 dining tables (the shop's tables) and a starter menu (and, if the owner already made one, the first administrator). **That does not block the import**: while the database holds
  nothing but those first-start things (no customer, no order, no other account, no products of your own), the import **replaces** them with your old data.
- If the database already holds **real data** (a customer, an order, another account, a product of your own) the import is skipped and the log says so; nothing is overwritten or duplicated.
- It is one transaction: if anything fails, everything is undone, the database stays as it was, the app stops with a message, and the SQLite file is never changed.
  The common failure is two customers with the same phone number (MariaDB allows one customer per phone): the message names the number; fix or merge those two
  customers in the SQLite version of the app (Quản lý khách hàng) and start again.
- If you already created your own first account (a name other than `admin`) before importing, the database no longer counts as new, so the import is skipped. Drop the database, create it again empty and start once more.
- A file made before the columns for the order of tabs and products, a product's reorder level, the points an order gave, the cash received and the bank reference were added simply has none of them: the order of tabs and products stays the order of creation, and the other fields stay empty (an old order is refunded with the loyalty rule of today).
- A file that has no `users` table is not a Lunavera database: it is ignored with a warning and nothing is changed.
- The old file is opened **read-only**, so SQLite cannot replay or delete a leftover `lunavera.db-wal` or `-journal` next to it, and it is looked at before one row is copied (TODO 3.62). The app **stops with a message** and copies nothing when the file comes from a **newer** version of Lunavera (it has columns this version would drop), when it is **damaged** (SQLite's own check fails), or when some rows **point at something that is not in the file** (an order line whose product is gone: the copy runs with foreign keys off, so they would only fail later, on the first screen that uses them). The message names the first rows; open the file with the SQLite version of the app and repair or remove them, then start again. A file from before the money migration (version 0) is copied, with a warning in the log; if you can, open it once with the SQLite version first, which upgrades it, and import that.

Afterwards remove the line (or leave it: once there is real data it does nothing but write one log line), and keep `data/lunavera.db` as a safety copy until you are sure.

**Check after the first start:** compare the numbers of products, customers and orders and today's revenue on the dashboard with the SQLite version, and open a few orders.

## 5. Editing in phpMyAdmin

When the shop runs on MariaDB, the admin dashboard has a **"Mở phpMyAdmin"** button (admin only). It asks first, then opens
`http://localhost/phpmyadmin/` in the browser; if phpMyAdmin lives elsewhere, set `db.phpmyadmin=http://server/phpmyadmin/` in `data/database.properties`
(only `http://` and `https://` addresses are accepted).

phpMyAdmin changes the data **without the app's rules**, so be careful:

- Take a backup first (the app's "Sao lưu dữ liệu", or phpMyAdmin **Export**).
- Values the app expects:
  - statuses are **UPPER CASE text**: orders `OPEN`, `PAID`, `CANCELLED`; users `ACTIVE`, `LOCKED`; roles `ADMIN`, `EMPLOYEE`; payment `CASH`, `CARD`, `TRANSFER`, `MOMO`, `ZALOPAY`, `VNPAY` (the e-wallets, TODO 4.34; the column is `VARCHAR(20)`, no schema change); tables `EMPTY`, `OCCUPIED`;
  - **money is whole dong** (a price of 25.000đ is `25000`);
  - customer phones in the `0...` form (`0912345678`, not `+84...`), and one customer per phone (a blank phone is NULL, not an empty string);
  - never edit `password_hash` by hand (use the app's account screen), and never touch `app_flags` and `schema_version`.
- Loyalty points can be spent at the till (TODO 4.16, rule D5): `orders.points_redeemed` and `orders.points_value` say how many and what they were worth; the value is also inside `orders.discount`, so `total = subtotal - discount` still holds. A refund gives the spent points back. A file made before these columns simply has none of them (nothing was spent).
- Do not change a **paid** order's lines or totals: stock and loyalty points were already applied (and the report adds up the saved totals). Cancel it in the app's order history instead.
- A product that has been sold cannot be deleted (the database refuses); hide it (`active` = 0) instead.
- A customer is never deleted. One is made anonymous (`customers.anonymised_at` is set, the name, phone and e-mail are removed, the orders stay), and two rows of one person are merged: the kept row keeps its name and phone and the merge is one audit-log line (no table; an older database may still hold an unused `customer_merges`). Rule D21. `orders.queue_no` is the take-away number of the day (empty for table orders) and is kept unique per shop day by the `queue_day` column and its key (TODO 2.98; the per-day counter table of TODO 3.77 was not kept when the branches were merged). `orders.channel` is where the order came from: `DINE_IN`, `TAKE_AWAY`, `GRAB_FOOD` or `SHOPEE_FOOD` (TODO 4.48); empty on an order made before it existed, which is read by its table (none = take-away, else dine-in). `order_payments` holds one row per method for an order paid with two or more methods. **Where a customer's name or phone is kept as text** (TODO 2.88): `customers` (cleared by making the customer anonymous), `reservations.customer_name` / `phone` (a booking stores the guest as typed; `reservations.customer_id` is set when the phone matched a customer, and making that customer anonymous replaces the name by "Former customer #id" and removes the phone on those bookings and, for older rows with no id, on bookings with the same phone), and the audit log (older lines are scrubbed, TODO 4.2). Free text typed by staff (`orders.discount_reason`, `orders.cancel_reason`, `reservations.note`, `order_items.note`) is not searched for names. The staff names in `refunds`, `stock_log`, `expenses` and the other tables are employees', not customers'.
- Staff hours (TODO 7) are in `time_clock` (user id, the name at the time, clock-in, clock-out; no clock-out = still in). `customers.birthday` is an optional date. Gift cards (TODO 7) are in `gift_cards` (code, initial amount, balance, who sold it, when) and `gift_card_moves` (card, order or empty for the sale, signed amount, when); a card's balance always equals the sum of its moves, and a payment that used one or more cards has one `GIFT_CARD` row in `order_payments` (the sum of its cards; TODO 3.87; each card's part is its `gift_card_moves` row) or `payment_method` GIFT_CARD when the cards paid it all. Audit actions `CLOCK_CORRECTED`, `GIFT_CARD_SOLD`, `BIRTHDAY_CHANGED`.
- A customer is never deleted. One is made anonymous (`customers.anonymised_at` is set, the name, phone and e-mail are removed, the orders stay), and two rows of one person are merged: the kept row keeps its name and phone and the merge is one audit-log line (no table; an older database may still hold an unused `customer_merges`). Rule D21. `orders.queue_no` is the take-away number of the shop day `orders.queue_day` (both empty for table orders; the database refuses the same number twice on one day, TODO 2.98). `order_payments` holds one row per method for an order paid with two or more methods.
- Staff hours (TODO 7) are in `time_clock` (user id, the name at the time, clock-in, clock-out; no clock-out = still in). The database allows only one open stretch per person. `customers.birthday` is an optional date. Gift cards (TODO 7) are in `gift_cards` (code, initial amount, balance, who sold it, when) and `gift_card_moves` (card, order or empty for the sale, signed amount, when); a card's balance always equals the sum of its moves, and a payment that used a card has a `GIFT_CARD` row in `order_payments` (or `payment_method` GIFT_CARD when the card paid it all). Audit actions `CLOCK_CORRECTED`, `GIFT_CARD_SOLD`, `BIRTHDAY_CHANGED`.
- A customer is never deleted. One is made anonymous (`customers.anonymised_at` is set, the name, phone and e-mail are removed, the orders stay), and two rows of one person are merged: the kept row keeps its name and phone and the merge is one audit-log line (no table; an older database may still hold an unused `customer_merges`). Rule D21. `orders.queue_no` is the take-away number of the day (empty for table orders). `order_payments` holds one row per method for an order paid with two or more methods.
- Staff hours (TODO 7) are in `time_clock` (user id, the name at the time, clock-in, clock-out; no clock-out = still in). `customers.birthday` is an optional date, and `customers.birthday_used_on` (TODO 3.66) is the day the birthday discount was last given to the customer (null = never; set in the payment's own transaction, kept as the later one when two customers are merged, cleared when a customer is made anonymous). Gift cards (TODO 7) are in `gift_cards` (code, initial amount, balance, who sold it, when) and `gift_card_moves` (card, order or empty for the sale, signed amount, when); a card's balance always equals the sum of its moves, and a payment that used a card has a `GIFT_CARD` row in `order_payments` (or `payment_method` GIFT_CARD when the card paid it all). Audit actions `CLOCK_CORRECTED`, `GIFT_CARD_SOLD`, `BIRTHDAY_CHANGED`, `BIRTHDAY_DISCOUNT_USED`.
- Moving, merging and splitting (TODO 4.10) add no table: a transfer changes `orders.table_number` and `dining_tables`; a merge cancels the joined order (`cancel_reason` "Đã gộp vào đơn #n"); a split inserts a PAID order for the part and rewrites the open one. Audit actions `TABLE_TRANSFERRED`, `TABLES_MERGED`, `BILL_SPLIT`.
- Ingredients and recipes (TODO 4.41) are in `ingredients` (name, unit, stock, min_stock, and, from TODO 4.51, `unit_cost`: the price of one unit in whole dong, NULL = not priced yet; added to an older database by `SqliteSchema.LATER_COLUMNS`, no schema version change) and `recipes` (product, ingredient, amount per serving), whole numbers only. Paying a product that has recipe lines lowers `ingredients.stock` and leaves `products.stock` alone; each paid line writes down what it took, per piece, in `stock_takes` (`order_item_id`, `ingredient_id` NULL for a cup, `amount`; TODO 2.96), and a refund or a cancelled paid order puts back exactly that, so a recipe changed since the sale cannot make the shelf wrong; a line paid before the table existed has no rows and falls back to the recipe the product has at that time. Audit actions `INGREDIENT_SAVED`, `INGREDIENT_STOCK`, `INGREDIENT_DELETED`, `RECIPE_CHANGED`. A delivery that says what was paid sets `unit_cost` to the average of the stock on the shelf and the new lot, weighted by quantity (`IngredientCostRules.afterDelivery`; an empty shelf takes the new price), and can write the amount as a `SUPPLIES` expense in the same transaction. A recipe's cost is the sum of amount * `unit_cost`; "use as the product's cost" copies it to `products.cost` (audit `PRODUCT_COST`) and never touches `order_items.unit_cost` of lines already paid.
- The stock history (TODO 4.59) has no table of its own. `StockHistoryDAO` builds it for one product or ingredient from `stock_log` (receipts, typed numbers, waste), `stock_takes` joined to `order_items` and `orders` (sales, one line per order), `refunds` with `restocked = 1` and cancelled paid orders whose audit line is `ORDER_REFUNDED` (not `ORDER_REFUNDED_NO_STOCK`) for goods put back, and the audit line `INGREDIENT_STOCK` for an ingredient's deliveries and counts. The balance is counted backwards from today's `stock`. Clearing the sales data (4.57) clears the audit log too, so a whole-order refund made before it is no longer listed.
- **Vietnamese text is stored composed (TODO 3.18).** Names, notes, reasons and descriptions are saved with each accent as one character (NFC), counted for the length limit after that. Text saved by an older version is rewritten once at start (`app_flags` flag `text_nfc_done`, `TextNormalization`; only the accent encoding changes). A name that would repeat another row's in a unique column (`categories.name`, `ingredients.name`) is left as it was and logged. `TextColumnsTest` fails for a new TEXT column that is neither on that list nor explained.
- **Time zone of stored times (TODO 3.68).** Every date and time in the database is zone-less text, and it always means the **shop's** wall-clock time: the program reads "now" in the shop zone (`Asia/Ho_Chi_Minh`, or `shop.zone=<zone id>` in `data/ui-settings.properties`) whatever zone the PC is set to, so two tills of one shop never write two clocks. The PC's clock must be right as a moment in time (automatic time keeps it so even with a wrong zone); the start-up warns when the PC's zone differs from the shop's. Changing `shop.zone` of a shop that already has data does not move the old rows.
- **Durability: what survives a power cut (TODO 2.92).** SQLite is run with write-ahead logging and `PRAGMA synchronous = FULL` on every connection (it answers 2): the log is flushed at every commit, so a sale the till showed as paid is still there after a power cut or an operating-system crash, at the price of one flush per payment or autosave (about a hundredth of a second on a spinning disk, not noticeable at a till). `NORMAL` was used before and only protects the file's structure, not the last few commits. **MariaDB / MySQL must keep `innodb_flush_log_at_trx_commit = 1`** (its default; `0` and `2` can lose the last second or more of commits in a crash) and should keep `sync_binlog` at its default if a binary log is on; XAMPP's `my.ini` ships with `innodb_flush_log_at_trx_commit = 1`, but it is worth checking after an upgrade. A power cut cannot be staged in the tests, which only check the setting.
- **Who did it, as text (TODO 2.87):** refunds, expenses, the time clock, gift cards, bookings, the stock log, closed shifts and the audit log copy the signed-in person's name. Making a former staff member anonymous rewrites every one of them in one transaction (`AccountService.scrubStaffNames`); `gift_cards.sold_by_id` and `reservations.created_by_id` (staff record id, NULL on older rows) exist for that. `StaffNameColumnsTest` fails when a new `*_name` / `*_by` text column is on neither the scrubbed nor the not-staff list.
- **Stock held by open orders (TODO 3.1)** is not stored: the lines of an OPEN order (`order_items` of `orders.status = 'OPEN'`) are the reservation, and the till reads `products.stock` (or, for a recipe product, the servings the ingredients give) less what the other open orders' lines need (`ProductDAO.forSaleSql`). Nothing to release when an order ends.
- Ingredients and recipes (TODO 4.41) are in `ingredients` (name, unit, stock, min_stock, and, from TODO 4.51, `unit_cost`: the price of one unit in whole dong, NULL = not priced yet; added to an older database by `SqliteSchema.LATER_COLUMNS`, no schema version change) and `recipes` (product, ingredient, amount per serving), whole numbers only. Paying a product that has recipe lines lowers `ingredients.stock` and leaves `products.stock` alone; a refund or a cancelled paid order puts them back with the recipe the product has at that time. Audit actions `INGREDIENT_SAVED`, `INGREDIENT_STOCK`, `INGREDIENT_DELETED`, `RECIPE_CHANGED`. A delivery that says what was paid sets `unit_cost` to the average of the stock on the shelf and the new lot, weighted by quantity (`IngredientCostRules.afterDelivery`; an empty shelf takes the new price), and can write the amount as a `SUPPLIES` expense in the same transaction. A recipe's cost is the sum of amount * `unit_cost`; "use as the product's cost" copies it to `products.cost` (audit `PRODUCT_COST`) and never touches `order_items.unit_cost` of lines already paid.
- Ingredients and recipes (TODO 4.41) are in `ingredients` (name, unit, stock, min_stock) and `recipes` (product, ingredient, amount per serving), whole numbers only. Paying a product that has recipe lines lowers `ingredients.stock` and leaves `products.stock` alone; a refund or a cancelled paid order puts them back with the recipe the product has at that time. Audit actions `INGREDIENT_SAVED`, `INGREDIENT_STOCK`, `INGREDIENT_DELETED`, `RECIPE_CHANGED`.
- Sizes and extras (TODO 4.47) are in `product_options` (product, kind SIZE or EXTRA, name, price_delta, cost_delta, recipe_percent, is_default, active; unique per product, kind and name; deleted with the product). An order line keeps what was chosen in four columns of `order_items`: `size_name`, `size_percent`, `extras` (`name=price;name=price`) and `option_cost`; they are copies, never a link to `product_options`, so editing an option moves no old order. All four are null on a plain line. They are added to an old database at start-up like every later column; `schema_version` does not change.
- A customer is never deleted. One is made anonymous (`customers.anonymised_at` is set, the name, phone and e-mail are removed, the orders stay), and two rows of one person are merged: the kept row keeps its name and phone and the merge is one audit-log line (no table; an older database may still hold an unused `customer_merges`). Rule D21. `orders.queue_no` is the take-away number of the day (empty for table orders) and is kept unique per shop day by the `queue_day` column and its key (TODO 2.98; the per-day counter table of TODO 3.77 was not kept when the branches were merged). `orders.channel` is where the order came from: `DINE_IN`, `TAKE_AWAY`, `GRAB_FOOD` or `SHOPEE_FOOD` (TODO 4.48); empty on an order made before it existed, which is read by its table (none = take-away, else dine-in). `order_payments` holds one row per method for an order paid with two or more methods. **Where a customer's name or phone is kept as text** (TODO 2.88): `customers` (cleared by making the customer anonymous), `reservations.customer_name` / `phone` (a booking stores the guest as typed; `reservations.customer_id` is set when the phone matched a customer, and making that customer anonymous replaces the name by "Former customer #id" and removes the phone on those bookings and, for older rows with no id, on bookings with the same phone), and the audit log (older lines are scrubbed, TODO 4.2). Free text typed by staff (`orders.discount_reason`, `orders.cancel_reason`, `reservations.note`, `order_items.note`) is not searched for names. The staff names in `refunds`, `stock_log`, `expenses` and the other tables are employees', not customers'.
- Table reservations (TODO 4.43) are in the table `reservations` (table number, guest, phone, time, people, note, status BOOKED / SEATED / CANCELLED / NO_SHOW); a booking is closed, never deleted.
- Vouchers (TODO 4.11) are in the table `vouchers`; an order that used one has `orders.voucher_code` and `orders.voucher_value` (the value is also inside `orders.discount`).
- Automatic promotions (TODO 4.55) are in the tables `promotions` (name, kind PERCENT / FIXED_PRICE / BUY_GET / COMBO, the numbers, days as digits 1-7, hours, dates, on/off) and `promotion_targets` (a product or a category each, with a piece count for combos; deleted with their promotion, product or category). They are applied when a bill is paid and their value is part of `orders.discount`: `orders.promo_value` is the part that came from promotions, and each line that got one has `order_items.promo_name` and `order_items.promo_value`. The in-app restore knows both tables.
- Free drinks (TODO 4.61, rule D26) are ordinary paid orders with `payment_method = COMPLIMENTARY`, `total = 0` and every line at 0, plus four columns of `orders`: `complimentary_kind` (STAFF, OWNER or TASTING), `complimentary_for` (the `employees.id` who drank it, STAFF only), `complimentary_value` (what the lines would have cost at the menu price, in dong; the only place that price is kept) and `complimentary_note` (up to 200 characters). All four are null on every other order. The cost of the drinks is `order_items.unit_cost x quantity` as for any sale. The audit action is `COMPLIMENTARY_GIVEN` (a drink for a member of staff is filed under that person, so making them anonymous removes the name there too).
- Refunding some lines of a paid order (TODO 4.37) writes one row per line in the table `refunds` (pieces, money given back,
  method, who, when, why); the order itself stays PAID. The reports count a refund on the day it was made.
  An order paid with a gift card (TODO 2.93) gets its money back on the card FIRST: the card part is its own row with method GIFT_CARD (and a positive line in `gift_card_moves`), the rest a row with the method chosen. A line split between the two is two rows: the card row carries the pieces and `line_amount`, the other row has quantity 0 and `line_amount` 0 and only `amount`; so sum `quantity` for the pieces refunded and sum `amount` for the money.
- Expenses (rent, wages, supplies, cash taken out of the drawer) are in the table `expenses`; the cash taken out during a shift
  is also stored on the closed shift (`shift_closes.cash_out`). Written in the "Chi phí" window by an administrator, or from
  "Chốt ca" for cash out of the drawer (TODO 4.38, rule D8).
- The audit log is the table `audit_log` (one row per sensitive action: `logged_at`, who did it as `actor_user_id`, `actor_employee_id` and `actor_name`, the `action`, what it touched as `target_type`, `target_id` and `target_label`, `old_value`, `new_value` and a free `detail`). The app only adds rows (and scrubs a customer's name and phone from older ones when the customer is made anonymous, rule D21); never edit or delete them in phpMyAdmin, or the log can no longer be trusted. The one thing in the app that empties the log is "Bắt đầu bán thật" / "Start selling for real" (Settings, TODO 4.57): it takes a backup first and writes one new line, `SALES_STARTED`, saying who did it.
- Screens show the new values the next time they are loaded.

## 6. Backups and restore

- The dashboard button **"Sao lưu dữ liệu"** and the daily automatic backup make a `.zip` that holds `lunavera.sql` (a `mysqldump`) plus the pictures. They run the `mysqldump` program, which must be on the computer that runs the app:
  XAMPP has it at `C:\xampp\mysql\bin\mysqldump.exe`; if it is not on the PATH, set `db.mysqldump=C:\xampp\mysql\bin\mysqldump.exe` in `data/database.properties` (write the path as it is, with single backslashes; a file written with doubled backslashes in an older version still works on Windows). The password is not shown in the process list (it travels in an environment variable).
- **Restore** (TODO 1.8): the dashboard button **"Khôi phục dữ liệu"** restores a MariaDB backup `.zip` from inside the app, at once (no restart). Close the other tills first; an administrator must be signed in. What it does, in this order, and stops at the first problem with nothing of the shop changed:
  1. It reads the whole zip (every checksum, the same size limits as a SQLite restore) and takes the server lock that a till holds while it creates or upgrades tables, so a restore never runs twice or under a till that is starting (that till waits up to a minute).
  2. **Scratch check.** It runs `lunavera.sql` into a separate empty database named `<your database>_restore_check` (for `lunavera`: `lunavera_restore_check`). Only the statements a dump of this app's tables holds are run (`CREATE TABLE`, `INSERT`); a `DROP DATABASE`, a `GRANT`, an `INSERT ... SELECT` or any other statement stops it before it is run. In the scratch database it checks that the backup is not from a newer version (`schema_version`), holds only tables and columns this program knows, has the `users`, `products` and `orders` tables, has an active administrator, and has no row that points at a row that is not there. The confirmation shows what it found (orders, products, customers, pictures).
  3. **Safety copy.** `backups/before-restore-<time>.zip` (a `mysqldump` and the pictures, the newest 3 are kept). If it cannot be made (for example `mysqldump` is not found) nothing is restored.
  4. **Swap, in one transaction.** The rows of every table of the app are deleted and the checked rows are copied across by the server, the row counts are compared, the picture folder is swapped (`images.old` is kept until the end) and only then is the transaction committed. A failure at any point rolls everything back and puts the pictures back; other tills never see a half-restored shop and keep reading the old data until the commit. A backup made by an older version is copied into the **current** structure (its columns; new ones take their defaults), and `schema_version` stays as it is. The structure of the live tables is never changed by a restore.
  5. The scratch database is dropped, the security log and the audit log (`Backup restored`) get a line, and the till signs out. Other tills should be closed and opened again.
  A backup between two full ones holds no pictures and the ones in the shop stay. Accounts and passwords go back to how they were when the backup was made.
  **One extra right is needed, once.** The database user of section 2 may not create other databases. The restore needs `lunavera_restore_check` and says so (and changes nothing) when it may not:
  ```sql
  GRANT ALL PRIVILEGES ON `lunavera_restore_check`.* TO 'lunavera'@'localhost';
  FLUSH PRIVILEGES;
  ```
  (Use your database name and user; the `_` in a database name is a wildcard for `GRANT`, which is harmless here.) If a database with that name already exists and holds tables that are not the app's own, the restore refuses to touch it. While it waits for other tills' open changes it can stop with "another till is using the data": close the other tills and try again.
  **By hand** (if the in-app restore cannot be used): unzip the backup, in phpMyAdmin choose the database, **Import**, pick `lunavera.sql` (it replaces the tables), then copy the zip's `images` folder into `data/images`.
- Copy backups off the computer (USB stick, another PC). A backup holds customer phones and password hashes.

### Hourly SQLite snapshots (automatic)

While the shop runs on MariaDB, the app writes a **SQLite copy of all the data** to `data/snapshots/lunavera-yyyyMMdd-HHmmss.db` every hour while it is open
(the first about 30 seconds after start-up, if the newest is older than an hour) and keeps only the **newest 10**; older ones are deleted.
Each snapshot is one consistent view of the data, checked to have the same number of rows in every table, and is written under a temporary name and renamed
when complete, so a broken half-file is never mistaken for a good one.

- Open one in DB Browser for SQLite to look at the data without the server.
- To run the app on a snapshot (for example the server is gone): close the app, copy the snapshot over `data/lunavera.db` (and delete `data/lunavera.db-wal` / `-shm` if they exist), put `db.type=sqlite` in `data/database.properties`, start.
- A snapshot does **not** contain the product pictures (`data/images`) and holds customer phones and password hashes, like any backup.
- **With several tills only one makes the copies.** The tills share the server, so one copy an hour is enough: the till that starts first takes a lock on the server and keeps it while it is open, and the other tills skip. When that till is closed, another one takes over at its next hour. The snapshots therefore sit in the `data/snapshots/` folder of whichever till was open; to keep them in one place, switch them off on the other tills with `db.snapshots=false` in their `data/database.properties`. The daily backup zips (which include a `mysqldump`) are separate and each till still makes its own.
- When two tills start in the same second after an upgrade, the first one creates and updates the tables while the other waits (up to a minute, a server lock); you do not need to start them one by one.
- On MariaDB the database itself refuses two login names that differ only in capitals (`Mai` and `mai`): `users.username_key` holds the lower-case name and has a unique key. If an older database already has such a pair, the key is not built; the start-up log says so ("login name(s) exist more than once") and gives the query that lists them. Rename or delete one of the two accounts and restart; the key is built at the next start.
- A SQLite shop gets no snapshots (the database already is a file; the backup button and the daily zip cover it). A failed snapshot is written to the log and tried again an hour later.

## 7. Security (read this)

- Keep the server for the **shop network only**. Never open port 3306, or phpMyAdmin, to the internet: that is the usual way shops get hacked.
- XAMPP's defaults are for learning: `root` has **no password** and phpMyAdmin lets anyone in. Set a root password, and in phpMyAdmin's `config.inc.php` use `auth_type = 'cookie'` so it asks for a login. Stop Apache when you do not need phpMyAdmin.
- With a single till keep MariaDB listening on this computer only (`bind-address=127.0.0.1` in `my.ini`, the XAMPP default). Only if other tills must connect, allow your network and add a Windows Firewall rule for the shop network only.
- `data/database.properties` contains the database password: it is outside git; do not email it or put it in a shared folder.
- Use the `lunavera` user (not `root`) in that file.

### Another till on the network: switch TLS on

Without TLS everything the app sends to a server on another computer crosses the shop's wifi or cable as plain text: customer names and phone numbers, password hashes, and the database password itself. A server on the same computer (`127.0.0.1`, `localhost`) is not affected. The app says it in two places when the server is somewhere else and nothing is encrypted: a warning line in `data/logs/` at start-up, and "KHÔNG mã hóa" on the line at the bottom of the login screen.

1. **On the server computer**, give MariaDB a certificate and a key (ask whoever set the server up; `openssl` can make a self-signed one) and name them in `my.ini`, under `[mysqld]`: `ssl-cert=`, `ssl-key=` and `ssl-ca=` with full paths. Restart MariaDB and check that `SHOW VARIABLES LIKE 'have_ssl';` says `YES`.
2. To make the server refuse an unencrypted login from the other tills: `ALTER USER 'lunavera'@'192.168.1.%' REQUIRE SSL;`
3. **On each till**, in `data/database.properties`: `db.ssl=true`. If the certificate is self-signed or from your own CA, copy the CA file next to `database.properties` and add `db.sslServerCert=ca-cert.pem`.
4. `db.ssl=true` checks the certificate **and** that its name matches `db.host`. If the till connects by IP address and the certificate carries a name, use `db.sslMode=verify-ca` (checks the certificate only). `db.sslMode=trust` encrypts but does not check who answers: it stops someone listening in, not someone pretending to be the server; use it only to get going, and say so in `progress.md`.

The backup button runs `mysqldump`, and passes it the same TLS settings (TODO 2A.42): with `db.ssl`, `db.sslMode` or `db.sslServerCert` set, the dump is encrypted and the server is checked like the till's own queries, and the backup works against a server that requires TLS. It first asks the program for `mysqldump --version`, because MySQL's and MariaDB's programs spell the options differently (`--ssl-mode=...` and `--ssl-ca=` for MySQL; `--ssl`, `--ssl-verify-server-cert` and `--ssl-ca=` for MariaDB). If it cannot tell which one it is, the backup does **not** start and says so, rather than sending the data in the clear: write the full path of the server's own `mysqldump` in `db.mysqldump`. MariaDB's program cannot check the certificate without also checking the host name, so with `db.sslMode=verify-ca` it is a little stricter than the connection; if that gets in the way (an IP address and a certificate with a name), use `trust` for the backup or a certificate that carries the address. Nothing in this repository has tried this against a real server; the `[client]` / `ssl-ca=` way in `my.ini` still works as before and is not needed any more.

A call to the server now gives up after 30 seconds (`db.socketTimeoutMs`) with an error message, instead of freezing the till for ever when the server stops answering (a power cut at the router, a stuck query). Raise it only if a report really takes longer.

## 8. When something is wrong

| Message / symptom | Usual cause |
| --- | --- |
| The app does not open and says it cannot reach the database server | The server is not running (XAMPP: start MySQL), wrong `db.host` / `db.port`, or a firewall. The till cannot sell without the server. |
| `Access denied for user ...` | Wrong `db.user` / `db.password`, or the user was created for another host (`'lunavera'@'localhost'` only allows this computer). |
| `Access denied; you need (at least one of) the PROCESS privilege(s)` while backing up | Older versions of this app (MySQL 8.0.21 and later only): the backup asks for tablespace information that a database-level user may not read. This version passes `--no-tablespaces`; if you still see it, the `mysqldump` you use is not the one of the server package: set `db.mysqldump`. |
| `Unknown database` | The `CREATE DATABASE` step was skipped, or `db.name` is spelled differently. |
| `Public Key Retrieval is not allowed` (MySQL 8) | Make the user use the older login: `ALTER USER 'lunavera'@'localhost' IDENTIFIED WITH mysql_native_password BY '...';` (MariaDB does not need this). |
| Backup says it cannot run `mysqldump` | Set `db.mysqldump` to its full path (section 6). |
| In the middle of a sale: "Không kết nối được máy chủ cơ sở dữ liệu" | The server stopped or the network dropped. Start it again, then repeat the last action; nothing half-saved is kept (each save is one transaction). |
| "Cơ sở dữ liệu đang bận" | Another till was writing at the same moment. Try again. |
| Import stopped with a message | Read the message; MariaDB stays empty (rolled back). Fix the cause and start again. |

To go back to SQLite put `db.type=sqlite` in the file (the app then uses `data/lunavera.db`). Data added to MariaDB in the meantime is **not** copied back, except through a snapshot (section 6).

## 9. Differences from SQLite (on purpose)

- Text compares ignoring **accents and capitals**: the categories `Trà` and `tra` count as the same name, and a search for `ca phe` finds `Cà phê`. The app now applies the same rule to category names on SQLite too (TODO 3.55), so the two databases accept and refuse the same names; `đ` counts as its own letter in the category rule (`Đá` and `Da` are two categories, as on the server), while the search boxes treat it as `d`. A SQLite file that already holds two categories the server would call equal (`Trà` and `Tra`) cannot be imported until one is renamed; the import says which two. Login names also ignore capitals in the app (`admin` and `ADMIN` are the same account when signing in or creating one). On SQLite the database enforces it as well; on MariaDB only the app does, so do not add two accounts that differ only in capitals in phpMyAdmin.
- Dates are real `DATETIME` columns; money is `BIGINT` (whole dong), the same numbers as before.
- Only one connection is opened per action (no connection pool yet).
- **Settings table (TODO 6.74).** `settings(name, value)` holds the shop name, address, phone, tax code, reorder level, the shop's rules, the day start and the transfer-QR fingerprints: one row per setting, the name without its old `:` (`shopname`, `shopaddr`, `shopphone`, `shoptax`, `lowstock`, `daystart`, `rule...`). Before this they were rows of `app_flags` whose *name* held the value (`shopname:Quán Mai`). `AppFlags.valueWithPrefix` reads `settings` first and, only when there is no row, the old flag; a save writes `settings` and deletes the old flag in the same transaction, so nothing needs to be copied by hand and an old database moves one setting at a time. It is copied by the SQLite import, kept by "Start selling for real" and not meant to be edited by hand (like `app_flags`).
- A brand-new *table* (not a new column) needs no numbered step and no version change: add its `CREATE TABLE IF NOT EXISTS` to both `SqliteSchema.TABLES` and `MariaDbSchema.TABLES` (and to `MariaDbSchema.DROP_ORDER`), because that list runs at every start-up; `login_attempts` (TODO 2A.19) was added this way. Say in `SqliteToMariaDbImporterTest` if its rows are not worth copying to a new server.
- **A new column is a numbered step (TODO 6.6).** Do not add it to `SqliteSchema.LATER_COLUMNS`: that list is frozen at 50 entries (`LaterColumnsAreFrozenTest`). Raise `SchemaMigrations.LATEST_VERSION`, put the column in the `CREATE TABLE` of both `SqliteSchema.TABLES` and `MariaDbSchema.TABLES` (a new MariaDB database runs no step), and register `SchemaMigrations.addColumns(new String[]{"table", "column", "type"})` under the new number in `SchemaMigrations.SQLITE_STEPS` and `MariaDbMigrations.STEPS`. On SQLite the step and the version number are saved in one transaction. Add a test that starts from a database one version older (like `SchemaMigrationsTest.anOldDatabaseGetsTheIndexesOnUpgrade`). If the new column needs old rows filled in, do it in the same step.
- For whoever changes the tables later: a *new* MariaDB database is created at the latest version; an *existing* one is brought up by numbered steps in `MariaDbMigrations` (today three: version 3, the index on `orders.paid_at`, version 4, the two indexes on `refunds`, and version 5, the other plain indexes both databases share (audit log, stock log, expenses, reservations, shift closes, gift card moves, time clock, `orders (status, order_date)`), each for a database made before `MariaDbSchema` had them), driven by the `schema_version` table. SQLite does the same with `SchemaMigrations.SQLITE_STEPS` and `PRAGMA user_version` (version 2 = the indexes, version 3 = the `paid_at` index, version 4 = the two `refunds` indexes, version 5 = the rest of the mirrored indexes; `MariaDbSchemaTest` compares the plain indexes of the two databases, table and columns, not only the columns). The date filters of the history, the reports and the dashboard are ranges on `paid_at` and `order_date` (`EffectiveDay`), never `DATE(...)` around a column, so these indexes can be used. On SQLite a *new* file is set up in one transaction (every table, the later columns and `user_version` together, `DatabaseConfig.createFreshSqliteSchema`) and each numbered step is run with its version number in one transaction (`DatabaseConfig.runAtomically`), so a start that is killed half way leaves the file as it was; MariaDB commits every `CREATE TABLE` by itself, so its set-up is repeatable instead (everything is `IF NOT EXISTS` and the version is written last). An app older than the database refuses to start and says to update.

## 10. Running the automated tests on MariaDB (for whoever develops the app)

`./mvnw test` always uses SQLite (the pom sets `lunavera.db.type=sqlite` for the tests), so it needs no server. To run every database test on a real server, create a **throw-away** database and user:

```sql
CREATE DATABASE lunavera_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lunavera_test'@'localhost' IDENTIFIED BY 'test-password';
GRANT ALL PRIVILEGES ON lunavera_test.* TO 'lunavera_test'@'localhost';
```

```
mvn test -Dlunavera.test.db=mariadb -Dlunavera.test.db.password=test-password
```

(`-Dlunavera.test.db.host / .port / .name / .user` change the other defaults.) The CI runs this on MariaDB 10.6, 10.11 and 11.4 and on MySQL 8.0 (TODO 6.85); MySQL 8.0 is allowed to fail there until it has been green once, so treat it as "should work, not yet proven". **Every table in that database is dropped first**, so the name must end with `_test`; anything else is refused. Tests about SQLite files and PRAGMAs skip themselves in this mode. The CI runs exactly this on every push, in the job `test-mariadb` of `.github/workflows/ci.yml` (a `mariadb:10.11` service container with the throw-away database above); the number of tests it prints is lower than on SQLite for that reason.

## 11. How long each kind of data is kept (TODO 6.75)

Read from the code (every `DELETE FROM` in the program, and the folders that prune themselves), not decided by a rule: **the program has
no retention period for personal data and no automatic clean-up of the tables.** Rows are removed only by the actions in the last column.
The owner's dashboard shows how many rows the growing tables hold (the second line of the database health line, from `DatabaseHealth`).

| What | Where | Kept | What removes it |
|---|---|---|---|
| Sales: orders, their lines, payments, refunds | `orders`, `order_items`, `order_payments`, `refunds` | For ever | Nothing. A cancelled or refunded order stays as a row. Lines of an order that is still open can be removed on the till; the lines of a paid order never are. |
| Audit log (who did what) | `audit_log` | For ever | Nothing. |
| Stock log (receipts, sales, waste, counts) and stock counts | `stock_log`, `stock_takes` | For ever | Nothing. There is no separate waste table: waste is lines of `stock_log`. |
| Clock-ins, shift closes | `time_clock`, `shift_closes` | For ever | Nothing. |
| Bookings | `reservations` | For ever | Nothing. A guest's name and phone in it are removed only by making that customer anonymous. |
| Gift cards and their movements, vouchers | `gift_cards`, `gift_card_moves`, `vouchers` | For ever | Nothing. |
| Expenses | `expenses` | Until an administrator deletes one | One at a time on the expenses screen; the audit log keeps the date, category and amount. |
| Customers | `customers` | Until made anonymous or deleted | Deleted only when unused: no points, no order and no login (`CustomerDAO.deleteIfUnused`; merging two customers does the same). A customer with orders is made anonymous by hand (name, phone, e-mail, birthday and points go; the orders stay). |
| Staff records and accounts | `employees`, `users` | Until removed | Removed only when the person has no history (no order they took, paid or cancelled, no stock-log line, no closed shift and no audit line: `EmployeeDAO.hasHistory`); otherwise marked as left and made anonymous by hand. The last active administrator cannot be removed. |
| Menu: products, categories, ingredients, recipes, options, promotions | `products`, `categories`, `ingredients`, `recipes`, `product_options`, `promotions`, `promotion_targets` | Until the owner deletes them | Deleted by hand; a product that an order used and a category that products use are only stopped, never deleted. |
| Dining tables | `dining_tables` | Until the owner removes one | In the table manager, only a free table with no booking waiting, and never the last one. |
| Wrong-password counters | `login_attempts` | 15 minutes after the last wrong try, at most 1000 kept | Automatic (`LoginAttemptsDAO.trim`). |
| Settings and one-off notices | `settings`, `app_flags` | Settings: until changed. Notices: until shown once | Automatic. |
| Test sales before opening day | `orders`, `audit_log` and the other tables listed in "Clearing the test data" below | Until the owner presses "Start selling for real" | That button (a `before-opening-...zip` is made first). |

**Files outside the database** (all in `data/`):

| What | Kept | What removes it |
|---|---|---|
| `backups/auto-*.zip` (daily, automatic) | The newest 14, so about two weeks | Automatic. Only every 7th one holds the pictures. |
| `backups/before-restore-*.zip`, `before-cleanup-*.zip` | The newest 3 of each | Automatic. |
| `backups/before-opening-*.zip` | For ever | Nothing: it is the only way back from clearing the test data. |
| `backups/pre-upgrade-v..-to-v..` (taken when a version changes the table layout) | For ever | Nothing; there is one per upgrade, so few. Delete old ones by hand once the new version has run for a while. |
| Backups saved by hand (a USB stick, another folder) and the second backup folder | By hand. The second backup folder keeps the newest 14. | The second folder prunes itself; the rest is the owner's. |
| `snapshots/` (hourly copies, MariaDB shops only) | The newest 10, so about ten hours | Automatic. |
| `logs/app-*.log` (the program's own log) | 3 files of 1 MB | Rotates by itself. |
| `logs/security-YYYY-MM*.log` | 24 months | Months older than that are deleted when the program starts. |
| Booking-reminder list (a small file) | 3 days | Rewritten without older lines. |
| `images/` (product pictures) | Until the owner deletes them | "Unused pictures" on the dashboard, after a `before-cleanup` backup. |

**What this means for personal data.** A customer made anonymous is changed in the database only: **every backup made earlier still holds
the name and phone as they were** (the automatic ones for up to about two weeks, a copy saved by hand, a `before-opening` or a
`pre-upgrade` file for ever). A reasonable period for customer data and the audit lines has not been chosen: it depends on the law for a
shop's accounts and on the personal-data law (`docs/PERSONAL_DATA.md`, question 4 for the adviser). Until then the table above is the
honest answer to "how long do you keep it?".

**Decision for the owner (TODO 6.75, not made by the program).** The program does not trim the audit lines and does not do a yearly
export. What it can already save as CSV: the order list (with a date range), the customers, the products, the revenue book, the report,
each closed shift's sheet and the staff hours. Not exported: the audit log, the stock log and the clock-ins as rows. Two things the owner
can decide once, after the adviser has answered: (1) whether to save the order list, the report and the shift sheets of a finished
year to CSV and keep that file with the accounts (this needs no change to the program); (2) whether the oldest audit and stock-log
lines should ever be removed. Removing them needs a new, deliberate action with a backup first, as "Start selling for real" has, and
should be built only with a period the adviser has named; nothing was built for it.

## The "one at a time" rules (TODO 2.98)

Three rules are rules of the database itself, so two tills working at the same moment cannot break them: one `OPEN` order per table, one open clock-in per person, and one take-away number once per shop day. SQLite has them as unique indexes (`ux_orders_one_open_per_table` and `ux_time_clock_one_open` are partial indexes, `ux_orders_queue_day_no` is on `queue_day, queue_no`). MariaDB/MySQL have no partial indexes, so each rule is a unique key on a stored generated column that is empty while the rule does not apply: `orders.open_table_key` (the table number while the order is `OPEN`) and `time_clock.open_user_key` (the person while `clock_out` is empty), plus a plain unique key on `orders.queue_day, queue_no`. Needs MariaDB 10.2.1 or newer, or MySQL 5.7 or newer. A new database is created with them; an older one gets them when the program starts. If duplicates already exist, the rule is skipped with a warning in the log and tried again at the next start; to find them: `SELECT table_number FROM orders WHERE status = 'OPEN' GROUP BY table_number HAVING COUNT(*) > 1`, `SELECT user_id FROM time_clock WHERE clock_out IS NULL GROUP BY user_id HAVING COUNT(*) > 1`, `SELECT queue_day, queue_no FROM orders WHERE queue_no IS NOT NULL GROUP BY queue_day, queue_no HAVING COUNT(*) > 1`. When a till is refused by one of these rules the person sees the same clear message as before; a take-away order whose number another till took at the same moment is saved again with a new number.

## Clearing the test data before opening day (TODO 4.57)

Settings has "Bắt đầu bán thật" / "Start selling for real" (administrators only). It makes a full backup first (`before-opening-<time>.zip` in the backups folder, never pruned), asks for the shop's name, and then, in one transaction, **empties** `orders`, `order_items`, `order_payments`, `refunds`, `stock_takes`, `gift_cards`, `gift_card_moves`, `expenses`, `stock_log`, `shift_closes`, `time_clock`, `reservations` and `audit_log`, frees every row of `dining_tables`, sets `vouchers.used_count` to 0 and sets the points of the customers to 0. With the second tick-box it also empties `customers` (a login in `users` that pointed at one is kept and unlinked). **Kept:** `employees`, `users`, `categories`, `products`, `dining_tables`, `ingredients`, `recipes`, `product_options`, `promotions`, `promotion_targets`, `vouchers`, `login_attempts`, `app_flags`, `schema_version`. Stock numbers and the order-number counter are not reset. `StartSellingRulesTest` fails when a table is added to a schema and not listed as emptied or kept in `service/StartSellingRules`.
