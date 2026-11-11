package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.util.DateLimits;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLimits;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The rules for a staff record typed on the staff screen (TODO 4.12), kept apart from the screen and from the database so they
 * can be tested on their own and so a second caller (a CSV import of staff, say) gets the same answers. No JavaFX, no SQL.
 * The texts are the ones the person sees.
 */
public final class StaffRules {

    private StaffRules() {
    }

    /**
     * @return the sentence to show when the record may not be saved, or {@code null} when it is fine. The first problem found
     *         is returned, in the order of the form.
     */
    public static String problem(Employee staff, LocalDate today) {
        if (staff == null || ValidationUtil.isBlank(staff.getFullName())) {
            return I18n.t("staff.val.name");
        }
        if (staff.getFullName().trim().length() > TextLimits.NAME_MAX) {
            return I18n.t("staff.val.toolong", I18n.t("staff.label.name"), TextLimits.NAME_MAX);
        }
        if (!ValidationUtil.isBlank(staff.getPhone()) && !ValidationUtil.isValidPhone(staff.getPhone())) {
            return I18n.t("account.rule.phone");
        }
        if (!ValidationUtil.isBlank(staff.getEmail())) {
            if (!ValidationUtil.isValidEmail(staff.getEmail())) {
                return I18n.t("staff.val.email");
            }
            if (staff.getEmail().trim().length() > TextLimits.EMAIL_MAX) {
                return I18n.t("staff.val.toolong", I18n.t("staff.label.email"), TextLimits.EMAIL_MAX);
            }
        }
        if (length(staff.getPosition()) > TextLimits.POSITION_MAX) {
            return I18n.t("staff.val.toolong", I18n.t("staff.label.position"), TextLimits.POSITION_MAX);
        }
        if (length(staff.getAddress()) > TextLimits.ADDRESS_MAX) {
            return I18n.t("staff.val.toolong", I18n.t("staff.label.address"), TextLimits.ADDRESS_MAX);
        }
        BigDecimal salary = staff.getSalary();
        if (salary != null && salary.signum() < 0) {
            return I18n.t("staff.val.salary");
        }
        String hireProblem = hireDateProblem(staff.getHireDate(), today);
        if (hireProblem != null) {
            return hireProblem;
        }
        return null;
    }

    /** A hire date that is in the future or before the year 2000 (a typed year such as 26, TODO 3.78): a sentence to show, else null. */
    public static String hireDateProblem(java.time.LocalDate hired, java.time.LocalDate today) {
        if (hired == null) {
            return null;
        }
        if (today != null && hired.isAfter(today)) {
            return I18n.t("accounts.val.date.message");
        }
        if (hired.isBefore(DateLimits.EARLIEST)) {
            return I18n.t("date.val.early", DateLimits.EARLIEST.getYear());
        }
        return null;
    }

    private static int length(String text) {
        return text == null ? 0 : text.trim().length();
    }

    /** A typed text as it is stored: trimmed, and null when nothing is left. */
    public static String clean(String text) {
        String clean = text == null ? null : com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.clean(text).trim(); // TODO 3.71: no invisible characters from a pasted contact
        return clean == null || clean.isBlank() ? null : clean;
    }
}
