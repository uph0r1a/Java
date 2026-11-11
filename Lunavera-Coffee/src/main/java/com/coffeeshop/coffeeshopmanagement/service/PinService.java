package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.util.List;
import java.util.Optional;

/**
 * The PIN of a cashier, for locking the till and for handing it to the next cashier without a full sign-out (TODO 4.36).
 *
 * <p><b>Rule D7, as chosen here</b> (the owner may change it):
 * <ul>
 *   <li>Only <em>staff</em> accounts have a PIN. An administrator never does: every administrator screen stays behind the
 *       password, and a till locked by an administrator opens only with that administrator's password.</li>
 *   <li>A PIN <b>never signs anybody in</b>. It only works while somebody is already signed in with a password on this till
 *       (the till is locked, not signed out), and this class is not used by the login screen at all. A PIN typed into the
 *       login box is just a wrong password.</li>
 *   <li>A PIN is 4 to 6 digits and not an easy one ({@link PinRules}); it is stored hashed like a password; the person sets it
 *       themselves and must give the password to do it.</li>
 *   <li>Its own throttle (one wait per account, kept in the database like the login one) and a hard limit: after
 *       {@value PinRules#MAX_WRONG_IN_A_ROW} wrong PINs in a row the PIN is switched off, so a stranger at the till cannot
 *       try the 10 000 possible four-digit PINs. The person signs in with the password and sets a new one.</li>
 *   <li>Because anybody signed in can type wrong PINs for a colleague's name, switching a PIN off is never silent (TODO 2A.46):
 *       it writes an audit line (who was signed in on the till, how many wrong PINs) in the same transaction, the owner of the PIN
 *       is told at the next sign-in ({@link #takeSwitchedOffNotice}), and each till also counts its wrong PINs for all names
 *       together ({@link TillPinCounter}): after {@value TillPinCounter#MAX_WRONG_ON_A_TILL} in a row the PIN pad of that till is
 *       closed for a while, so one till cannot switch off everybody's PIN.</li>
 *   <li>An administrator resetting a password, or a former member of staff being made anonymous, also removes the PIN.</li>
 * </ul>
 * A PIN protects the till, not the database: somebody who has the database file can try PINs offline. The password rules are
 * what protect that.
 */
public class PinService {

    /** {@code DISABLED_NOW}: this wrong PIN was the last allowed one and the PIN is now off. */
    /** {@code TILL_CLOSED}: too many wrong PINs on this till; the PIN pad is closed for a while (TODO 2A.46). */
    public enum Status { OK, WRONG, WAIT, NO_PIN, DISABLED_NOW, NOT_ALLOWED, TILL_CLOSED }

    /** {@code remaining}: wrong PINs still allowed (for WRONG); {@code waitSeconds}: for WAIT. */
    public record Outcome(Status status, User user, Employee employee, int remaining, long waitSeconds) {
        static Outcome of(Status status) {
            return new Outcome(status, null, null, 0, 0);
        }
    }

    private static final LoginThrottle SHARED_THROTTLE = LoginThrottle.persistent("pin");

    private final UserDAO userDAO;
    private final EmployeeDAO employeeDAO;
    private final LoginThrottle throttle;
    private static final TillPinCounter THIS_TILL = new TillPinCounter();

    private final AuthenticationService authentication;
    private final TillPinCounter till;

    public PinService() {
        this(new UserDAO(), new EmployeeDAO(), SHARED_THROTTLE, new AuthenticationService(), THIS_TILL);
    }

    public PinService(UserDAO userDAO, EmployeeDAO employeeDAO, LoginThrottle throttle, AuthenticationService authentication) {
        this(userDAO, employeeDAO, throttle, authentication, new TillPinCounter());
    }

    public PinService(UserDAO userDAO, EmployeeDAO employeeDAO, LoginThrottle throttle, AuthenticationService authentication,
                      TillPinCounter till) {
        this.till = till;
        this.userDAO = userDAO;
        this.employeeDAO = employeeDAO;
        this.throttle = throttle;
        this.authentication = authentication;
    }

