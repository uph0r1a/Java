package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.17: one staff member per phone is a database rule on SQLite and on MariaDB, not only a check in the app. */
public class StaffPhoneUniqueTest {

    private final EmployeeDAO dao = new EmployeeDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static String randomPhone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    }

    private Employee staff(String phone) {
        Employee e = new Employee();
        e.setFullName("test-staff-" + UUID.randomUUID());
        e.setPhone(phone);
        e.setActive(true);
        return e;
    }

    @Test
    public void theDatabaseRefusesASecondStaffMemberWithTheSamePhone() {
        String phone = randomPhone();
        dao.insert(staff(phone)); // goes straight to the DAO, past the app's own check
        DataAccessException e = assertThrows(DataAccessException.class, () -> dao.insert(staff(phone)));
        assertTrue(e.getMessage(), e.getMessage().contains("nhân viên khác"));
    }

    @Test
    public void updatingToAPhoneThatIsTakenIsRefused() {
        String phone = randomPhone();
        dao.insert(staff(phone));
        Employee other = dao.insert(staff(randomPhone()));
        other.setPhone(phone);
        assertThrows(DataAccessException.class, () -> dao.update(other));
    }

    @Test
    public void staffWithoutAPhoneMayRepeat() {
        dao.insert(staff(null));
        dao.insert(staff(""));
        dao.insert(staff("  "));
        // no exception: blank phones are stored as NULL, which the unique key allows to repeat
    }
}
