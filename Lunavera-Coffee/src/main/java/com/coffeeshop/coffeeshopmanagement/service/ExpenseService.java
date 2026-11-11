package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ExpenseDAO;
import com.coffeeshop.coffeeshopmanagement.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the expense screens ask for (TODO 6.21): the lines of a range of days and writing one down. The rules themselves stay where they
 * were checked before: {@link ExpenseRules} says what is valid and {@link ExpenseDAO} refuses a person who may not do it (only an
 * administrator reads, writes a dated expense and deletes; anybody signed in may write cash taken out of the drawer, dated today) and
 * writes the audit line in the same transaction. The screens ({@code ExpenseDialog}) hold no DAO of their own.
 */
public class ExpenseService {

    /** The newest lines shown by default. */
    public static final int DEFAULT_LIMIT = ExpenseDAO.DEFAULT_LIMIT;

    private final ExpenseDAO expenses;

    public ExpenseService() {
        this(new ExpenseDAO());
    }

    ExpenseService(ExpenseDAO expenses) {
        this.expenses = expenses;
    }

    /** The expenses of the days {@code from} to {@code to}, newest first (administrators only). */
    public List<Expense> find(LocalDate from, LocalDate to, int limit) {
        return expenses.find(from, to, limit);
    }

    /** Deletes a line written by mistake (administrators only); the audit line keeps its day, amount, category and note. */
    public boolean delete(int id) {
        return expenses.delete(id);
    }

    /** Writes an expense down; see {@link ExpenseDAO#add}. */
    public Expense add(LocalDate spentOn, BigDecimal amount, String category, String note, boolean fromDrawer) {
        return expenses.add(spentOn, amount, category, note, fromDrawer);
    }
}