    /** Whether the account may have a PIN at all: staff only. */
    public static boolean mayHavePin(User user) {
        return user != null && user.getRole() == Role.EMPLOYEE;
    }

    public boolean hasPin(int userId) {
        return userDAO.pinHash(userId) != null;
    }

    /** The cashiers whose PIN can open the till, for the lock screen's list. */
    public List<UserDAO.PinHolder> cashiersWithPin() {
        return userDAO.pinHolders();
    }

    /**
     * Sets (or replaces) the signed-in person's own PIN. The password is checked first, with the login throttle, so a person
     * walking past an unlocked till cannot set a PIN of their own for somebody else's account.
     *
     * @throws DataAccessException with the reason in plain words
     */
    public void setPin(String password, String pin) {
        User signedIn = Session.getCurrentUser();
        if (signedIn == null) {
            throw new DataAccessException(I18n.t("pin.set.notsignedin"));
        }
        User user = userDAO.findById(signedIn.getId()).orElseThrow(() -> new DataAccessException(I18n.t("pin.set.notsignedin")));
        if (!mayHavePin(user) || user.getStatus() != AccountStatus.ACTIVE) {
            throw new DataAccessException(I18n.t("pin.set.staffonly"));
        }
        if (!PinRules.isAcceptable(pin)) {
            throw new DataAccessException(I18n.t("pin.rule", PinRules.MIN_LENGTH, PinRules.MAX_LENGTH));
        }
        AuthenticationService.LoginResult proof = authentication.login(user.getUsername(), password);
        if (proof.status() == AuthenticationService.LoginStatus.TOO_MANY_ATTEMPTS) {
            throw new DataAccessException(I18n.t("pin.set.wait", proof.retryAfterSeconds()));
        }
        if (proof.status() != AuthenticationService.LoginStatus.SUCCESS || proof.user().getId() != user.getId()) {
            throw new DataAccessException(I18n.t("pin.set.password"));
        }
        userDAO.setPinHash(user.getId(), PasswordUtil.hash(pin));
        AppFlags.clear(AppFlags.PIN_OFF_NOTICE_PREFIX + user.getId()); // a new PIN is the answer to the notice
        SecurityLog.event("PIN set by '" + user.getUsername() + "' (user id " + user.getId() + ")");
    }

    /** Removes the signed-in person's own PIN. */
    public void removeOwnPin() {
        User signedIn = Session.getCurrentUser();
        if (signedIn == null) return;
        userDAO.clearPin(signedIn.getId());
        SecurityLog.event("PIN removed by '" + signedIn.getUsername() + "' (user id " + signedIn.getId() + ")");
    }

