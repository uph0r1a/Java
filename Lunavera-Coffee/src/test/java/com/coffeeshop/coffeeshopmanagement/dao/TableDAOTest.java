package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Table status is computed: a table is occupied when it has an OPEN order OR its stored status
 * is OCCUPIED (manual toggle). setEmpty() must also cancel any OPEN order on that table.
 * Each test uses its own table number (the database is shared across DAO tests).
 */
public class TableDAOTest {

    private final TableDAO tableDAO = new TableDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    /** Changing the table list is for the administrator (TODO 4.18); the tests that do it sign in as one. */
    @Before
    public void administrator() {
        TestSession.admin();
    }

    @After
    public void signOutAndBackToVietnamese() {
        TestSession.clear();
        I18n.setLanguage(I18n.VI);
    }

    /** The UI only ever lists tables, so the test looks one up the same way. */
    private DiningTable table(int number) {
        return tableDAO.findAll().stream().filter(t -> t.getTableNumber() == number).findFirst().orElse(null);
    }

    private Order openOrderOn(int tableNumber) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(tableNumber);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    @Test
    public void seedsTwelveTablesAndTheyStartEmpty() {
        List<DiningTable> tables = tableDAO.findAll();
        assertTrue(tables.size() >= 12);
        DiningTable first = table(12);
        assertNotNull(first);
        assertEquals(TableStatus.EMPTY, table(12).getStatus());
    }

    @Test
    public void manualOccupiedThenEmpty() {
        tableDAO.setOccupied(1, null);
        assertTrue(table(1).isOccupied());
        tableDAO.setEmpty(1);
        assertFalse(table(1).isOccupied());
    }

    @Test
    public void openOrderMakesTableOccupiedAndSetEmptyCancelsIt() {
        Order saved = openOrderOn(2);
        assertTrue(saved.getId() > 0);
        assertTrue(table(2).isOccupied());
        assertEquals(Integer.valueOf(saved.getId()), table(2).getCurrentOrderId());
        assertTrue(orderDAO.findOpenOrderByTable(2).isPresent());

        tableDAO.setEmpty(2);

        assertFalse(table(2).isOccupied());
        assertFalse(orderDAO.findOpenOrderByTable(2).isPresent());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
    }

    @Test
    public void unknownTableReturnsNull() {
        assertNull(table(9999));
    }

