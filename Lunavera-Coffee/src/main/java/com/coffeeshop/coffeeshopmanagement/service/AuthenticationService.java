package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;

import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.util.Optional;

public class AuthenticationService {

    /**
     * {@code INVALID_CREDENTIALS} covers both "no such user" and "wrong password" on purpose, so the
     * login screen cannot be used to find out which usernames exist.
     */
    public enum LoginStatus { SUCCESS, INVALID_CREDENTIALS, INACTIVE, TOO_MANY_ATTEMPTS }

    public record LoginResult(LoginStatus status, User user, long retryAfterSeconds) {
        public static LoginResult of(LoginStatus status) {
            return new LoginResult(status, null, 0);
        }
    }

    /** Shared by every login screen instance, so logging out and back in does not reset the delay. */
    private static final LoginThrottle SHARED_THROTTLE = LoginThrottle.persistent("login");

    private final UserDAO userDAO;
    private final LoginThrottle throttle;

    public AuthenticationService() {
        this(new UserDAO(), SHARED_THROTTLE);
    }

    public AuthenticationService(UserDAO userDAO) {
        this(userDAO, SHARED_THROTTLE);
    }

    public AuthenticationService(UserDAO userDAO, LoginThrottle throttle) {
        this.userDAO = userDAO;
        this.throttle = throttle;
    }

    public LoginResult login(String username, String password) {
        String name = username == null ? "" : username.trim();
        long wait = throttle.secondsUntilAllowed(name);
        if (wait > 0) {
            // Not known yet whether this is an account, and people type passwords into this box: no full text in the log (TODO 2A.39).
            // Rate-limited: holding Enter on the login screen must not write a line per key press (TODO 2A.25).
            SecurityLog.warnLimited("blocked:" + SecurityLog.redactTyped(name),
                    "login blocked for the typed text " + SecurityLog.redactTyped(name)
                            + " (too many wrong passwords), " + wait + " s left");
            return new LoginResult(LoginStatus.TOO_MANY_ATTEMPTS, null, wait);
        }
        // Customers never sign in (only admin and staff do); an old CUSTOMER row is treated as unknown.
        Optional<User> found = userDAO.findByUsername(name)
                .filter(u -> u.getRole() != com.coffeeshop.coffeeshopmanagement.model.Role.CUSTOMER);
        if (found.isEmpty()) {
            PasswordUtil.matches(password, PasswordUtil.dummyHash()); // same cost as a real check, from the first attempt (2A.32)
            throttle.recordFailure(name);
            SecurityLog.warnLimited("nouser:" + SecurityLog.redactTyped(name),
                    "failed login: no such user, typed text " + SecurityLog.redactTyped(name));
            return LoginResult.of(LoginStatus.INVALID_CREDENTIALS);
        }
        User user = found.get();
        // One account, one wait: the user name and every spelling of the staff phone reach the same row, so the delay is
        // also kept per account (TODO 2A.28), not only per typed text.
        long accountWait = throttle.secondsUntilAllowedForAccount(user.getId());
        if (accountWait > 0) {
            SecurityLog.warnLimited("blocked-account:" + user.getId(),
                    "login blocked for account '" + user.getUsername() + "' (user id " + user.getId()
                            + ", too many wrong passwords), " + accountWait + " s left");
            return new LoginResult(LoginStatus.TOO_MANY_ATTEMPTS, null, accountWait);
        }
        if (!PasswordUtil.matchesAtConstantCost(password, user.getPasswordHash())) { // an old hash costs as much as a new one (2A.32)
            throttle.recordFailure(name);
            throttle.recordFailureForAccount(user.getId());
            // The name comes from the database, never from what was typed (TODO 2A.39).
            SecurityLog.warnLimited("wrongpw:" + user.getId(),
                    "failed login: wrong password for '" + user.getUsername() + "' (user id " + user.getId() + ")");
            return LoginResult.of(LoginStatus.INVALID_CREDENTIALS);
        }
        // A locked account is only reported to someone who knows its password.
        if (user.getStatus() == AccountStatus.LOCKED) {
            SecurityLog.warnLimited("locked:" + user.getId(),
                    "login refused: locked account '" + user.getUsername() + "' (user id " + user.getId() + ")");
            return LoginResult.of(LoginStatus.INACTIVE);
        }
        throttle.recordSuccess(name);
        throttle.recordSuccessForAccount(user.getId());
        SecurityLog.event("login: " + user.getUsername() + " (id " + user.getId() + ", " + user.getRole() + ")");
        upgradeHashIfOld(user, password);
        return new LoginResult(LoginStatus.SUCCESS, user, 0);
    }

    /**
     * The password was just proven right, so it is the moment to store it with today's iteration count (TODO 2.12).
     * A failure here is only logged: the person is logged in either way and it is retried at the next login.
     */
    private void upgradeHashIfOld(User user, String password) {
        if (!PasswordUtil.needsRehash(user.getPasswordHash())) return;
        try {
            String fresh = PasswordUtil.hash(password);
            userDAO.rehashPassword(user.getId(), fresh); // not updatePasswordHash: that would clear "must change password" (TODO 2.72)
            user.setPasswordHash(fresh);
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(AuthenticationService.class.getName())
                    .log(java.util.logging.Level.WARNING, "Could not upgrade the password hash of user " + user.getId(), e);
        }
    }
}
