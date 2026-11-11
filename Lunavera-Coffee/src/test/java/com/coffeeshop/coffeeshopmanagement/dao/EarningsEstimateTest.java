package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.EarningsRules;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.60: the salary of each account comes from the staff table, and with the clocked hours it makes the estimate. */
public class EarningsEstimateTest {

    private final TimeClockDAO dao = new TimeClockDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void admin() {
        TestSession.admin();
        ShopSettings.resetForTests();
    }

    @After
    public void reset() {
        TestSession.admin();
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", ShopRules.DEFAULTS);
        ShopSettings.resetForTests();
    }

    /** A staff account whose staff record has this salary (null: none). */
    private int account(BigDecimal salary, boolean withStaffRecord) {
        Integer employeeId = null;
        if (withStaffRecord) {
            Employee e = new Employee();
            e.setFullName("Earner " + UUID.randomUUID().toString().substring(0, 6));
            e.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new java.util.Random().nextInt(100_000_000)));
            e.setSalary(salary);
            employeeId = new StaffService().save(e).getId();
        }
        User user = new User();
        user.setUsername("earner-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("x");
        user.setRole(Role.EMPLOYEE);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmployeeId(employeeId);
        return new UserDAO().insert(user).getId();
    }

    @Test
    public void theSalaryOfEachAccountComesFromItsStaffRecord() {
        int paid = account(new BigDecimal("5200000"), true);
        int unpaid = account(null, true);
        int noRecord = account(null, false);
        Map<Integer, BigDecimal> salaries = dao.salaries();
        assertEquals(0, new BigDecimal("5200000").compareTo(salaries.get(paid)));
        assertFalse("a staff record without a salary is left out", salaries.containsKey(unpaid));
        assertFalse("an account without a staff record is left out", salaries.containsKey(noRecord));
    }

    @Test
    public void onlyAnAdministratorMayReadTheSalaries() {
        User cashier = new User();
        cashier.setId(7);
        cashier.setUsername("pay-cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        try {
            dao.salaries();
            fail("a cashier may not read the salaries");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
    }

    @Test
    public void eightHoursOfAMonthlyPaidPersonComeToAnEighthOfADaysShare() throws Exception {
        int userId = account(new BigDecimal("5200000"), true); // 25.000 an hour at 208 hours
        LocalDateTime in = LocalDateTime.of(2026, 4, 6, 8, 0);
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (?, ?, ?, ?)")) {
            st.setInt(1, userId);
            st.setString(2, "Earner");
            st.setString(3, DbTime.write(in));
            st.setString(4, DbTime.write(in.plusHours(8)));
            st.executeUpdate();
        }
        TimeClockDAO.Hours hours = dao.hours(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)).stream().filter(h -> h.userId() == userId).findFirst().orElseThrow();
        assertEquals(8 * 60, hours.minutes());
        BigDecimal rate = EarningsRules.hourlyRate(dao.salaries().get(userId), ShopRules.current());
        assertEquals(0, new BigDecimal("25000").compareTo(rate));
        assertEquals(0, new BigDecimal("200000").compareTo(EarningsRules.estimate(hours.minutes(), rate)));

        // the owner says the figure is an hourly rate: the same hours now come to 8 times it
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", new ShopRules.Values(10, 10_000, 1_000, 50, 20, 100, 500, 10, 90, 15, 16, 1, 208));
        BigDecimal hourlyRate = EarningsRules.hourlyRate(dao.salaries().get(userId), ShopRules.current());
        assertEquals(0, new BigDecimal("5200000").compareTo(hourlyRate));
        assertEquals(0, new BigDecimal("41600000").compareTo(EarningsRules.estimate(hours.minutes(), hourlyRate)));
    }
}
