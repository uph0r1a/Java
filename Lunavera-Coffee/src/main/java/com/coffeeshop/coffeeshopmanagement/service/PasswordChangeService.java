package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.util.Optional;

/**
 * Lets a signed-in person change their OWN password (TODO 2.2). The rules live here, not in the dialog, so they
 * can be tested: the new password must be strong enough, must not be the well-known default, must differ from the
 * current one, and (unless the person has just logged in with the default password) the current one must be right.
 */
public class PasswordChangeService {

    public enum Result { OK, NO_SUCH_USER, WRONG_CURRENT, WEAK, IS_DEFAULT, SAME_AS_CURRENT }

    private final UserDAO userDAO;

    public PasswordChangeService() {
        this(new UserDAO());
    }

    public PasswordChangeService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * @param currentPassword what the person typed as their current password; ignored when
     *                        {@code requireCurrent} is false (the forced change right after logging in with the default)
     */
    public Result change(int userId, String currentPassword, String newPassword, boolean requireCurrent) {
        Optional<User> found = userDAO.findById(userId);
        if (found.isEmpty()) return Result.NO_SUCH_USER;
        User user = found.get();

        if (requireCurrent
                && (currentPassword == null || !PasswordUtil.matches(currentPassword, user.getPasswordHash()))) {
            return Result.WRONG_CURRENT;
        }
        if (!ValidationUtil.isValidPassword(newPassword)) return Result.WEAK;
        if (DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(newPassword)) return Result.IS_DEFAULT;
        if (PasswordUtil.matches(newPassword, user.getPasswordHash())) return Result.SAME_AS_CURRENT;

        userDAO.updatePasswordHash(userId, PasswordUtil.hash(newPassword));
        return Result.OK;
    }
}
