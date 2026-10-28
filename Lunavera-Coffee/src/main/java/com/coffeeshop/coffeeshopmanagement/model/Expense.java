package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One expense of the shop (TODO 4.38): rent, wages, electricity, supplies, a repair, or cash taken out of the drawer.
 *
 * @param spentOn        the day the money was spent (what the report's date range is about)
 * @param category       one of {@code ExpenseRules.CATEGORIES}
 * @param fromDrawer     true when the money was taken from the cash drawer: it is then taken off the cash the drawer should hold
 *                       when the shift is closed (TODO 4.6)
 * @param recordedByName the name of the person who wrote it down, kept as text so it reads the same after a rename
 * @param createdAt      when it was written down: what decides which shift a drawer cash-out belongs to
 */
public record Expense(int id, LocalDate spentOn, BigDecimal amount, String category, String note, boolean fromDrawer,
                      Integer recordedBy, String recordedByName, LocalDateTime createdAt) {
}
