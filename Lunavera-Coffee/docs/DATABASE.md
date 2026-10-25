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
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER, LOCK TABLES ON lunavera.* TO 'lunavera'@'localhost';
FLUSH PRIVILEGES;
```

Why not `ALL PRIVILEGES`: the app reads and writes every day (`SELECT, INSERT, UPDATE, DELETE`) and needs `CREATE, ALTER, INDEX, REFERENCES, TRIGGER, LOCK TABLES` only when it starts and creates or upgrades its tables. It never needs `DROP`, `GRANT OPTION`, `CREATE USER` or any right on another database. With the narrow grant, a stolen till or a leaked `database.properties` cannot drop the tables. `DROP` (and a restore from phpMyAdmin) stays with your own administrator account, not the app's user.
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
  - statuses are **UPPER CASE text**: orders `OPEN`, `PAID`, `CANCELLED`; users `ACTIVE`, `LOCKED`; roles `ADMIN`, `EMPLOYEE`; payment `CASH`, `CARD`, `TRANSFER`; tables `EMPTY`, `OCCUPIED`;
  - **money is whole dong** (a price of 25.000đ is `25000`);
  - customer phones in the `0...` form (`0912345678`, not `+84...`), and one customer per phone (a blank phone is NULL, not an empty string);
  - never edit `password_hash` by hand (use the app's account screen), and never touch `app_flags` and `schema_version`.
- Do not change a **paid** order's lines or totals: stock and loyalty points were already applied (and the report adds up the saved totals). Cancel it in the app's order history instead.
- A product that has been sold cannot be deleted (the database refuses); hide it (`active` = 0) instead.
- Screens show the new values the next time they are loaded.

## 6. Backups and restore

- The dashboard button **"Sao lưu dữ liệu"** and the daily automatic backup make a `.zip` that holds `lunavera.sql` (a `mysqldump`) plus the pictures. They run the `mysqldump` program, which must be on the computer that runs the app:
  XAMPP has it at `C:\xampp\mysql\bin\mysqldump.exe`; if it is not on the PATH, set `db.mysqldump=C:\xampp\mysql\bin\mysqldump.exe` in `data/database.properties` (write the path as it is, with single backslashes; a file written with doubled backslashes in an older version still works on Windows). The password is not shown in the process list (it travels in an environment variable).
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

- Text compares ignoring **accents and capitals**: the categories `Trà` and `tra` count as the same name, and a search for `ca phe` finds `Cà phê`. Login names also ignore capitals in the app (`admin` and `ADMIN` are the same account when signing in or creating one). On SQLite the database enforces it as well; on MariaDB only the app does, so do not add two accounts that differ only in capitals in phpMyAdmin.
- Dates are real `DATETIME` columns; money is `BIGINT` (whole dong), the same numbers as before.
- Only one connection is opened per action (no connection pool yet).
- A brand-new *table* (not a new column) needs no numbered step and no version change: add its `CREATE TABLE IF NOT EXISTS` to both `SqliteSchema.TABLES` and `MariaDbSchema.TABLES` (and to `MariaDbSchema.DROP_ORDER`), because that list runs at every start-up; `login_attempts` (TODO 2A.19) was added this way. Say in `SqliteToMariaDbImporterTest` if its rows are not worth copying to a new server.
- For whoever changes the tables later: a *new* MariaDB database is created at the latest version; an *existing* one is brought up by numbered steps in `MariaDbMigrations` (empty today), driven by the `schema_version` table. An app older than the database refuses to start and says to update.

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

(`-Dlunavera.test.db.host / .port / .name / .user` change the other defaults.) **Every table in that database is dropped first**, so the name must end with `_test`; anything else is refused. Tests about SQLite files and PRAGMAs skip themselves in this mode. The CI runs exactly this on every push, in the job `test-mariadb` of `.github/workflows/ci.yml` (a `mariadb:10.11` service container with the throw-away database above); the number of tests it prints is lower than on SQLite for that reason.
