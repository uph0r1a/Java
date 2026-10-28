package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PinService.Status;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * TODO 4.36, rule D7: the PIN of a cashier. Real SQLite; the throttles are in memory with a clock the test moves.
 */
public class PinServiceTest {

    private final UserDAO users = new UserDAO();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private PinService pins;
    private User ann;
    private User bob;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        SecurityLog.resetLimitsForTests();
        pins = new PinService(users, new EmployeeDAO(), new LoginThrottle(clock::get),
                new AuthenticationService(users, new LoginThrottle(clock::get)));
        ann = account(Role.EMPLOYEE, "Password1");
        bob = account(Role.EMPLOYEE, "Password2");
        signInAs(ann);
    }

    @After
    public void signOut() {
        TestSession.admin();
    }

    private User account(Role role, String password) {
        User user = new User();
        user.setUsername("pin-test-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return users.insert(user);
    }

    private static void signInAs(User user) {
        Session.start(users(user), null);
    }

    private static User users(User user) {
        return new UserDAO().findById(user.getId()).orElseThrow();
    }

    private void setPinOf(User user, String pin) {
        signInAs(user);
        pins.setPin(user == ann ? "Password1" : "Password2", pin);
    }

    @Test
    public void aStaffMemberSetsTheirOwnPinWithThePasswordAndItIsStoredHashed() {
        pins.setPin("Password1", "4821");
        String stored = users.pinHash(ann.getId());
        assertNotNull(stored);
        assertFalse("the PIN itself is never stored", stored.contains("4821"));
        assertTrue(pins.hasPin(ann.getId()));
    }

    @Test
    public void aWrongPasswordAnEasyPinOrAnAdministratorCannotSetOne() {
        try { pins.setPin("Wrong-one1", "4821"); fail(); } catch (DataAccessException expected) { /* fine */ }
        try { pins.setPin("Password1", "1234"); fail(); } catch (DataAccessException expected) { /* fine */ }
        assertFalse(pins.hasPin(ann.getId()));
        User admin = account(Role.ADMIN, "Admin-pass1");
        signInAs(admin);
        try { pins.setPin("Admin-pass1", "4821"); fail(); } catch (DataAccessException expected) { /* fine */ }
        assertFalse(pins.hasPin(admin.getId()));
    }

    @Test
    public void theRightPinSwitchesTheSignedInPersonToThatCashier() {
        setPinOf(bob, "5937");
        signInAs(ann);
        PinService.Outcome outcome = pins.signInByPin(bob.getId(), "5937");
        assertEquals(Status.OK, outcome.status());
        assertEquals(bob.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void theSamePersonUnlocksWithTheirOwnPin() {
        setPinOf(ann, "4821");
        assertEquals(Status.OK, pins.signInByPin(ann.getId(), "4821").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aPinNeverStartsASessionNorTakesOverAnAdministrators() {
        setPinOf(bob, "5937");
        Session.clear(); // nobody is signed in: this is the login screen's situation
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "5937").status());
        assertNull(Session.getCurrentUser());
        signInAs(account(Role.ADMIN, "Admin-pass1")); // an administrator's till
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "5937").status());
        assertEquals(Role.ADMIN, Session.getCurrentRole());
    }

    @Test
    public void anAdministratorsPinWouldNotWorkEvenIfOneWasStored() {
        User admin = account(Role.ADMIN, "Admin-pass1");
        users.setPinHash(admin.getId(), PasswordUtil.hash("5937"));
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(admin.getId(), "5937").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aLockedAccountsPinIsRefused() {
        setPinOf(bob, "5937");
        TestSession.admin(); // locking an account is an administrator's action
        users.updateStatus(bob.getId(), AccountStatus.LOCKED);
        signInAs(ann);
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "5937").status());
    }

    @Test
    public void fiveWrongPinsInARowSwitchThePinOff() {
        setPinOf(bob, "5937");
        signInAs(ann);
        for (int i = 1; i <= 4; i++) {
            PinService.Outcome wrong = pins.signInByPin(bob.getId(), "1357");
            assertEquals(Status.WRONG, wrong.status());
            assertEquals(5 - i, wrong.remaining());
            clock.addAndGet(60_000); // past any wait
        }
        assertEquals(Status.DISABLED_NOW, pins.signInByPin(bob.getId(), "1357").status());
        assertFalse(pins.hasPin(bob.getId()));
        clock.addAndGet(60_000);
        assertEquals("even the right PIN no longer works", Status.NO_PIN, pins.signInByPin(bob.getId(), "5937").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aRightPinStartsTheCountAgain() {
        setPinOf(bob, "5937");
        signInAs(ann);
        for (int i = 0; i < 3; i++) {
            pins.signInByPin(bob.getId(), "1357");
            clock.addAndGet(60_000);
        }
        assertEquals(Status.OK, pins.signInByPin(bob.getId(), "5937").status());
        signInAs(ann);
        assertEquals(4, pins.signInByPin(bob.getId(), "1357").remaining());
    }

    @Test
    public void wrongPinsInQuickSuccessionMakeTheNextOneWait() {
        setPinOf(bob, "5937");
        signInAs(ann);
        pins.signInByPin(bob.getId(), "1357");
        pins.signInByPin(bob.getId(), "1357");
        pins.signInByPin(bob.getId(), "1357"); // the third wrong one starts the wait
        PinService.Outcome waiting = pins.signInByPin(bob.getId(), "5937");
        assertEquals("the wait applies even to the right PIN", Status.WAIT, waiting.status());
        assertTrue(waiting.waitSeconds() > 0);
    }

    @Test
    public void thePasswordOpensTheTillOfTheSignedInPersonOnly() {
        assertEquals(AuthenticationService.LoginStatus.SUCCESS, pins.unlockWithPassword("Password1").status());
        assertEquals(AuthenticationService.LoginStatus.INVALID_CREDENTIALS, pins.unlockWithPassword("Password2").status());
        assertEquals("the signed-in person did not change", ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void onlyActiveStaffWithAPinAreListed() {
        setPinOf(ann, "4821");
        setPinOf(bob, "5937");
        User gone = account(Role.EMPLOYEE, "Password3");
        users.setPinHash(gone.getId(), PasswordUtil.hash("7391"));
        TestSession.admin();
        users.updateStatus(gone.getId(), AccountStatus.LOCKED);
        User admin = account(Role.ADMIN, "Admin-pass1");
        users.setPinHash(admin.getId(), PasswordUtil.hash("7391"));
        var ids = pins.cashiersWithPin().stream().map(UserDAO.PinHolder::userId).toList();
        assertTrue(ids.contains(ann.getId()) && ids.contains(bob.getId()));
        assertFalse(ids.contains(gone.getId()));
        assertFalse(ids.contains(admin.getId()));
    }

    @Test
    public void aPinTypedAtTheLoginScreenIsJustAWrongPassword() {
        setPinOf(ann, "4821");
        AuthenticationService login = new AuthenticationService(users, new LoginThrottle(clock::get));
        assertEquals(AuthenticationService.LoginStatus.INVALID_CREDENTIALS, login.login(ann.getUsername(), "4821").status());
    }

    @Test
    public void theLoginCodeDoesNotKnowThePinAtAll() throws Exception {
        Path main = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");
        for (String file : new String[]{"controller/LoginController.java", "service/AuthenticationService.java"}) {
            String text = Files.readString(main.resolve(file));
            assertFalse(file + " must not use PinService or the PIN columns", text.contains("PinService") || text.contains("pin_hash")
                    || text.contains("signInByPin"));
        }
    }
}
