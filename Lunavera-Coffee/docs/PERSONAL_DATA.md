# Personal data: what the program keeps, what protects it, and a draft notice for an adviser

TODO 2A.4 (customer data stored and backed up in plain form). **This file is not legal advice and the notice in it is a draft that
nobody qualified has read.** The program does not show any legal text to customers or staff; nothing below is in the app. The job
left in 2A.4 is for a Vietnamese lawyer or data-protection adviser: check the facts in parts 1 to 3, answer the questions in part 5,
and approve or rewrite the notice in part 4.

## 0. The law has changed since this item was written

The item talks about "the personal-data decree" (Decree 13/2023/ND-CP, in force from 1 July 2023). According to the summaries found on
2026-11-03, it has been replaced: the **Law on Personal Data Protection No. 91/2025/QH15** (passed 26 June 2025) took effect on
**1 January 2026**, and **Decree 356/2025/ND-CP** (issued 31 December 2025, in force 1 January 2026) guides it and supersedes Decree
13/2023. The summaries mention consent that must be stored and provable, silence not being consent, rights to access, correct, delete and
de-identify, notice of a breach, an impact assessment, and deletion of employees' data after a contract ends. **I could not read the
texts themselves, and the summaries say nothing about small businesses or household businesses** (an exemption or a transition period
may or may not exist): that is the first question for the adviser.
Sources: [LuatVietnam summary](https://english.luatvietnam.vn/legal-updates/the-latest-law-on-personal-data-protection-and-the-guiding-documents-892-106778-article.html),
[PwC Vietnam note of 28 Jan 2026](https://www.pwc.com/vn/en/publications/legal-news-brief/20260128-new-rules-personal-data-protection.html).

## 1. What the program stores about people (read from the code)

| Who | What | Where |
|---|---|---|
| Customer | full name, phone, e-mail (optional), birthday (optional), loyalty points, orders they made | `customers`, `orders.customer_id` |
| Customer with a login | username, password hash (PBKDF2), status | `users` |
| Table booking guest | name and phone as typed, people, time, note | `reservations` (and `customer_id` when the phone matched a customer, TODO 2.88) |
| Staff | full name, phone, e-mail, address, position, **salary**, hire date, clock-in times, login and PIN hash | `employees`, `users`, `time_clock` |
| Anyone signed in | who did what and when (names of staff, sometimes the name of a customer) | `audit_log`, the monthly security log file |
| Free text typed by staff | line notes, discount and cancel reasons, booking notes | `order_items.note`, `orders.discount_reason`, `orders.cancel_reason`, `reservations.note` (not searched for names) |

Not stored: payment card numbers (a card or wallet payment records only the method and, for a transfer, the bank's reference typed by
the cashier), identity-card numbers, photos of people, location, biometric data. The program has **no field or screen that records
that a customer agreed to anything.**

## 2. What already protects it

- The data folder is set to owner-only at every start: `rwx------` on Linux and macOS, an owner-only access list on Windows
  (`DataPaths.restrictToOwner`, items 2A.14 and the 8th pass). A folder on a USB stick or a network drive is not covered.
- A backup can be locked with a passphrase (`.lvbak`, AES, `BackupCrypto`); without one the backup message warns that the zip holds
  names, phones and password hashes unencrypted.
- A customer can be made anonymous (name replaced, phone, e-mail, birthday and points removed, login locked, audit lines and bookings
  scrubbed; the orders stay for the accounts). A former member of staff can be made anonymous the same way (TODO 4.29).
- Passwords are stored as PBKDF2 hashes; there is a login delay, an idle logout and a security log (items 2A.25, 2A.31, 2A.37).
- A MariaDB server can be reached with TLS (`db.ssl`, item 2A.26); without it a server on another computer sees everything in clear.

## 3. Gaps the program has today (for the adviser to rank)

1. **No record of consent** and no place to show the shop's privacy notice when a customer's name and phone are taken at the till.
2. **No retention rule.** A customer is never deleted; orders, audit lines, bookings and security logs are kept without an end date. *(What is kept and for how long is now written down, table by table, in `docs/DATABASE.md` section 11; two corrections to this sentence: a customer nobody used is deleted, and the monthly security-log files are removed after 24 months. No period has been chosen.)*
   Backups (the last 14 automatic ones, the last 3 "before restore", 10 hourly SQLite snapshots next to a MariaDB shop) **keep the
   data as it was**: making a customer anonymous does not change a backup made earlier.
3. **No procedure for a customer's request** (see my data, correct it, delete it, stop using it). Anonymising covers "delete" for the
   customer row only; there is no export of "everything held about me".
4. **No breach procedure** and nothing in the program that tells anyone about a lost USB stick or stolen laptop.
5. **Staff data:** no rule for deleting an employee's data some time after the contract ends (the program only has the manual anonymise).
6. The data folder on a **stolen or shared computer** is protected only by the operating-system account; the database itself is not
   encrypted (SQLite file), and an unencrypted backup is a normal zip.
7. Free-text notes may hold a name or a phone number; nothing looks for them.

## 4. DRAFT notice (a starting point to rewrite, not text to publish)

> **Notice about your personal data - [shop name], [address], tax code [..], contact [phone / e-mail]** *(draft, to be checked by an adviser)*
>
> **What we collect:** your name and phone number (and, if you give them, your e-mail and birthday) when you join our loyalty
> programme or book a table; the orders you make with us and the points you earn.
> **Why:** to find your account at the till, give you points and discounts, keep your table, and keep our sales records.
> **Who sees it:** our staff who need it for their work. We do not sell it. *[Say here if a delivery app, an accountant or any other
> company gets a copy.]*
> **How long:** *[period to be decided]*; the sales records are kept as long as the law requires for accounts. *[adviser]*
> **Your rights:** to ask what we hold about you, to have it corrected, to take back your agreement, and to ask us to delete it or make
> it anonymous. Ask any member of staff or write to [contact]; we answer within *[time set by the law]*.
> **Your agreement:** by giving your phone number at the till you agree to the above. *[The law says silence is not agreement and the
> shop must be able to prove it: the adviser says how (tick on a card, a signature, a tick in the program).]*

Vietnamese version to be written by the adviser (the shop's customers read it; a machine draft of legal text is not safe to hand out).

## 5. Questions for the adviser

1. Which law and decree apply to a single coffee shop, as a company, as a household business, and as a sole trader? Is there a
   small-business or start-up exemption or transition period, and until when?
2. Is this a "data controller" duty with a registration, a **personal-data impact assessment** or a notice to the authority? What
   exactly must the shop file, and when?
3. What form of **consent** is enough for a phone number given at a till for loyalty points, and how must it be kept and proved?
4. How long may the shop keep customer data, order history, booking data, audit lines and backups? What must a backup do when the
   customer asked for deletion?
5. How fast must a customer's request be answered, and what must the answer contain?
6. What must the shop do, and within how many hours, after a lost or stolen computer or USB stick that held an unencrypted backup?
7. Staff data (salary, address): what is the retention period after the contract ends?
8. Is the notice in part 4 enough, in what language and where must it be shown (at the till, on a sign, on the screen)?

## 6. What the program could do once the adviser has answered (not built: each depends on an answer above)

- A tick "customer agreed" with a date on the customer row, and the notice text shown in the customer box (needs the wording).
- A retention setting and a button that makes old customers anonymous (needs the period).
- "Everything held about this customer" export (needs the answer to question 5).
- Encrypt the backup by default, or refuse to write an unencrypted one to a removable drive.

## 7. Column by column (kept true by a test)

Part 1 is written for a reader; this part is the list the program can be checked against. `PersonalDataDocTest` fails when a column of
the shop database that looks like it holds a person's data (a name, phone, e-mail, address, salary, birthday, login, a copied name of
who did something, or free text) is not in this table, when a column in this table does not exist, and when the last column says
**scrubbed** but the program has no statement that rewrites that column when the person is made anonymous. Where a column says
**kept**, it stays after anonymising: either it holds an id or a number and no name, or it is free text that nothing looks into.
Backups made earlier keep everything as it was (part 3, item 2).

| Column | About | What it holds | When the person is made anonymous |
|---|---|---|---|
| `customers.full_name` | customer | name | scrubbed |
| `customers.phone` | customer | phone | scrubbed |
| `customers.email` | customer | e-mail (optional) | scrubbed |
| `customers.birthday` | customer | birthday (optional) | scrubbed |
| `customers.birthday_used_on` | customer | the day the birthday discount was last used | kept (a date, no name) |
| `employees.full_name` | staff | name | scrubbed |
| `employees.phone` | staff | phone | scrubbed |
| `employees.email` | staff | e-mail | scrubbed |
| `employees.address` | staff | home address | scrubbed |
| `employees.position` | staff | job title | scrubbed |
| `employees.salary` | staff | monthly salary | scrubbed |
| `employees.hire_date` | staff | hire date | kept (a date, no name) |
| `users.username` | staff or customer | login name | scrubbed |
| `users.username_key` | staff or customer | login name in lower case, for matching (a generated column of the MariaDB table: the database fills it from `users.username`) | scrubbed (generated from `users.username`) |
| `users.password_hash` | staff or customer | PBKDF2 hash of the password | scrubbed |
| `users.pin_hash` | staff | PBKDF2 hash of the PIN | scrubbed |
| `users.pin_failures` | staff | count of wrong PINs | kept (a number, no name) |
| `users.must_change_password` | staff or customer | a yes/no flag | kept (a flag, no name) |
| `login_attempts.name_key` | anyone who tried to sign in | the login name typed, lower case, also a wrong one | kept (cleared when the delay ends) |
| `orders.paid_by` | staff | id of the staff record that took the payment | kept (id only) |
| `orders.cancelled_by` | staff | id of the staff record that cancelled | kept (id only) |
| `orders.complimentary_for` | staff | id of the staff member a free order was for | kept (id only) |
| `orders.discount_reason` | staff typed | free text | kept (free text, not searched) |
| `orders.cancel_reason` | staff typed | free text | kept (free text, not searched) |
| `orders.complimentary_note` | staff typed | free text | kept (free text, not searched) |
| `orders.transfer_ref` | staff typed | the bank's reference of a transfer | kept (free text, not searched) |
| `order_items.note` | staff typed | line note, for example no sugar | kept (free text, not searched) |
| `refunds.refunded_by` | staff | id of the staff record | kept (id only) |
| `refunds.refunded_by_name` | staff | name copied at the time | scrubbed |
| `refunds.reason` | staff typed | free text | kept (free text, not searched) |
| `reservations.customer_name` | booking guest | name as typed | scrubbed |
| `reservations.phone` | booking guest | phone as typed | scrubbed |
| `reservations.note` | staff typed | free text | kept (free text, not searched) |
| `reservations.created_by` | staff | name copied at the time | scrubbed |
| `time_clock.user_name` | staff | name copied at the time | scrubbed |
| `gift_cards.sold_by` | staff | name copied at the time | scrubbed |
| `stock_log.employee_name` | staff | name copied at the time | scrubbed |
| `stock_log.supplier` | supplier | name as typed, may be a person | kept (free text, not searched) |
| `stock_log.note` | staff typed | free text | kept (free text, not searched) |
| `expenses.recorded_by` | staff | id of the staff record | kept (id only) |
| `expenses.recorded_by_name` | staff | name copied at the time | scrubbed |
| `expenses.note` | staff typed | free text | kept (free text, not searched) |
| `shift_closes.closed_by` | staff | id of the staff record | kept (id only) |
| `shift_closes.closed_by_name` | staff | name copied at the time | scrubbed |
| `shift_closes.note` | staff typed | free text | kept (free text, not searched) |
| `audit_log.actor_name` | staff | name copied at the time | scrubbed |
| `audit_log.target_label` | staff or customer | whom the action was about | scrubbed |
| `audit_log.old_value` | staff or customer | the value before a change, may be a name or phone | scrubbed |
| `audit_log.new_value` | staff or customer | the value after a change, may be a name or phone | scrubbed |
| `audit_log.detail` | staff or customer | a short text about the action | scrubbed |

Two things the table shows that part 1 does not say: **the name of a member of staff is copied as text into eight other tables** (refunds,
expenses, the stock log, shift closes, the time clock, gift cards, bookings and the audit log), and the program rewrites all eight when
the person is made anonymous (`AccountService.anonymiseStaff`, TODO 2.87); and **`login_attempts` keeps the login name typed by anyone,
including a wrong one**, until the delay for it ends.
