package com.coffeeshop.coffeeshopmanagement.config;

import java.util.List;

/**
 * The same tables for MariaDB / MySQL (InnoDB, utf8mb4), created the first time the app connects.
 * Differences from SQLite, on purpose:
 * <ul>
 *   <li>real types: {@code INT} ids, {@code BIGINT} whole-dong money, {@code DATETIME} / {@code DATE} instead of text;</li>
 *   <li>{@code VARCHAR} with lengths where a column is indexed or unique (MySQL cannot index plain TEXT);</li>
 *   <li>text compares ignoring case and accents ({@code utf8mb4_unicode_ci}), except {@code users.username}, which stays
 *       exact ({@code utf8mb4_bin}) so an accent still matters in a login name. {@code Admin} and {@code admin} must not be two
 *       accounts though (the SQLite index {@code ux_users_username_nocase}), so {@code users.username_key}, a stored
 *       lower-case copy of the name with a unique key, makes the server refuse the pair (TODO 1.9). It exists only here;</li>
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
                anonymised_at DATETIME NULL,
                UNIQUE KEY ux_employees_phone (phone)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS customers (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                full_name VARCHAR(255) NOT NULL,
                phone VARCHAR(32),
                email VARCHAR(255),
                loyalty_points INT NOT NULL DEFAULT 0,
                lifetime_points INT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                anonymised_at DATETIME NULL,
                birthday DATE NULL,
                birthday_used_on DATE NULL,
                UNIQUE KEY ux_customers_phone (phone)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS users (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
                username_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin AS (LOWER(username)) STORED,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                employee_id INT,
                customer_id INT,
                created_at DATETIME NOT NULL,
                must_change_password TINYINT(1) NOT NULL DEFAULT 0,
                pin_hash VARCHAR(255) NULL,
                pin_failures INT NOT NULL DEFAULT 0,
                UNIQUE KEY ux_users_username_key (username_key),
                FOREIGN KEY (employee_id) REFERENCES employees(id),
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS categories (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                description TEXT,
                active TINYINT(1) NOT NULL DEFAULT 1,
                sort_order INT NOT NULL DEFAULT 0
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS products (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                category_id INT,
                price BIGINT NOT NULL,
                cost BIGINT,
                stock INT NOT NULL DEFAULT 0,
                reorder_level INT NULL,
                description TEXT,
                image_path VARCHAR(500),
                active TINYINT(1) NOT NULL DEFAULT 1,
                sort_order INT NOT NULL DEFAULT 0,
                code VARCHAR(64) NULL,
                UNIQUE KEY ux_products_code (code),
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
                points_earned INT,
                cash_received BIGINT,
                change_given BIGINT,
                transfer_ref VARCHAR(64),
                points_redeemed INT,
                points_value BIGINT,
                cancelled_by INT,
                cancelled_at DATETIME NULL,
                cancel_reason VARCHAR(200),
                voucher_code VARCHAR(40) NULL,
                voucher_value BIGINT NULL,
                promo_value BIGINT NULL,
                queue_no INT NULL,
                rev INT NOT NULL DEFAULT 0,
                queue_day DATE NULL,
                open_table_key INT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN table_number END) STORED,
                UNIQUE KEY ux_orders_one_open_per_table (open_table_key),
                UNIQUE KEY ux_orders_queue_day_no (queue_day, queue_no),
                channel VARCHAR(20) NULL,
                print_count INT NOT NULL DEFAULT 0,
                complimentary_kind VARCHAR(20) NULL,
                complimentary_for INT NULL,
                complimentary_value BIGINT NULL,
                complimentary_note VARCHAR(200) NULL,
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
                unit_cost BIGINT,
                note VARCHAR(200),
                size_name VARCHAR(40),
                size_percent INT,
                extras VARCHAR(400),
                option_cost BIGINT,
                promo_name VARCHAR(100) NULL,
                promo_value BIGINT NULL,
                FOREIGN KEY (order_id) REFERENCES orders(id),
                FOREIGN KEY (product_id) REFERENCES products(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS order_payments (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                order_id INT NOT NULL,
                method VARCHAR(20) NOT NULL,
                amount BIGINT NOT NULL,
                UNIQUE KEY ux_order_payments (order_id, method),
                FOREIGN KEY (order_id) REFERENCES orders(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS refunds (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                order_id INT NOT NULL,
                order_item_id INT NOT NULL,
                quantity INT NOT NULL,
                line_amount BIGINT NOT NULL,
                amount BIGINT NOT NULL,
                method VARCHAR(20) NOT NULL,
                restocked TINYINT(1) NOT NULL DEFAULT 0,
                reason VARCHAR(200) NULL,
                refunded_by INT NULL,
                refunded_by_name VARCHAR(255) NOT NULL,
                refunded_at DATETIME NOT NULL,
                INDEX idx_refunds_order (order_id),
                INDEX idx_refunds_at (refunded_at),
                FOREIGN KEY (order_id) REFERENCES orders(id),
                FOREIGN KEY (order_item_id) REFERENCES order_items(id)
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
            CREATE TABLE IF NOT EXISTS settings (
                name VARCHAR(100) NOT NULL PRIMARY KEY,
                value VARCHAR(500) NOT NULL
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS login_attempts (
                scope VARCHAR(20) NOT NULL,
                name_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                failures INT NOT NULL DEFAULT 0,
                last_failure BIGINT NOT NULL DEFAULT 0,
                blocked_until BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (scope, name_key)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS stock_log (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                logged_at DATETIME NOT NULL,
                kind VARCHAR(20) NOT NULL,
                product_id INT NULL,
                product_name VARCHAR(255) NOT NULL,
                quantity INT NOT NULL,
                stock_before INT NOT NULL,
                stock_after INT NOT NULL,
                unit_cost BIGINT NULL,
                supplier VARCHAR(100) NULL,
                note VARCHAR(200) NULL,
                employee_id INT NULL,
                employee_name VARCHAR(255) NULL,
                INDEX idx_stock_log_logged_at (logged_at),
                INDEX idx_stock_log_product (product_id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                spent_on DATE NOT NULL,
                amount BIGINT NOT NULL,
                category VARCHAR(20) NOT NULL,
                note VARCHAR(200) NULL,
                from_drawer TINYINT(1) NOT NULL DEFAULT 0,
                recorded_by INT NULL,
                recorded_by_name VARCHAR(255) NOT NULL,
                created_at DATETIME NOT NULL,
                INDEX idx_expenses_spent_on (spent_on),
                INDEX idx_expenses_created_at (created_at)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS ingredients (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                unit VARCHAR(20) NOT NULL,
                stock BIGINT NOT NULL DEFAULT 0,
                min_stock BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                unit_cost BIGINT NULL,
                UNIQUE KEY uq_ingredients_name (name)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS stock_takes (
                order_item_id INT NOT NULL,
                ingredient_id INT NULL,
                amount BIGINT NOT NULL,
                UNIQUE KEY ux_stock_takes (order_item_id, ingredient_id),
                FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS recipes (
                product_id INT NOT NULL,
                ingredient_id INT NOT NULL,
                amount BIGINT NOT NULL,
                PRIMARY KEY (product_id, ingredient_id),
                FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS product_options (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                product_id INT NOT NULL,
                kind VARCHAR(10) NOT NULL,
                name VARCHAR(40) NOT NULL,
                price_delta BIGINT NOT NULL DEFAULT 0,
                cost_delta BIGINT NOT NULL DEFAULT 0,
                recipe_percent INT NOT NULL DEFAULT 100,
                is_default TINYINT(1) NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 1,
                UNIQUE KEY uq_product_option (product_id, kind, name),
                FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS time_clock (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                user_name VARCHAR(255) NOT NULL,
                clock_in DATETIME NOT NULL,
                clock_out DATETIME NULL,
                open_user_key INT GENERATED ALWAYS AS (CASE WHEN clock_out IS NULL THEN user_id END) STORED,
                UNIQUE KEY ux_time_clock_one_open (open_user_key),
                INDEX idx_time_clock_user_out (user_id, clock_out)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS gift_cards (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                code VARCHAR(32) NOT NULL,
                initial_amount BIGINT NOT NULL,
                balance BIGINT NOT NULL,
                sold_by VARCHAR(255) NOT NULL,
                sold_by_id INT NULL,
                created_at DATETIME NOT NULL,
                UNIQUE KEY uq_gift_cards_code (code)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS gift_card_moves (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                card_id INT NOT NULL,
                order_id INT NULL,
                amount BIGINT NOT NULL,
                moved_at DATETIME NOT NULL,
                INDEX idx_gift_card_moves_order (order_id),
                FOREIGN KEY (card_id) REFERENCES gift_cards(id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS reservations (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                table_number INT NOT NULL,
                customer_name VARCHAR(255) NOT NULL,
                phone VARCHAR(20) NULL,
                reserved_for DATETIME NOT NULL,
                people INT NOT NULL,
                note VARCHAR(200) NULL,
                status VARCHAR(12) NOT NULL DEFAULT 'BOOKED',
                created_by VARCHAR(255) NOT NULL,
                created_by_id INT NULL,
                created_at DATETIME NOT NULL,
                closed_at DATETIME NULL,
                customer_id INT NULL,
                INDEX idx_reservations_when (reserved_for),
                INDEX idx_reservations_table (table_number, status)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS vouchers (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                code VARCHAR(40) NOT NULL,
                kind VARCHAR(10) NOT NULL,
                value BIGINT NOT NULL,
                min_subtotal BIGINT NOT NULL DEFAULT 0,
                valid_from DATE NULL,
                valid_to DATE NULL,
                max_uses INT NULL,
                used_count INT NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 1,
                created_at DATETIME NOT NULL,
                once_per_customer INT NOT NULL DEFAULT 0,
                UNIQUE KEY uq_vouchers_code (code)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS promotions (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                kind VARCHAR(12) NOT NULL,
                percent INT NOT NULL DEFAULT 0,
                fixed_price BIGINT NOT NULL DEFAULT 0,
                buy_qty INT NOT NULL DEFAULT 0,
                free_qty INT NOT NULL DEFAULT 0,
                days VARCHAR(7) NOT NULL DEFAULT '',
                time_from VARCHAR(5) NULL,
                time_to VARCHAR(5) NULL,
                valid_from DATE NULL,
                valid_to DATE NULL,
                active TINYINT(1) NOT NULL DEFAULT 1,
                created_at DATETIME NOT NULL
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS promotion_targets (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                promotion_id INT NOT NULL,
                product_id INT NULL,
                category_id INT NULL,
                quantity INT NOT NULL DEFAULT 1,
                FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE,
                FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                logged_at DATETIME NOT NULL,
                actor_user_id INT NULL,
                actor_employee_id INT NULL,
                actor_name VARCHAR(255) NOT NULL,
                action VARCHAR(40) NOT NULL,
                target_type VARCHAR(20) NULL,
                target_id INT NULL,
                target_label VARCHAR(255) NULL,
                old_value VARCHAR(500) NULL,
                new_value VARCHAR(500) NULL,
                detail VARCHAR(500) NULL,
                INDEX idx_audit_log_logged_at (logged_at),
                INDEX idx_audit_log_action (action),
                INDEX idx_audit_log_target (target_type, target_id)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS shift_closes (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                period_start DATETIME NOT NULL,
                period_end DATETIME NOT NULL,
                closed_by INT NULL,
                closed_by_name VARCHAR(255) NOT NULL,
                paid_orders INT NOT NULL,
                revenue BIGINT NOT NULL,
                cash_sales BIGINT NOT NULL,
                opening_cash BIGINT NOT NULL,
                expected_cash BIGINT NOT NULL,
                counted_cash BIGINT NOT NULL,
                difference BIGINT NOT NULL,
                note VARCHAR(200) NULL,
                cash_out BIGINT NULL,
                INDEX idx_shift_closes_period_end (period_end)
            """ + TAIL,
            """
            CREATE TABLE IF NOT EXISTS schema_version (
                id TINYINT NOT NULL PRIMARY KEY,
                version INT NOT NULL
            """ + TAIL
    );

    /** Tables to drop (children first) when a throw-away test database is reset. */
    public static final List<String> DROP_ORDER = List.of("dining_tables", "promotion_targets", "promotions", "gift_card_moves", "gift_cards", "time_clock", "product_options", "recipes", "ingredients", "refunds", "order_payments", "stock_takes", "order_items", "orders", "products",
            "categories", "users", "customers", "employees", "app_flags", "settings", "login_attempts", "stock_log", "expenses", "vouchers", "reservations", "shift_closes", "audit_log", "schema_version");

    private MariaDbSchema() {
    }
}
