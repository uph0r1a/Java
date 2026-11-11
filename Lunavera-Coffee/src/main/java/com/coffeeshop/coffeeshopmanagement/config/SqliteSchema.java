package com.coffeeshop.coffeeshopmanagement.config;

import java.util.List;

/**
 * The SQLite table definitions, in the order they must be created (a table is created after the
 * ones it points to). Kept apart from {@link DatabaseConfig} so a test can compare them with
 * {@link MariaDbSchema} and catch a column added to one but not the other.
 *
 * <p>Columns added to an existing database later (see {@link #LATER_COLUMNS}) are listed here too,
 * because a brand-new SQLite database gets them through {@code addColumnIfMissing} right after creation.
 */
final class SqliteSchema {

    static final List<String> TABLES = List.of(
            """
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    address TEXT,
                    position TEXT,
                    salary INTEGER,
                    hire_date TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    loyalty_points INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    employee_id INTEGER,
                    customer_id INTEGER,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    category_id INTEGER,
                    price INTEGER NOT NULL,
                    cost INTEGER,
                    stock INTEGER NOT NULL DEFAULT 0,
                    description TEXT,
                    image_path TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (category_id) REFERENCES categories(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_date TEXT NOT NULL,
                    employee_id INTEGER,
                    customer_id INTEGER,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    subtotal INTEGER NOT NULL DEFAULT 0,
                    discount INTEGER NOT NULL DEFAULT 0,
                    total INTEGER NOT NULL DEFAULT 0,
                    payment_method TEXT,
                    paid_at TEXT,
                    table_number INTEGER,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS order_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    product_id INTEGER,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price INTEGER NOT NULL,
                    line_total INTEGER NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """,
            // The lines of an order paid with two methods (TODO 4.32): one row per method, the amounts add up to the order's total.
            // An order paid with one method has no rows here; orders.payment_method says it all.
            """
                CREATE TABLE IF NOT EXISTS order_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    method TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    UNIQUE (order_id, method),
                    FOREIGN KEY (order_id) REFERENCES orders(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS refunds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    order_item_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    line_amount INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    method TEXT NOT NULL,
                    restocked INTEGER NOT NULL DEFAULT 0,
                    reason TEXT,
                    refunded_by INTEGER,
                    refunded_by_name TEXT NOT NULL,
                    refunded_at TEXT NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (order_item_id) REFERENCES order_items(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS dining_tables (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_number INTEGER UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'EMPTY',
                    capacity INTEGER DEFAULT 4,
                    current_order_id INTEGER,
                    FOREIGN KEY (current_order_id) REFERENCES orders(id)
                )
            """,
            // The shop's settings (TODO 6.74): one row per setting, the value in its own column (before, the value was part of the name of
            // a row of app_flags). Read through AppFlags.valueWithPrefix, which still falls back to the old flag rows.
            """
                CREATE TABLE IF NOT EXISTS settings (
                    name TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS login_attempts (
                    scope TEXT NOT NULL,
                    name_key TEXT NOT NULL,
                    failures INTEGER NOT NULL DEFAULT 0,
                    last_failure INTEGER NOT NULL DEFAULT 0,
                    blocked_until INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (scope, name_key)
                )
            """,
            // What came into the shop and every hand-typed change of a stock number: who, when, how many (TODO 4.5). No foreign
            // keys on purpose: a product or a staff record may go later and the line keeps the name it had.
            """
                CREATE TABLE IF NOT EXISTS stock_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    logged_at TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    product_id INTEGER,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    stock_before INTEGER NOT NULL,
                    stock_after INTEGER NOT NULL,
                    unit_cost INTEGER,
                    supplier TEXT,
                    note TEXT,
                    employee_id INTEGER,
                    employee_name TEXT
                )
            """,
            // Who did what, when, and what it was before and after (TODO 4.2): discounts, refunds, a table emptied with an open order,
            // price and stock edits, account changes, backups and restores, customer and staff clean-ups, shift closes. Names are copies
            // and there are no foreign keys, like stock_log: the line must read the same after a person or a product is gone, and the
            // personal parts are rewritten when someone is made anonymous (AuditDAO.scrubActor / scrubTarget).
            """
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    logged_at TEXT NOT NULL,
                    actor_user_id INTEGER,
                    actor_employee_id INTEGER,
                    actor_name TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_type TEXT,
                    target_id INTEGER,
                    target_label TEXT,
                    old_value TEXT,
                    new_value TEXT,
                    detail TEXT
                )
            """,
            // One closed shift: the cash the till should hold against the cash counted (TODO 4.6). Names are copied, like the
            // order lines copy a product's name, so the history reads the same after a person is renamed or made anonymous.
            """
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    spent_on TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    note TEXT,
                    from_drawer INTEGER NOT NULL DEFAULT 0,
                    recorded_by INTEGER,
                    recorded_by_name TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    unit TEXT NOT NULL,
                    stock INTEGER NOT NULL DEFAULT 0,
                    min_stock INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS stock_takes (
                    order_item_id INTEGER NOT NULL,
                    ingredient_id INTEGER,
                    amount INTEGER NOT NULL,
                    UNIQUE (order_item_id, ingredient_id),
                    FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS recipes (
                    product_id INTEGER NOT NULL,
                    ingredient_id INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    PRIMARY KEY (product_id, ingredient_id),
                    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS product_options (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    name TEXT NOT NULL,
                    price_delta INTEGER NOT NULL DEFAULT 0,
                    cost_delta INTEGER NOT NULL DEFAULT 0,
                    recipe_percent INTEGER NOT NULL DEFAULT 100,
                    is_default INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    UNIQUE (product_id, kind, name),
                    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS time_clock (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    user_name TEXT NOT NULL,
                    clock_in TEXT NOT NULL,
                    clock_out TEXT
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS gift_cards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    initial_amount INTEGER NOT NULL,
                    balance INTEGER NOT NULL,
                    sold_by TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS gift_card_moves (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    card_id INTEGER NOT NULL,
                    order_id INTEGER,
                    amount INTEGER NOT NULL,
                    moved_at TEXT NOT NULL,
                    FOREIGN KEY (card_id) REFERENCES gift_cards(id)
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS reservations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_number INTEGER NOT NULL,
                    customer_name TEXT NOT NULL,
                    phone TEXT,
                    reserved_for TEXT NOT NULL,
                    people INTEGER NOT NULL,
                    note TEXT,
                    status TEXT NOT NULL DEFAULT 'BOOKED',
                    created_by TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    closed_at TEXT
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS vouchers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    kind TEXT NOT NULL,
                    value INTEGER NOT NULL,
                    min_subtotal INTEGER NOT NULL DEFAULT 0,
                    valid_from TEXT,
                    valid_to TEXT,
                    max_uses INTEGER,
                    used_count INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS promotions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    percent INTEGER NOT NULL DEFAULT 0,
                    fixed_price INTEGER NOT NULL DEFAULT 0,
                    buy_qty INTEGER NOT NULL DEFAULT 0,
                    free_qty INTEGER NOT NULL DEFAULT 0,
                    days TEXT NOT NULL DEFAULT '',
                    time_from TEXT,
                    time_to TEXT,
                    valid_from TEXT,
                    valid_to TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS promotion_targets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    promotion_id INTEGER NOT NULL,
                    product_id INTEGER,
                    category_id INTEGER,
                    quantity INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE,
                    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
                )
            """,
            """
                CREATE TABLE IF NOT EXISTS shift_closes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    period_start TEXT NOT NULL,
                    period_end TEXT NOT NULL,
                    closed_by INTEGER,
                    closed_by_name TEXT NOT NULL,
                    paid_orders INTEGER NOT NULL,
                    revenue INTEGER NOT NULL,
                    cash_sales INTEGER NOT NULL,
                    opening_cash INTEGER NOT NULL,
                    expected_cash INTEGER NOT NULL,
                    counted_cash INTEGER NOT NULL,
                    difference INTEGER NOT NULL,
                    note TEXT
                )
            """
    );

