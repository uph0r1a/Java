# The database: SQLite (default) or MariaDB/MySQL with phpMyAdmin

The app works with **one of two databases**, and **MariaDB/MySQL is the default**:

| | MariaDB / MySQL (default) | SQLite |
| --- | --- | --- |
| Setup | install a database server once (section 1) | none: one file, `data/lunavera.db` |
| Edit the data by hand | **phpMyAdmin** in the browser | an external tool (DB Browser for SQLite) |
| Several tills on one database | yes (all point at the same server) | no |
| If the computer with the data is off | the tills cannot work (the server is the shop) | that till cannot work |

Without `data/database.properties` the app looks for a server on this computer (`127.0.0.1:3306`, database `lunavera`); if there is none it stops at
start-up and tells you what to do. To use the old SQLite file instead, put `db.type=sqlite` in that file.
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
GRANT ALL PRIVILEGES ON lunavera.* TO 'lunavera'@'localhost';
FLUSH PRIVILEGES;
```

The app creates its own tables the first time it connects (it needs the right to create and alter tables in this one database; it needs nothing on any other).
For other tills on the shop network create the user as `'lunavera'@'192.168.1.%'` (your network) as well, and see "Security" below.

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

Every key can also be given on the command line as `-Dlunavera.db.name=...` (a command-line value wins over the file).
Start the app. The first start creates the tables, the 12 dining tables and the starter menu, then asks the owner to create the first administrator (there is no built-in `admin` password), exactly like a new SQLite database. If `db.import.sqlite` is set (section 4), the old accounts come over instead and no account is asked for.

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
- A file that has no `users` table is not a Lunavera database: it is ignored with a warning and nothing is changed.

Afterwards remove the line (or leave it: once there is real data it does nothing but write one log line), and keep `data/lunavera.db` as a safety copy until you are sure.

**Check after the first start:** compare the numbers of products, customers and orders and today's revenue on the dashboard with the SQLite version, and open a few orders.

## 5. Editing in phpMyAdmin

When the shop runs on MariaDB, the admin dashboard has a **"Mở phpMyAdmin"** button (admin only). It asks first, then opens
`http://localhost/phpmyadmin/` in the browser; if phpMyAdmin lives elsewhere, set `db.phpmyadmin=http://server/phpmyadmin/` in `data/database.properties`
(only `http://` and `https://` addresses are accepted).

phpMyAdmin changes the data **without the app's rules**, so be careful:

- Take a backup first (the app's "Sao lưu dữ liệu", or phpMyAdmin **Export**).
- Values the app expects:
  - statuses are **UPPER CASE text**: orders `OPEN`, `PAID`, `CANCELLED`; users `ACTIVE`, `LOCKED`; roles `ADMIN`, `EMPLOYEE`; payment `CASH`, `CARD`, `TRANSFER`; tables `EMPTY`, `OCCUPIED`;
  - **money is whole dong** (a price of 25.000đ is `25000`);
  - customer phones in the `0...` form (`0912345678`, not `+84...`), and one customer per phone (a blank phone is NULL, not an empty string);
  - never edit `password_hash` by hand (use the app's account screen), and never touch `app_flags` and `schema_version`.
- Do not change a **paid** order's lines or totals: stock and loyalty points were already applied (and the report adds up the saved totals). Cancel it in the app's order history instead.
- A product that has been sold cannot be deleted (the database refuses); hide it (`active` = 0) instead.
- Screens show the new values the next time they are loaded.

## 6. Backups and restore

- The dashboard button **"Sao lưu dữ liệu"** and the daily automatic backup make a `.zip` that holds `lunavera.sql` (a `mysqldump`) plus the pictures. They run the `mysqldump` program, which must be on the computer that runs the app:
  XAMPP has it at `C:\xampp\mysql\bin\mysqldump.exe`; if it is not on the PATH, set `db.mysqldump=C:\\xampp\\mysql\\bin\\mysqldump.exe` in `data/database.properties`. The password is not shown in the process list (it travels in an environment variable).
- **Restore** is done by hand (the in-app "Khôi phục dữ liệu" is for SQLite only): unzip the backup, in phpMyAdmin choose the database, **Import**, pick `lunavera.sql` (it replaces the tables), then copy the zip's `images` folder into `data/images`.
- Copy backups off the computer (USB stick, another PC). A backup holds customer phones and password hashes.

### Hourly SQLite snapshots (automatic)

While the shop runs on MariaDB, the app writes a **SQLite copy of all the data** to `data/snapshots/lunavera-yyyyMMdd-HHmmss.db` every hour while it is open
(the first about 30 seconds after start-up, if the newest is older than an hour) and keeps only the **newest 10**; older ones are deleted.
Each snapshot is one consistent view of the data, checked to have the same number of rows in every table, and is written under a temporary name and renamed
when complete, so a broken half-file is never mistaken for a good one.

- Open one in DB Browser for SQLite to look at the data without the server.
- To run the app on a snapshot (for example the server is gone): close the app, copy the snapshot over `data/lunavera.db` (and delete `data/lunavera.db-wal` / `-shm` if they exist), put `db.type=sqlite` in `data/database.properties`, start.
- A snapshot does **not** contain the product pictures (`data/images`) and holds customer phones and password hashes, like any backup.
- A SQLite shop gets no snapshots (the database already is a file; the backup button and the daily zip cover it). A failed snapshot is written to the log and tried again an hour later.

## 7. Security (read this)

- Keep the server for the **shop network only**. Never open port 3306, or phpMyAdmin, to the internet: that is the usual way shops get hacked.
- XAMPP's defaults are for learning: `root` has **no password** and phpMyAdmin lets anyone in. Set a root password, and in phpMyAdmin's `config.inc.php` use `auth_type = 'cookie'` so it asks for a login. Stop Apache when you do not need phpMyAdmin.
- With a single till keep MariaDB listening on this computer only (`bind-address=127.0.0.1` in `my.ini`, the XAMPP default). Only if other tills must connect, allow your network and add a Windows Firewall rule for the shop network only.
- `data/database.properties` contains the database password: it is outside git; do not email it or put it in a shared folder.
- Use the `lunavera` user (not `root`) in that file.

## 8. When something is wrong

| Message / symptom | Usual cause |
| --- | --- |
| The app does not open and says it cannot reach the database server | The server is not running (XAMPP: start MySQL), wrong `db.host` / `db.port`, or a firewall. The till cannot sell without the server. |
| `Access denied for user ...` | Wrong `db.user` / `db.password`, or the user was created for another host (`'lunavera'@'localhost'` only allows this computer). |
| `Unknown database` | The `CREATE DATABASE` step was skipped, or `db.name` is spelled differently. |
| `Public Key Retrieval is not allowed` (MySQL 8) | Make the user use the older login: `ALTER USER 'lunavera'@'localhost' IDENTIFIED WITH mysql_native_password BY '...';` (MariaDB does not need this). |
| Backup says it cannot run `mysqldump` | Set `db.mysqldump` to its full path (section 6). |
| In the middle of a sale: "Không kết nối được máy chủ cơ sở dữ liệu" | The server stopped or the network dropped. Start it again, then repeat the last action; nothing half-saved is kept (each save is one transaction). |
| "Cơ sở dữ liệu đang bận" | Another till was writing at the same moment. Try again. |
| Import stopped with a message | Read the message; MariaDB stays empty (rolled back). Fix the cause and start again. |

To go back to SQLite put `db.type=sqlite` in the file (the app then uses `data/lunavera.db`). Data added to MariaDB in the meantime is **not** copied back, except through a snapshot (section 6).

## 9. Differences from SQLite (on purpose)

- Text compares ignoring **accents and capitals**: the categories `Trà` and `tra` count as the same name, and a search for `ca phe` finds `Cà phê`. Login names also ignore capitals in the app (`admin` and `ADMIN` are the same account when signing in or creating one). On SQLite the database enforces it as well; on MariaDB only the app does, so do not add two accounts that differ only in capitals in phpMyAdmin.
- Dates are real `DATETIME` columns; money is `BIGINT` (whole dong), the same numbers as before.
- Only one connection is opened per action (no connection pool yet).
- For whoever changes the tables later: a *new* MariaDB database is created at the latest version; an *existing* one is brought up by numbered steps in `MariaDbMigrations` (empty today), driven by the `schema_version` table. An app older than the database refuses to start and says to update.

## 10. Running the automated tests on MariaDB (for whoever develops the app)

`mvn test` always uses SQLite (the pom sets `lunavera.db.type=sqlite` for the tests), so it needs no server. To run every database test on a real server, create a **throw-away** database and user:

```sql
CREATE DATABASE lunavera_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lunavera_test'@'localhost' IDENTIFIED BY 'test-password';
GRANT ALL PRIVILEGES ON lunavera_test.* TO 'lunavera_test'@'localhost';
```

```
mvn test -Dlunavera.test.db=mariadb -Dlunavera.test.db.password=test-password
```

(`-Dlunavera.test.db.host / .port / .name / .user` change the other defaults.) **Every table in that database is dropped first**, so the name must end with `_test`; anything else is refused. Tests about SQLite files and PRAGMAs skip themselves in this mode.
