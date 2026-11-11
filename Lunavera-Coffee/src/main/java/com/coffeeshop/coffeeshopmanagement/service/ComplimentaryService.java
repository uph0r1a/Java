package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ComplimentaryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;

import java.util.ArrayList;
import java.util.List;

/**
 * What the free-drink box asks for (TODO 6.21): the people a drink can be given to, and how many cups a person already had today.
 * The limits are {@link ComplimentaryRules}; the payment checks the count again inside its own transaction
 * ({@link ComplimentaryDAO}), so the number here only spares the cashier a surprise. {@code ComplimentaryDialog} holds no DAO.
 */
public class ComplimentaryService {

    private final EmployeeDAO employees;
    private final ComplimentaryDAO complimentary;

    public ComplimentaryService() {
        this(new EmployeeDAO(), new ComplimentaryDAO());
    }

    ComplimentaryService(EmployeeDAO employees, ComplimentaryDAO complimentary) {
        this.employees = employees;
        this.complimentary = complimentary;
    }

    /** Staff a free drink can be put against: still working here and not anonymised, in the order the staff list has them. */
    public List<Employee> staffToChoose() {
        return choosable(employees.findAll());
    }

    /** The part of {@link #staffToChoose} that needs no database. */
    static List<Employee> choosable(List<Employee> all) {
        List<Employee> result = new ArrayList<>();
        for (Employee e : all) {
            if (e.isActive() && !e.isAnonymised()) result.add(e);
        }
        return result;
    }

    /** Cups already given today to this person (or to the owner when {@code employeeId} is null). */
    public int cupsGivenToday(ComplimentaryRules.Kind kind, Integer employeeId) {
        return complimentary.cupsGivenToday(kind, employeeId);
    }
}