    /** How many entries {@link #LATER_COLUMNS} has; it must stay this number (TODO 6.6). */
    static final int LATER_COLUMNS_FROZEN = 50;

    /**
     * {table, column, SQLite type} added after the first release; created by {@code addColumnIfMissing} at every start.
     * <b>Frozen (TODO 6.6):</b> these {@link #LATER_COLUMNS_FROZEN} entries are the columns that went in before numbered steps took
     * over. A new column does NOT go here: it goes in a numbered step ({@code SchemaMigrations.addColumns}, registered under a new
     * version) and in the CREATE TABLE of both schema classes. {@code LaterColumnsAreFrozenTest} fails if this list grows.
     */
    static final List<String[]> LATER_COLUMNS = List.of(
            new String[]{"orders", "table_number", "INTEGER"},
            new String[]{"orders", "discount_reason", "TEXT"},
            new String[]{"orders", "paid_by", "INTEGER"}, // who took the money (TODO 2.47)
            new String[]{"order_items", "unit_cost", "INTEGER"}, // the product's cost when the line was paid; null = not known then (TODO 3.53)
            new String[]{"products", "reorder_level", "INTEGER"}, // the product's own low-stock level; null = the shop default (TODO 4.19)
            new String[]{"users", "pin_hash", "TEXT"}, // the hashed 4-6 digit PIN of a cashier, null = none (TODO 4.36)
            new String[]{"users", "pin_failures", "INTEGER NOT NULL DEFAULT 0"}, // wrong PINs in a row; at the limit the PIN is switched off (TODO 4.36)
            new String[]{"users", "must_change_password", "INTEGER NOT NULL DEFAULT 0"}, // set when an admin made or reset the password (TODO 2A.18)
            // Shown order of the tabs and cards on the sales screen; equal numbers (every old row has 0) keep the order of creation (TODO 5.31).
            new String[]{"categories", "sort_order", "INTEGER NOT NULL DEFAULT 0"},
            new String[]{"products", "sort_order", "INTEGER NOT NULL DEFAULT 0"},
            // The loyalty points this order gave; null = not recorded (an order paid before this column existed) (TODO 3.25).
            new String[]{"orders", "points_earned", "INTEGER"},
            // What the customer handed over and what went back, for a cash payment; null otherwise (TODO 3.17).
            new String[]{"orders", "cash_received", "BIGINT"},
            new String[]{"orders", "change_given", "BIGINT"},
            // The bank's reference of a transfer, typed by the cashier, optional (TODO 4.31). Who confirmed and when are paid_by and paid_at.
            new String[]{"orders", "transfer_ref", "VARCHAR(64)"},
            // Loyalty points spent on this order and what they were worth in dong (TODO 4.16). The value is also inside "discount"
            // (the total still equals subtotal - discount); these two say how much of the discount was points and are what a refund
            // gives back. Null on an order that spent none (and on every order made before points could be spent).
            new String[]{"orders", "points_redeemed", "INTEGER"},
            new String[]{"orders", "points_value", "BIGINT"},
            // The barcode / product code a scanner types; null = none. Unique when set, see SchemaMigrations.ensureUniqueProductCode (TODO 4.42).
            new String[]{"products", "code", "VARCHAR(64)"},
            // What the customer asked for on this line: "less sugar", "no ice" (TODO 4.9). Null = nothing.
            new String[]{"order_items", "note", "VARCHAR(200)"},
            // The size and extras the cashier picked on this line (TODO 4.47), kept as they were named and priced at the sale: the size's
            // name, how much of the recipe one piece used (100 = normal), the extras as "name=price;name=price", and what the size and
            // extras added to the cost of one piece. All null on a plain line and on every line made before.
            new String[]{"order_items", "size_name", "VARCHAR(40)"},
            new String[]{"order_items", "size_percent", "INTEGER"},
            new String[]{"order_items", "extras", "VARCHAR(400)"},
            new String[]{"order_items", "option_cost", "BIGINT"},
            // When a leaver's personal details were removed; null = not made anonymous (TODO 4.29).
            new String[]{"employees", "anonymised_at", "DATETIME"},
            // Who cancelled an order, when, and why (TODO 4.2): set when a paid order is refunded, a table is emptied with an open order
            // or an open take-away order is cancelled. Null on every order that was never cancelled and on cancellations made before.
            new String[]{"orders", "cancelled_by", "INTEGER"},
            // cancelled_at is also the day a paid order's refund counts on; null = not refunded, or refunded before the column existed (TODO 3.13).
            new String[]{"orders", "cancelled_at", "DATETIME"},
            new String[]{"orders", "cancel_reason", "VARCHAR(200)"},
            // When a customer's personal details were removed on request; null = not made anonymous (TODO 3.4 / 4.8, rule D21).
            new String[]{"customers", "anonymised_at", "DATETIME"},
            new String[]{"customers", "birthday", "DATE"}, // optional, for the birthday badge (TODO 7, birthdays)
            // The day the birthday discount was given to this customer (TODO 3.66, rule D25): set in the payment's own transaction, so it is
            // given once a day; null = never. Cleared when the customer is made anonymous.
            new String[]{"customers", "birthday_used_on", "DATE"},
            // Cash taken out of the drawer during the shift (TODO 4.38); null on a shift closed before this existed = 0.
            new String[]{"shift_closes", "cash_out", "INTEGER"},
            // The voucher used on this order and what it took off, in dong (TODO 4.11, rule D6). The value is also inside "discount";
            // a refund gives the voucher its use back. Null = no voucher.
            new String[]{"orders", "voucher_code", "VARCHAR(40)"},
            new String[]{"orders", "queue_no", "INTEGER"}, // TODO 4.25 (D3): the take-away number of the day
            new String[]{"orders", "voucher_value", "BIGINT"},
            // Goes up by one whenever the lines, the discount or the customer of an open order change, so a till that still shows an
            // older state is refused instead of deleting what another till added (TODO 2.91). 0 = never changed since this existed.
            new String[]{"orders", "rev", "INTEGER NOT NULL DEFAULT 0"},
            // The price of one unit of an ingredient (a gram, a millilitre, a piece), in whole dong; null = not priced yet. Set in the
            // ingredient box and by a delivery that says what it cost; a recipe's cost is the sum of amount * this (TODO 4.51).
            new String[]{"ingredients", "unit_cost", "BIGINT"},
            // Who sold the gift card / made the booking, by staff record id next to the name copied at the time, so that making a former
            // member of staff anonymous can find these rows by id (TODO 2.87). NULL on rows written before this column existed.
            new String[]{"gift_cards", "sold_by_id", "INTEGER"},
            new String[]{"reservations", "created_by_id", "INTEGER"},
            // The shop day (TODO 4.50) that take-away number belongs to, so the database can refuse a second order with the same
            // number on the same day (TODO 2.98). Null on a table order. Orders made before get their calendar day when the key is built.
            new String[]{"orders", "queue_day", "DATE"},
            // 1 = a voucher code that each customer may use only once (VoucherDAO). MariaDbSchema already had this column; the SQLite table
            // did not, so every voucher lookup on SQLite failed with "no such column". Old databases get it here.
            new String[]{"vouchers", "once_per_customer", "INTEGER NOT NULL DEFAULT 0"},
            // Where the order came from: DINE_IN, TAKE_AWAY, GRAB_FOOD, SHOPEE_FOOD; null on an order made before this (TODO 4.48),
            // which is read from its table: no table = take-away, else dine-in (OrderChannel.of / OrderChannel.SQL).
            new String[]{"orders", "channel", "VARCHAR(20)"},
            // How many times the receipt of this order was printed (TODO 3.72); the second and later prints carry a "copy" mark. 0 = never printed.
            new String[]{"orders", "print_count", "INT NOT NULL DEFAULT 0"},
            // The customer a table booking is for, when its phone matched one at booking time (TODO 2.88); null = a guest nobody knows.
            // Making a customer anonymous blanks the name and phone of their bookings through this (and, for older rows, through the phone).
            new String[]{"reservations", "customer_id", "INTEGER"},
            // The points this customer has earned over time, net of what refunds took back: spending points does not lower it, and the
            // membership tier is read from it (TODO 3.80). Filled for the customers that already exist when the column is added, see
            // DatabaseConfig.addLaterColumn.
            new String[]{"customers", "lifetime_points", "INTEGER NOT NULL DEFAULT 0"},
            // What the automatic promotions took off this order, in dong (TODO 4.55). Also inside "discount" (the total still equals
            // subtotal - discount), like the voucher's and the points' value. Null = no promotion. On a line: the promotion's name and
            // what it took off that line, printed on the receipt.
            new String[]{"orders", "promo_value", "BIGINT"},
            new String[]{"order_items", "promo_name", "VARCHAR(100)"},
            new String[]{"order_items", "promo_value", "BIGINT"},
            // A bill closed with no money (TODO 4.61, rule D26): payment_method is COMPLIMENTARY, the lines are saved at 0 and these four say what
            // it was. kind: STAFF, OWNER or TASTING; for: the staff record that drank it (STAFF only); value: what the drinks would have cost at
            // the menu price, in dong (the lines themselves are at 0, so this is the only place the price is kept); note: why, as typed.
            // All null on an ordinary order.
            new String[]{"orders", "complimentary_kind", "VARCHAR(20)"},
            new String[]{"orders", "complimentary_for", "INTEGER"},
            new String[]{"orders", "complimentary_value", "BIGINT"},
            new String[]{"orders", "complimentary_note", "VARCHAR(200)"});

    private SqliteSchema() {
    }
}
