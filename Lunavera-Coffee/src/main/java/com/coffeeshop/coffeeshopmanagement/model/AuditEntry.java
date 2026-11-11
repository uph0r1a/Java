package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

/**
 * One line of the audit log (TODO 4.2). The actor and target names are copies made when the line was written, like in the stock
 * log, so the line reads the same after a person or a product is gone. {@code action} is the stored name; see {@link AuditAction}.
 *
 * @param actorUserId the account that did it; null for something nobody was signed in for (start-up, the first-run setup)
 * @param targetType what was changed (an order, a product, an account...), see {@code AuditTarget}
 * @param oldValue what it was before (a number as plain digits for money and counts), or null
 * @param newValue what it is now, or null
 * @param detail the reason typed by the person, or a short code the screen turns into words
 */
public record AuditEntry(int id, LocalDateTime loggedAt, Integer actorUserId, Integer actorEmployeeId, String actorName,
                         String action, String targetType, Integer targetId, String targetLabel, String oldValue,
                         String newValue, String detail) {
}
