package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.10: position, address, salary and hire date are saved with the staff record and read back. */
public class EmployeeDetailsTest {

    private final EmployeeDAO dao = new EmployeeDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void detailsSurviveASaveAndAnUpdate() {
        Employee e = new Employee();
        e.setFullName("test-emp-" + UUID.randomUUID());
        e.setPosition("Pha chế");
        e.setAddress("12 Nguyễn Huệ, Quận 1");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        e.setActive(true);
        dao.insert(e);

        Employee loaded = dao.findById(e.getId()).orElseThrow();
        assertEquals("Pha chế", loaded.getPosition());
        assertEquals("12 Nguyễn Huệ, Quận 1", loaded.getAddress());
        assertEquals(0, new BigDecimal("8000000").compareTo(loaded.getSalary()));
        assertEquals(LocalDate.of(2026, 3, 1), loaded.getHireDate());

        loaded.setPosition("Quản lý ca");
        loaded.setSalary(new BigDecimal("9500000"));
        loaded.setAddress(null);
        loaded.setHireDate(null);
        dao.update(loaded);

        Employee again = dao.findById(e.getId()).orElseThrow();
        assertEquals("Quản lý ca", again.getPosition());
        assertEquals(0, new BigDecimal("9500000").compareTo(again.getSalary()));
        assertNull(again.getAddress());
        assertNull(again.getHireDate());
    }

    @Test
    public void aStaffRecordWithoutDetailsStillWorks() {
        Employee e = new Employee();
        e.setFullName("test-emp-" + UUID.randomUUID());
        e.setActive(true);
        dao.insert(e);
        Employee loaded = dao.findById(e.getId()).orElseThrow();
        assertNull(loaded.getSalary());
        assertNull(loaded.getHireDate());
        assertNull(loaded.getPosition());
    }
}
