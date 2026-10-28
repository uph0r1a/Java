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
                CREATE TABLE IF NOT EXISTS customer_merges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kept_id INTEGER NOT NULL,
                    removed_id INTEGER NOT NULL,
                    removed_name TEXT NOT NULL,
                    orders_moved INTEGER NOT NULL DEFAULT 0,
                    points_moved INTEGER NOT NULL DEFAULT 0,
                    merged_by INTEGER,
                    merged_at TEXT NOT NULL
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

    /** {table, column, SQLite type} added after the first release; created by {@code addColumnIfMissing}. */
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
            // When a leaver's personal details were removed; null = not made anonymous (TODO 4.29).
            new String[]{"employees", "anonymised_at", "DATETIME"},
            // When a customer's personal details were removed on request; null = not made anonymous (TODO 3.4 / 4.8, rule D21).
            new String[]{"customers", "anonymised_at", "DATETIME"},
            // Cash taken out of the drawer during the shift (TODO 4.38); null on a shift closed before this existed = 0.
            new String[]{"shift_closes", "cash_out", "INTEGER"},
            // When a paid order was refunded; null = not refunded, or refunded before this column existed (TODO 3.13). The refund counts on this day.
            new String[]{"orders", "cancelled_at", "DATETIME"});

    private SqliteSchema() {
    }
}
