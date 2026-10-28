package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.util.List;

/**
 * The staff list as its own thing (TODO 4.12): every staff record with or without a login, so a barista who never signs in can
 * be kept, edited and marked as having left. Accounts are still made, locked and given roles on the account screen
 * ({@link AccountService}); this class never touches a password or a role.
 *
 * <p>Who may do what is checked here, not only on the screen (TODO 2A.3): every method needs an administrator, because the list
 * carries salaries. A person who has an account is "working" or "left" by that account's status, so changing it goes through
 * {@link AccountService#setLocked}: the same one transaction that keeps the staff flag and the account in step, and the same
 * "the last active administrator cannot be locked" rule.
 */
public class StaffService {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AccountService accountService = new AccountService();

    /** Everyone on the staff list with their account, oldest first. */
    public List<StaffRow> list() {
        Permissions.requireAdmin("view the staff list");
        return employeeDAO.findAllWithAccounts();
    }

    /**
     * Saves a typed staff record: a new person when {@code input.getId()} is 0, otherwise a change to that person's details
     * (name, phone, email, position, address, salary, start date). Whether the person is working is not changed here; see
     * {@link #setWorking}. A new person starts as working.
     *
     * @throws DataAccessException with a sentence fit to show, when a rule is broken or the phone belongs to someone else
     */
    public Employee save(Employee input) {
        Permissions.requireAdmin("change a staff record");
        String problem = StaffRules.problem(input, AppClock.today());
        if (problem != null) {
            throw new DataAccessException(problem);
        }
        String phone = StaffRules.clean(input.getPhone());
        if (phone != null && employeeDAO.existsByPhone(phone, input.getId())) {
            throw new DataAccessException(I18n.t("employee.phone.taken"));
        }
        if (input.getId() == 0) {
            Employee created = new Employee();
            copyDetails(input, created);
            created.setActive(true);
            employeeDAO.insert(created);
            SecurityLog.event(SecurityLog.actor() + " added staff record " + created.getId());
            return created;
        }
        Employee existing = employeeDAO.findById(input.getId())
                .orElseThrow(() -> new DataAccessException(I18n.t("staff.gone")));
        if (existing.isAnonymised()) {
            throw new DataAccessException(I18n.t("staff.anon.noedit")); // TODO 4.29: there is nothing personal left to edit
        }
        boolean salaryChanged = !sameMoney(existing, input);
        copyDetails(input, existing);
        employeeDAO.update(existing);
        // The amount is never written to the log; only that it changed.
        SecurityLog.event(SecurityLog.actor() + " changed staff record " + existing.getId()
                + (salaryChanged ? " (salary changed)" : ""));
        return existing;
    }

    /**
     * Marks a person as working or as having left. With an account this locks or unlocks the account (and so the staff flag with
     * it); without one it only changes the staff flag. Your own record is refused, as locking your own account is.
     */
    public void setWorking(StaffRow row, boolean working) {
        Permissions.requireAdmin("change whether a staff member is working");
        if (row == null) {
            throw new DataAccessException(I18n.t("staff.gone"));
        }
        if (working && row.employee().isAnonymised()) {
            throw new DataAccessException(I18n.t("staff.anon.nounlock")); // TODO 4.29
        }
        if (row.hasAccount()) {
            User me = Session.getCurrentUser();
            if (me != null && me.getId() == row.userId()) {
                throw new DataAccessException(I18n.t("accounts.lock.self"));
            }
            User target = new User();
            target.setId(row.userId());
            target.setUsername(row.username());
            target.setRole(row.role());
            target.setStatus(row.status());
            target.setEmployeeId(row.employee().getId());
            accountService.setLocked(target, !working);
            return;
        }
        employeeDAO.setActive(row.employee().getId(), working);
        SecurityLog.event(SecurityLog.actor() + " marked staff record " + row.employee().getId()
                + (working ? " as working" : " as left"));
    }

    /** Deletes a person with no history in any sale, stock line or shift (TODO 4.29); see {@link AccountService#removeStaff}. */
    public void remove(StaffRow row) {
        accountService.removeStaff(row);
    }

    /** Removes the personal details of a former member of staff who has history (TODO 4.29); see {@link AccountService#anonymiseStaff}. */
    public void makeAnonymous(StaffRow row) {
        accountService.anonymiseStaff(row);
    }

    /** Whether this person is in any order, stock line or closed shift: decides between "delete" and "make anonymous" on the screen. */
    public boolean hasHistory(StaffRow row) {
        Permissions.requireAdmin("view the staff list");
        return employeeDAO.hasHistory(row.employee().getId());
    }

    private static void copyDetails(Employee from, Employee to) {
        to.setFullName(from.getFullName().trim());
        to.setPhone(StaffRules.clean(from.getPhone()));
        to.setEmail(StaffRules.clean(from.getEmail()));
        to.setPosition(StaffRules.clean(from.getPosition()));
        to.setAddress(StaffRules.clean(from.getAddress()));
        to.setSalary(from.getSalary());
        to.setHireDate(from.getHireDate());
    }

    private static boolean sameMoney(Employee a, Employee b) {
        if (a.getSalary() == null || b.getSalary() == null) {
            return a.getSalary() == b.getSalary();
        }
        return a.getSalary().compareTo(b.getSalary()) == 0;
    }
}
