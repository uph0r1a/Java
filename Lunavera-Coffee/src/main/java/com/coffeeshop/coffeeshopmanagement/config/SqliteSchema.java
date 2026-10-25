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
            """
    );

    /** {table, column, SQLite type} added after the first release; created by {@code addColumnIfMissing}. */
    static final List<String[]> LATER_COLUMNS = List.of(
            new String[]{"orders", "table_number", "INTEGER"},
            new String[]{"orders", "discount_reason", "TEXT"},
            new String[]{"orders", "paid_by", "INTEGER"}, // who took the money (TODO 2.47)
            new String[]{"order_items", "unit_cost", "INTEGER"}, // the product's cost when the line was paid; null = not known then (TODO 3.53)
            new String[]{"products", "reorder_level", "INTEGER"}, // the product's own low-stock level; null = the shop default (TODO 4.19)
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
            new String[]{"orders", "transfer_ref", "VARCHAR(64)"});

    private SqliteSchema() {
    }
}
