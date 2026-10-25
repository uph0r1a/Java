package com.coffeeshop.coffeeshopmanagement.model;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/** TODO 3.45: on a Turkish or Azerbaijani PC the default locale turns "i" into a dotted capital; the parsers must not care. */
public class LocaleIndependenceTest {

    private Locale saved;

    @Before
    public void useTurkish() {
        saved = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @After
    public void restore() {
        Locale.setDefault(saved);
    }

    @Test
    public void statusParsersWorkWithTurkishDefaultLocale() {
        assertEquals(OrderStatus.PAID, OrderStatus.fromDb("paid"));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromDb("cancelled"));
        assertEquals(Role.ADMIN, Role.fromDb("admin"));
        assertEquals(Role.CUSTOMER, Role.fromDb("customer"));
        assertEquals(PaymentMethod.CARD, PaymentMethod.fromDb("card"));
        assertEquals(PaymentMethod.TRANSFER, PaymentMethod.fromDb("transfer"));
        assertEquals(AccountStatus.LOCKED, AccountStatus.fromDb("locked"));
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromDb("active"));
        assertEquals(TableStatus.OCCUPIED, TableStatus.fromDb("occupied"));
    }
}
