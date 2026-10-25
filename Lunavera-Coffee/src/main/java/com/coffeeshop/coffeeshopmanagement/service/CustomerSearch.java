package com.coffeeshop.coffeeshopmanagement.service;

import java.util.Locale;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

/**
 * What the customer screen's search box looks at (TODO 5.35): the name, the phone and the e-mail. Kept out of the screen so
 * the rule can be tested without JavaFX.
 * <ul>
 *   <li>Name and e-mail are compared through {@link TextNormalizer#searchKey}: no case, no accents, either accent encoding.</li>
 *   <li>The phone matches as typed, and also in its canonical form, so "+84 901 111 222" finds the stored "0901111222"
 *       (TODO 3.39).</li>
 * </ul>
 * A blank search matches everyone.
 */
public final class CustomerSearch {

    private CustomerSearch() {
    }

    public static boolean matches(Customer customer, String typed) {
        String keyword = TextNormalizer.searchKey(typed);
        if (keyword.isEmpty()) {
            return true;
        }
        if (customer == null) {
            return false;
        }
        if (TextNormalizer.searchKey(customer.getFullName()).contains(keyword)) {
            return true;
        }
        String email = customer.getEmail();
        if (email != null && TextNormalizer.searchKey(email).contains(keyword)) {
            return true;
        }
        String phone = customer.getPhone();
        if (phone == null) {
            return false;
        }
        String phoneKeyword = ValidationUtil.normalizePhone(keyword);
        return phone.toLowerCase(Locale.ROOT).contains(keyword)
                || (phoneKeyword != null && !phoneKeyword.isEmpty() && phone.contains(phoneKeyword));
    }
}
