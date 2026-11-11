# Guide for the owner

One page for the administrator. Button names are written in bold type, exactly as on the screen. How to work the till is in the cashier guide.

## 1. Getting started

- On a new database the first start makes you create the owner account (name, username, password). There is no built-in password. **First-time setup** asks for the shop name and the number of tables; you can press **Later**.
- After the trial period, before opening day: **Shop settings** → **Start selling for real...** clears the test orders and records and keeps the menu, tables and staff. The program makes a backup before it deletes.

## 2. People and accounts

- **Staff**: the personnel file (salary, position, start date), including people with no login. **Mark as left** when they leave.
- **Accounts**: username, role, password, and **Lock** an account. A cashier sees only the sales and customer screens.
- Everybody changes their own password with **Change password**; a cashier can set a **PIN code** to unlock the till quickly.

## 3. Menu and stock

- **Products** and **Categories**. Press **Receive stock** to record goods received (cost, supplier) and see the stock log; **Record waste** for spoiled, spilled or expired stock.
- **Ingredients** (**Ingredients and recipes**): recipes take stock off when a product is sold. Sizes and toppings are set on the product screen; the report has a tab **Sizes and extras**.
- Products running low are shown on the home screen; the common level is set in **Shop settings**.

## 4. Discounts and regulars

- **Vouchers**: **Create voucher** (percent or amount, minimum bill, number of uses, date range, **One use per customer**). **Switch the selected voucher on / off** stops a code without deleting it.
- **Auto promotions**: percent off, fixed price, buy X get Y, combo price, by day and hour (for example happy hour).
- **Shop settings** → **Shop rules**: the most a cashier may discount, how points are earned and spent, the Silver and Gold thresholds, the birthday discount.
- **Customers**: only the owner changes a phone number, merges duplicates, makes a customer anonymous, or exports the list.

## 5. Gift cards

**Gift cards** → **Card list**: code, amount, balance, who sold it, when, last use, and the total the shop still owes customers on cards. **Void the selected card** works only for a card never used (the money goes back to the customer).

## 6. Tables and bookings

**Manage tables** (or **Shop settings** → **Manage tables...**): add, rename, remove tables; a table with an open order cannot be removed. Old bookings nobody closed show a reminder: **Close old bookings**.

## 7. Working hours

**Clock in / out** shows the owner everybody's hours over a range of days and who is still clocked in. A forgotten clock-out: **Close a forgotten clock-out** (type the real finishing time). A wrong or missed shift: **Correct or add hours...** (a reason is required). Pay in the table is only an estimate.

## 8. Reports and books

- **Revenue report**: by day, product, hour, weekday, category, employee and payment method; against the previous period; the **Discounts** button shows discounts by person and reason.
- **Sales book**: export the sales book for a period.
- **Expenses**: expenses and cash taken out of the drawer. The report tab **Staff and shift close** shows revenue per cashier and the closed shifts with their differences.
- **Order history**: only the owner can **Cancel order** or **Refund lines** (stock can go back on the shelf), and export the list.

## 9. Backups and data safety

- **Back up data**: do it every week and keep a copy outside the computer's disk (USB drive, cloud). A backup can have a password.
- **Second backup place**: choose one more folder (USB, another computer, a synced folder) so the automatic backups are also copied there. **Restore data** puts the data back to a backup: try it on a copy first, and the program always keeps a safety copy of the current data.
- **Open phpMyAdmin** exists only when the database is MariaDB.

## 10. Keeping watch

The **Audit log** records who did what: discounts, refunds, moving and merging tables, price changes, accounts and passwords, gift cards, hour corrections, restores, rule changes. The **Security log** holds the program's security events, view only. The **Transfer QR code** is the picture shown in the payment box: changing it changes where the money goes, so the program asks you to confirm.
