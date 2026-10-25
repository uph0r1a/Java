package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

/**
 * When does a customer who was attached to the sale stop being the right one? (TODO 2.70, 6.5.) The till attaches a customer
 * when a known phone number is typed or "Gắn khách hàng" is used. {@code CheckoutService.pay} then prefers that customer over
 * whatever is typed, so if the boxes no longer describe them, the sale would be paid, and the points given, under the wrong
 * person. The rule: the attached customer is still the right one only while both boxes still match them.
 * <ul>
 *   <li><b>Phone:</b> the typed phone, in any spelling ({@link ValidationUtil#normalizePhone}), equals theirs. A customer saved
 *       <em>without</em> a phone matches only an empty phone box; typing a number detaches them (the old rule skipped
 *       customers with no phone, so a new number was silently paid under the wrong person).</li>
 *   <li><b>Name:</b> the typed name equals theirs (ignoring spaces at the ends). The till never renames a customer.</li>
 * </ul>
 */
public final class AttachedCustomerRule {

    private AttachedCustomerRule() {
    }

    public static boolean stillMatches(Customer attached, String typedName, String typedPhone) {
        if (attached == null) {
            return false;
        }
        String ownPhone = attached.getPhone() == null ? "" : ValidationUtil.normalizePhone(attached.getPhone().trim());
        String phone = typedPhone == null ? "" : ValidationUtil.normalizePhone(typedPhone.trim());
        boolean phoneMatches = ownPhone.isEmpty() ? phone.isEmpty() : ownPhone.equals(phone);
        if (!phoneMatches) {
            return false;
        }
        String ownName = attached.getFullName() == null ? "" : attached.getFullName().trim();
        String name = typedName == null ? "" : typedName.trim();
        return name.equals(ownName);
    }
}
