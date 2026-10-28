package com.coffeeshop.coffeeshopmanagement.model;

/**
 * One line of the staff screen (TODO 4.12): a staff record and, when there is one, the login account that belongs to it.
 * A person can be on the list without an account (a barista who never signs in); an account always has a staff record
 * except the first administrator made by the program, who is not on this list.
 *
 * @param employee the staff record (name, phone, position, salary ...)
 * @param userId   the account's id, or null when this person has no account
 * @param username the account's login name, or null
 * @param role     the account's role, or null
 * @param status   the account's status, or null
 */
public record StaffRow(Employee employee, Integer userId, String username, Role role, AccountStatus status) {

    public boolean hasAccount() {
        return userId != null;
    }

    /** What the person sees as "working": the account's status when there is an account, otherwise the staff record's own flag. */
    public boolean isWorking() {
        return hasAccount() ? status == AccountStatus.ACTIVE : employee.isActive();
    }
}
