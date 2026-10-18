package com.coffeeshop.coffeeshopmanagement.config;

import java.util.List;

/**
 * The same tables for MariaDB / MySQL (InnoDB, utf8mb4), created the first time the app connects.
 * Differences from SQLite, on purpose:
 * <ul>
 *   <li>real types: {@code INT} ids, {@code BIGINT} whole-dong money, {@code DATETIME} / {@code DATE} instead of text;</li>
 *   <li>{@code VARCHAR} with lengths where a column is indexed or unique (MySQL cannot index plain TEXT);</li>
 *   <li>text compares ignoring case and accents ({@code utf8mb4_unicode_ci}), except {@code users.username}, which stays
 *       exact ({@code utf8mb4_bin}) like SQLite did, so {@code admin} and {@code ADMIN} are still different logins;</li>
 *   <li>a {@code schema_version} table instead of {@code PRAGMA user_version}.</li>
 * </ul>
 * Keep the columns in step with {@link SqliteSchema} (a test compares the two).
 */
public final class MariaDbSchema {

    private static final String TAIL = ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    /** Created in this order: a table after the ones its foreign keys point to. */
    public static final List<String> TABLES = List.of(
            """
            CREATE TABLE IF NOT EXISTS employees (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                full_name VARCHAR(255) NOT NULL,
                phone VARCHAR(32),
                email VARCHAR(255),
                address VARCHAR(500),
                position VARCHAR(100),
                salary BIGINT,
                hire_date DATE,
                active TINYINT(1) NOT NULL DEFAULT 1,
                UNIQUE KEY ux_employees_phone (phone)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS customers (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                full_name VARCHAR(255) NOT NULL,
                phone VARCHAR(32),
                email VARCHAR(255),
                loyalty_points INT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                UNIQUE KEY ux_customers_phone (phone)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS users (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                employee_id INT,
                customer_id INT,
                created_at DATETIME NOT NULL,
                must_change_password TINYINT(1) NOT NULL DEFAULT 0,
                FOREIGN KEY (employee_id) REFERENCES employees(id),
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS categories (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                description TEXT,
                active TINYINT(1) NOT NULL DEFAULT 1
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS products (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                category_id INT,
                price BIGINT NOT NULL,
                cost BIGINT,
                stock INT NOT NULL DEFAULT 0,
                description TEXT,
                image_path VARCHAR(500),
                active TINYINT(1) NOT NULL DEFAULT 1,
                FOREIGN KEY (category_id) REFERENCES categories(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS orders (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                order_date DATETIME NOT NULL,
                employee_id INT,
                customer_id INT,
                status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                subtotal BIGINT NOT NULL DEFAULT 0,
                discount BIGINT NOT NULL DEFAULT 0,
                total BIGINT NOT NULL DEFAULT 0,
                payment_method VARCHAR(20),
                paid_at DATETIME NULL,
                table_number INT,
                discount_reason VARCHAR(255),
                paid_by INT,
                INDEX idx_orders_status_date (status, order_date),
                INDEX idx_orders_paid_at (paid_at),
                INDEX idx_orders_table (table_number),
                FOREIGN KEY (employee_id) REFERENCES employees(id),
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS order_items (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                order_id INT NOT NULL,
                product_id INT,
                product_name VARCHAR(255) NOT NULL,
                quantity INT NOT NULL,
                unit_price BIGINT NOT NULL,
                line_total BIGINT NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders(id),
                FOREIGN KEY (product_id) REFERENCES products(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS dining_tables (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                table_number INT NOT NULL UNIQUE,
                name VARCHAR(100) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'EMPTY',
                capacity INT DEFAULT 4,
                current_order_id INT,
                FOREIGN KEY (current_order_id) REFERENCES orders(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS app_flags (
                name VARCHAR(100) NOT NULL PRIMARY KEY
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS schema_version (
                id TINYINT NOT NULL PRIMARY KEY,
                version INT NOT NULL
            """ + TAIL
    );

    /** Tables to drop (children first) when a throw-away test database is reset. */
    public static final List<String> DROP_ORDER = List.of("dining_tables", "order_items", "orders", "products",
            "categories", "users", "customers", "employees", "app_flags", "schema_version");

    private MariaDbSchema() {
    }
}
