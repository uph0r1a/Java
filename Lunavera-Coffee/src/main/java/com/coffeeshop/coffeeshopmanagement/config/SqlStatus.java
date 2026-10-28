package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;

/**
 * The status and role values as they are written inside SQL text, built from the enums (TODO 6.23). A hand-typed
 * {@code 'PAID'} that is misspelled compiles and quietly matches nothing; {@code SqlStatus.PAID} that is misspelled does not
 * compile, and renaming an enum constant is followed here instead of being forgotten in 40 queries.
 *
 * <p>Each constant is the quoted enum name, for example {@code 'PAID'}, so it is joined into the statement text:
 * <pre>"UPDATE orders SET status = " + SqlStatus.PAID + " WHERE id = ?"</pre>
 * The text sent to the database is exactly what it was before. The values come from enum names, never from user input, so
 * this adds no injection risk. The table-creation scripts keep their {@code DEFAULT '...'} as plain text (they are one block
 * of DDL); {@code SqlStatusTest} checks that those defaults still match the enums.
 */
public final class SqlStatus {

    public static final String OPEN = quote(OrderStatus.OPEN);
    public static final String PAID = quote(OrderStatus.PAID);
    public static final String CANCELLED = quote(OrderStatus.CANCELLED);

    public static final String ACTIVE = quote(AccountStatus.ACTIVE);
    public static final String LOCKED = quote(AccountStatus.LOCKED);

    public static final String EMPTY = quote(TableStatus.EMPTY);
    public static final String OCCUPIED = quote(TableStatus.OCCUPIED);

    public static final String ROLE_ADMIN = quote(Role.ADMIN);
    public static final String ROLE_CUSTOMER = quote(Role.CUSTOMER);

    private SqlStatus() {
    }

    private static String quote(Enum<?> value) {
        return "'" + value.name() + "'";
    }
}