    @Test
    public void setEmptyFreesTheTableAndCancelsItsOpenOrderTogether() {
        Order open = openOrderOn(3);
        assertTrue(table(3).isOccupied());

        tableDAO.setEmpty(3);

        assertFalse(table(3).isOccupied());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(open.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- TODO 2.53: one source of truth

    private static com.coffeeshop.coffeeshopmanagement.model.OrderItem line() {
        com.coffeeshop.coffeeshopmanagement.model.OrderItem item = new com.coffeeshop.coffeeshopmanagement.model.OrderItem();
        item.setProductName("Test");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(20_000));
        item.setLineTotal(BigDecimal.valueOf(20_000));
        return item;
    }

    @Test
    public void anOrderAloneMakesTheTableOccupiedAndLeavesNoHandMadeMark() {
        openOrderOn(10);
        assertTrue(table(10).isOccupied());
        assertFalse("the order is the reason, not a stored mark", tableDAO.holdsWithoutOrder().contains(10));
        tableDAO.setEmpty(10);
    }

    @Test
    public void aHandMadeMarkWithoutAnOrderIsListedSoItCanBeFlagged() {
        tableDAO.setOccupied(11, null);
        assertTrue(tableDAO.holdsWithoutOrder().contains(11));
        String text = com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.heldTablesWarning(List.of(11));
        assertTrue(text, text.contains("bàn 11"));
        assertNull(com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.heldTablesWarning(List.of()));
        tableDAO.setEmpty(11);
        assertFalse(tableDAO.holdsWithoutOrder().contains(11));
    }

    @Test
    public void payingAnOrderOnAMarkedTableClearsTheMarkInTheSameTransaction() {
        tableDAO.setOccupied(12, null);
        Order open = openOrderOn(12);
        orderDAO.payExistingOrder(open.getId(), null, com.coffeeshop.coffeeshopmanagement.model.PaymentMethod.CASH,
                BigDecimal.ZERO, BigDecimal.valueOf(20_000), List.of(line()));
        assertFalse(table(12).isOccupied());
        assertFalse(tableDAO.holdsWithoutOrder().contains(12));
    }

    @Test
    public void aSaleSavedStraightAsPaidClearsAMarkOnItsTable() {
        tableDAO.setOccupied(9, null);
        Order paid = new Order();
        paid.setOrderDate(AppClock.now());
        paid.setPaidAt(AppClock.now());
        paid.setTableNumber(9);
        paid.setStatus(OrderStatus.PAID);
        paid.setSubtotal(BigDecimal.valueOf(20_000));
        paid.setDiscount(BigDecimal.ZERO);
        paid.setTotal(BigDecimal.valueOf(20_000));
        orderDAO.insert(paid, List.of(line()));
        assertFalse(table(9).isOccupied());
    }

    /** TODO 5.11: the map can say how long an occupied table has been open. */
    @Test
    public void anOccupiedTableKnowsWhenItsOrderWasOpened() {
        LocalDateTime opened = AppClock.now().minusMinutes(45).withNano(0);
        Order order = new Order();
        order.setOrderDate(opened);
        order.setTableNumber(7);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        orderDAO.saveOrUpdateOpenOrder(order, List.of());
        assertEquals(opened, table(7).getOpenedAt());
        tableDAO.setEmpty(7);
        assertNull("a freed table has no age", table(7).getOpenedAt());
    }

    // ---- TODO 4.18: table management

    @Test
    public void anAddedTableGetsTheNextNumberAndCanBeRenamedAndRemoved() {
        int number = tableDAO.add("Garden test", 6);
        DiningTable added = table(number);
        assertNotNull(added);
        assertEquals("Garden test", added.getName());
        assertEquals(6, added.getCapacity());
        assertFalse(added.isOccupied());

        tableDAO.update(number, "Garden renamed", 8);
        assertEquals("Garden renamed", table(number).getName());
        assertEquals(8, table(number).getCapacity());

        assertTrue(tableDAO.remove(number));
        assertNull(table(number));
        assertTrue("removing a table that is already gone is not an error", tableDAO.remove(number));
    }

    @Test
    public void aBlankNameBecomesTheStarterName() {
        int number = tableDAO.add("  ", 2);
        try {
            assertEquals("B\u00e0n " + number, table(number).getName());
        } finally {
            tableDAO.remove(number);
        }
    }

    @Test
    public void aTableWithAnOpenOrderIsKept() {
        int number = tableDAO.add("Busy test", 4);
        try {
            openOrderOn(number);
            assertFalse(tableDAO.remove(number));
            assertNotNull(table(number));
        } finally {
            tableDAO.setEmpty(number); // cancels the open order
            tableDAO.remove(number);
        }
    }

    @Test
    public void aRemovedTablesNumberIsNeverGivenAgainWhileOrdersStillUseIt() {
        int first = tableDAO.add("Reuse test", 4);
        openOrderOn(first);
        tableDAO.setEmpty(first); // the order is cancelled but keeps the table number
        assertTrue(tableDAO.remove(first));
        int second = tableDAO.add("Reuse test 2", 4);
        try {
            assertTrue("number " + second + " must be above the removed " + first, second > first);
        } finally {
            tableDAO.remove(second);
        }
    }

    // ---- the fixes of the 4.18 check ----

    /** Puts back the rows the last-table test removed (same numbers, names and seats), so the shared test database is as before. */
    private static void restoreTables(java.util.List<DiningTable> before) {
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection()) {
            for (DiningTable t : before) {
                try (java.sql.PreparedStatement exists = c.prepareStatement("SELECT 1 FROM dining_tables WHERE table_number = ?")) {
                    exists.setInt(1, t.getTableNumber());
                    try (java.sql.ResultSet rs = exists.executeQuery()) {
                        if (rs.next()) {
                            continue;
                        }
                    }
                }
                try (java.sql.PreparedStatement put = c.prepareStatement(
                        "INSERT INTO dining_tables (table_number, name, status, capacity) VALUES (?, ?, "
                                + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.EMPTY + ", ?)")) {
                    put.setInt(1, t.getTableNumber());
                    put.setString(2, t.getName());
                    put.setInt(3, t.getCapacity());
                    put.executeUpdate();
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void refused(Runnable action) {
        try {
            action.run();
            fail("must be refused");
        } catch (IllegalArgumentException | com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException expected) {
            // refused, as it should be
        }
    }

    @Test
    public void onlyTheAdministratorChangesTheTableList() {
        int number = tableDAO.add("Admin " + System.nanoTime(), 2);
        try {
            TestSession.clear(); // nobody signed in
            refused(() -> tableDAO.add("Lén " + System.nanoTime(), 2));
            refused(() -> tableDAO.update(number, "Đổi", 3));
            refused(() -> tableDAO.remove(number));
            com.coffeeshop.coffeeshopmanagement.model.User cashier = new com.coffeeshop.coffeeshopmanagement.model.User();
            cashier.setId(2);
            cashier.setUsername("test-cashier");
            cashier.setRole(com.coffeeshop.coffeeshopmanagement.model.Role.EMPLOYEE);
            com.coffeeshop.coffeeshopmanagement.util.Session.start(cashier, null); // a cashier
            refused(() -> tableDAO.add("Lén " + System.nanoTime(), 2));
            refused(() -> tableDAO.update(number, "Đổi", 3));
            refused(() -> tableDAO.remove(number));
            assertNotNull(table(number));
        } finally {
            TestSession.admin();
            tableDAO.remove(number);
        }
    }

    @Test
    public void theTableLimitIsCheckedByTheDatabaseCodeToo() {
        int room = com.coffeeshop.coffeeshopmanagement.service.TableRules.MAX_TABLES - tableDAO.findAll().size();
        java.util.List<Integer> added = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < room; i++) {
                added.add(tableDAO.add("Nhiều " + i + " " + System.nanoTime(), 2));
            }
            assertEquals(com.coffeeshop.coffeeshopmanagement.service.TableRules.MAX_TABLES, tableDAO.findAll().size());
            refused(() -> tableDAO.add("Một bàn quá nhiều " + System.nanoTime(), 2));
        } finally {
            added.forEach(tableDAO::remove);
        }
    }

    @Test
    public void aNameIsTakenWhateverTheLanguageOrTheAccentEncoding() {
        // table 5 is the starter table "Bàn 5": it is "Table 5" in English
        refused(() -> tableDAO.add("Table 5", 2));
        refused(() -> tableDAO.add("bàn 5", 2));
        refused(() -> tableDAO.add("Ba\u0300n   5", 2)); // 'a' + combining grave: the same letters, written the other way
        refused(() -> tableDAO.update(6, "Bàn 5", 4));
        refused(() -> tableDAO.update(6, "TABLE 5", 4));
        int custom = tableDAO.add("Sân vườn " + System.nanoTime(), 2);
        try {
            String name = table(custom).getName();
            refused(() -> tableDAO.add(name.toUpperCase(), 2));
            String decomposed = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
            refused(() -> tableDAO.add(decomposed, 2));
            tableDAO.update(custom, name, 5); // its own name is fine
        } finally {
            tableDAO.remove(custom);
        }
    }

    @Test
    public void seatsOutsideOneToNinetyNineAreRefusedByTheDatabaseCodeToo() {
        refused(() -> tableDAO.add("Ghế " + System.nanoTime(), 0));
        refused(() -> tableDAO.add("Ghế " + System.nanoTime(), 100));
        refused(() -> tableDAO.update(1, table(1).getName(), 0));
    }

    @Test
    public void theLastTableCannotBeRemovedAndTheRestCanBeDownToOne() {
        java.util.List<DiningTable> before = tableDAO.findAll();
        java.util.List<Integer> removed = new java.util.ArrayList<>();
        try {
            // free every table except one: only removing the LAST one must be refused
            // (a copy of the real list is kept; every removed row is put back below)
            for (DiningTable t : before) {
                if (tableDAO.findAll().size() == 1) {
                    break;
                }
                assertTrue(tableDAO.remove(t.getTableNumber()));
                removed.add(t.getTableNumber());
            }
            assertEquals(1, tableDAO.findAll().size());
            int lastNumber = tableDAO.findAll().get(0).getTableNumber();
            refused(() -> tableDAO.remove(lastNumber));
            assertEquals(1, tableDAO.findAll().size());
        } finally {
            restoreTables(before);
        }
    }

    @Test
    public void changingOnlyTheSeatsKeepsTheStarterNameInBothLanguages() {
        I18n.setLanguage(I18n.EN);
        DiningTable five = table(5);
        assertEquals("Table 5", five.displayName());
        // what the edit box does: it holds the name SHOWN, and OK saves what TableRules says to store
        String stored = com.coffeeshop.coffeeshopmanagement.service.TableRules.nameToStore(five.displayName(), five.getName(), five.displayName());
        tableDAO.update(5, stored, 6);
        I18n.setLanguage(I18n.VI);
        assertEquals("Bàn 5", table(5).displayName());
        assertEquals(6, table(5).getCapacity());
        tableDAO.update(5, "Bàn 5", 4);
    }

    @Test
    public void aSecondTillTakingTheSameNumberIsRetriedNotShownAsATechnicalError() {
        int[] interfering = {0};
        // between "choose the number" and "insert", another till adds a table: the same number is now taken
        int number = tableDAO.add("Hai máy " + System.nanoTime(), 2, () -> {
            if (interfering[0]++ == 0) {
                tableDAO.add("Máy kia " + System.nanoTime(), 2);
            }
        });
        try {
            assertTrue(number > 0);
            assertNotNull(table(number));
            assertEquals("the other till's table is there too", 2, tableDAO.findAll().stream()
                    .filter(t -> t.getName().startsWith("Hai máy") || t.getName().startsWith("Máy kia")).count());
        } finally {
            tableDAO.findAll().stream().filter(t -> t.getName().startsWith("Hai máy") || t.getName().startsWith("Máy kia"))
                    .forEach(t -> tableDAO.remove(t.getTableNumber()));
        }
    }

    @Test
    public void aTableThatIsStillClashingAfterThreeTriesGivesAPlainMessage() {
        int[] calls = {0};
        try {
            tableDAO.add("Luôn trùng " + System.nanoTime(), 2, () -> {
                calls[0]++;
                tableDAO.add("Chen vào " + System.nanoTime() + "-" + calls[0], 2);
            });
            fail("three clashes in a row must end in a message");
        } catch (com.coffeeshop.coffeeshopmanagement.dao.DataAccessException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(I18n.t("tablemgr.fail.busy"))); // the project adds its own "database is busy" hint in front
            assertFalse("no raw driver text", e.getMessage().toLowerCase().contains("sqlite"));
        } finally {
            tableDAO.findAll().stream().filter(t -> t.getName().startsWith("Chen vào"))
                    .forEach(t -> tableDAO.remove(t.getTableNumber()));
        }
    }

    @Test
    public void anOpenOrderCannotBeStartedOnATableThatWasRemoved() {
        int number = tableDAO.add("Sắp xóa " + System.nanoTime(), 2);
        assertTrue(tableDAO.remove(number));
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(number);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        try {
            orderDAO.insert(order, List.of());
            fail("an order on a table that does not exist must be refused");
        } catch (com.coffeeshop.coffeeshopmanagement.dao.DataAccessException e) {
            assertEquals(I18n.t("order.table.gone", number), e.getMessage());
        }
    }
}
