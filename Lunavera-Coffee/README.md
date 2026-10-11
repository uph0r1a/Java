# Lunavera Coffee

A desktop coffee-shop point of sale (JavaFX 21, SQLite). The screens are in Vietnamese.

## Run and test

```
mvn javafx:run      # start the app (run it from this folder)
mvn test            # run the tests
```

Needs JDK 21. First login: user `admin` with the default password (ask the owner); the app **makes** you set a new password before you can continue. Anyone can change their own later with the "Đổi mật khẩu" button at the bottom of the sidebar.

## Where your data is

Everything the shop saves lives in one folder, **`data/`**, next to `pom.xml`:

| Path | What it is |
| --- | --- |
| `data/lunavera.db` | the database: products, orders, customers, accounts |
| `data/images/` | product pictures |
| `data/logs/` | log files (for finding out what went wrong) |
| `data/backups/` | automatic copies made before risky one-time changes |

The folder is created on the first start and is **not** part of git (see `.gitignore`): it holds
customer phone numbers and password hashes, so never commit or share it.

**Old versions** kept the data in `~/.lunavera-coffee` (on Windows `C:\Users\<name>\.lunavera-coffee`).
The first time a new version starts with no `data/lunavera.db`, it **copies** the database, pictures and
backups from there into `data/`. The old folder is left as it was.

### Keep the data safe

- `data/` is found relative to the folder the app is **started from**. Start it from the project folder
  (`mvn javafx:run`). For an installed or packaged copy, or an IDE run configuration that starts elsewhere,
  say where the data is: `-Dlunavera.dataDir="D:\LuneveraData"` or the environment variable `LUNAVERA_DATA_DIR`.
- `mvn clean`, switching git branches and rebuilding do **not** delete `data/`.
- Updating by extracting a new zip: extract it **over** the old project folder, or copy your `data/` folder
  into the new one. A new empty project folder starts with a new empty `data/`.
- A backup in `data/backups/` (or one saved inside `data/`) is on the same disk as the data. Use the
  "Sao lưu dữ liệu" button on the admin dashboard now and then and copy the file to a USB stick or
  another computer.
- To restore: close the app and replace `data/lunavera.db` with the backup file.