    /**
     * Checks {@code pin} for the staff account {@code userId} and, when it is right, makes that person the signed-in one (the
     * same person who locked the till, or another cashier taking over). Refused ({@link Status#NOT_ALLOWED}) unless somebody is
     * signed in with a password <em>and is staff</em>: a PIN never starts a session, and never takes over an administrator's.
     */
    public Outcome signInByPin(int userId, String pin) {
        User locker = Session.getCurrentUser();
        if (locker == null || locker.getRole() != Role.EMPLOYEE) {
            SecurityLog.warnLimited("pin-refused", "PIN refused: nobody (or an administrator) is signed in on this till");
            return Outcome.of(Status.NOT_ALLOWED);
        }
        if (till.isClosed()) {
            return Outcome.of(Status.TILL_CLOSED); // before the database: a closed pad must not count against anybody's PIN
        }
        Optional<User> found = userDAO.findById(userId);
        if (found.isEmpty() || !mayHavePin(found.get()) || found.get().getStatus() != AccountStatus.ACTIVE) {
            return Outcome.of(Status.NOT_ALLOWED);
        }
        User target = found.get();
        long wait = throttle.secondsUntilAllowedForAccount(userId);
        if (wait > 0) {
            return new Outcome(Status.WAIT, null, null, 0, wait);
        }
        String stored = userDAO.pinHash(userId);
        if (stored == null) {
            return Outcome.of(Status.NO_PIN);
        }
        boolean right = PinRules.hasShape(pin) && PasswordUtil.matchesAtConstantCost(pin, stored);
        if (!right) {
            throttle.recordFailureForAccount(userId);
            if (till.recordWrong()) {
                closeThisTill(locker);
            }
            int failures = userDAO.addPinFailure(userId);
            if (failures >= PinRules.MAX_WRONG_IN_A_ROW) {
                userDAO.switchOffPinAfterWrongTries(userId, target.getUsername(), failures); // PIN off, audit line and notice together
                SecurityLog.warn("PIN of '" + target.getUsername() + "' (user id " + userId + ") switched off after "
                        + failures + " wrong PINs in a row, typed on the till of '" + locker.getUsername() + "' (user id "
                        + locker.getId() + ")");
                return Outcome.of(Status.DISABLED_NOW);
            }
            SecurityLog.warnLimited("wrongpin:" + userId, "wrong PIN for '" + target.getUsername() + "' (user id " + userId + ")");
            return new Outcome(Status.WRONG, null, null, PinRules.MAX_WRONG_IN_A_ROW - failures, 0);
        }
        throttle.recordSuccessForAccount(userId);
        till.recordRight();
        userDAO.resetPinFailures(userId);
        Employee employee = target.getEmployeeId() == null ? null : employeeDAO.findById(target.getEmployeeId()).orElse(null);
        Session.start(target, employee);
        if (locker.getId() == userId) {
            SecurityLog.event("till unlocked by PIN: " + target.getUsername() + " (id " + userId + ")");
        } else {
            SecurityLog.event("till handed over by PIN: " + locker.getUsername() + " (id " + locker.getId() + ") -> "
                    + target.getUsername() + " (id " + userId + ")");
        }
        return new Outcome(Status.OK, target, employee, 0, 0);
    }

    /** True while this till's PIN pad is closed after too many wrong PINs for any names (TODO 2A.46); the password still opens it. */
    public boolean isPinPadClosed() {
        return till.isClosed();
    }

    /** One audit line and one security-log line when the wrong PINs of this till reach the limit; never throws (the PIN check goes on). */
    private void closeThisTill(User locker) {
        SecurityLog.warn("PIN pad closed on this till after " + TillPinCounter.MAX_WRONG_ON_A_TILL + " wrong PINs in a row; the till is signed in as '"
                + locker.getUsername() + "' (user id " + locker.getId() + ")");
        AuditDAO.recordQuietly(AuditAction.PIN_TILL_CLOSED, AuditTarget.ACCOUNT, locker.getId(), locker.getUsername(), null, null,
                TillPinCounter.MAX_WRONG_ON_A_TILL + "|" + TillPinCounter.CLOSED_MINUTES);
    }

    /**
     * Whether the person's PIN was switched off by wrong tries and they have not been told yet; when it was, the notice is taken
     * away so it is shown once (TODO 2A.46). Called after a sign-in with the password, which is the only way to learn it.
     */
    public boolean takeSwitchedOffNotice(int userId) {
        String flag = AppFlags.PIN_OFF_NOTICE_PREFIX + userId;
        if (!AppFlags.has(flag)) return false;
        AppFlags.clear(flag);
        return true;
    }

    /**
     * Opens a till locked by whoever is signed in, with that person's password (the way an administrator's till opens, and the
     * way anyone gets in who forgot the PIN). Uses the login throttle. The signed-in person stays the same.
     */
    public AuthenticationService.LoginResult unlockWithPassword(String password) {
        User locker = Session.getCurrentUser();
        if (locker == null) {
            return AuthenticationService.LoginResult.of(AuthenticationService.LoginStatus.INVALID_CREDENTIALS);
        }
        AuthenticationService.LoginResult result = authentication.login(locker.getUsername(), password);
        if (result.status() == AuthenticationService.LoginStatus.SUCCESS && result.user().getId() != locker.getId()) {
            return AuthenticationService.LoginResult.of(AuthenticationService.LoginStatus.INVALID_CREDENTIALS);
        }
        return result;
    }
}
