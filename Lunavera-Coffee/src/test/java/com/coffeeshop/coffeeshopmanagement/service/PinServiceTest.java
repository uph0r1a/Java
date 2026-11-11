package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
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
import java.util.List;
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
        pins.setPin("Password1", "482139");
        String stored = users.pinHash(ann.getId());
        assertNotNull(stored);
        assertFalse("the PIN itself is never stored", stored.contains("482139"));
        assertTrue(pins.hasPin(ann.getId()));
    }

    @Test
    public void aWrongPasswordAnEasyPinOrAnAdministratorCannotSetOne() {
        try { pins.setPin("Wrong-one1", "482139"); fail(); } catch (DataAccessException expected) { /* fine */ }
        try { pins.setPin("Password1", "123456"); fail(); } catch (DataAccessException expected) { /* fine */ }
        assertFalse(pins.hasPin(ann.getId()));
        User admin = account(Role.ADMIN, "Admin-pass1");
        signInAs(admin);
        try { pins.setPin("Admin-pass1", "482139"); fail(); } catch (DataAccessException expected) { /* fine */ }
        assertFalse(pins.hasPin(admin.getId()));
    }

    @Test
    public void theRightPinSwitchesTheSignedInPersonToThatCashier() {
        setPinOf(bob, "593714");
        signInAs(ann);
        PinService.Outcome outcome = pins.signInByPin(bob.getId(), "593714");
        assertEquals(Status.OK, outcome.status());
        assertEquals(bob.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void theSamePersonUnlocksWithTheirOwnPin() {
        setPinOf(ann, "482139");
        assertEquals(Status.OK, pins.signInByPin(ann.getId(), "482139").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aPinNeverStartsASessionNorTakesOverAnAdministrators() {
        setPinOf(bob, "593714");
        Session.clear(); // nobody is signed in: this is the login screen's situation
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "593714").status());
        assertNull(Session.getCurrentUser());
        signInAs(account(Role.ADMIN, "Admin-pass1")); // an administrator's till
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "593714").status());
        assertEquals(Role.ADMIN, Session.getCurrentRole());
    }

    @Test
    public void anAdministratorsPinWouldNotWorkEvenIfOneWasStored() {
        User admin = account(Role.ADMIN, "Admin-pass1");
        users.setPinHash(admin.getId(), PasswordUtil.hash("593714"));
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(admin.getId(), "593714").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aLockedAccountsPinIsRefused() {
        setPinOf(bob, "593714");
        TestSession.admin(); // locking an account is an administrator's action
        users.updateStatus(bob.getId(), AccountStatus.LOCKED);
        signInAs(ann);
        assertEquals(Status.NOT_ALLOWED, pins.signInByPin(bob.getId(), "593714").status());
    }

    @Test
    public void fiveWrongPinsInARowSwitchThePinOff() {
        setPinOf(bob, "593714");
        signInAs(ann);
        for (int i = 1; i <= 4; i++) {
            PinService.Outcome wrong = pins.signInByPin(bob.getId(), "135792");
            assertEquals(Status.WRONG, wrong.status());
            assertEquals(5 - i, wrong.remaining());
            clock.addAndGet(60_000); // past any wait
        }
        assertEquals(Status.DISABLED_NOW, pins.signInByPin(bob.getId(), "135792").status());
        assertFalse(pins.hasPin(bob.getId()));
        clock.addAndGet(60_000);
        assertEquals("even the right PIN no longer works", Status.NO_PIN, pins.signInByPin(bob.getId(), "593714").status());
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
    }

    @Test
    public void aRightPinStartsTheCountAgain() {
        setPinOf(bob, "593714");
        signInAs(ann);
        for (int i = 0; i < 3; i++) {
            pins.signInByPin(bob.getId(), "135792");
            clock.addAndGet(60_000);
        }
        assertEquals(Status.OK, pins.signInByPin(bob.getId(), "593714").status());
        signInAs(ann);
        assertEquals(4, pins.signInByPin(bob.getId(), "135792").remaining());
    }

    @Test
    public void wrongPinsInQuickSuccessionMakeTheNextOneWait() {
        setPinOf(bob, "593714");
        signInAs(ann);
        pins.signInByPin(bob.getId(), "135792");
        pins.signInByPin(bob.getId(), "135792");
        pins.signInByPin(bob.getId(), "135792"); // the third wrong one starts the wait
        PinService.Outcome waiting = pins.signInByPin(bob.getId(), "593714");
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
        setPinOf(ann, "482139");
        setPinOf(bob, "593714");
        User gone = account(Role.EMPLOYEE, "Password3");
        users.setPinHash(gone.getId(), PasswordUtil.hash("739158"));
        TestSession.admin();
        users.updateStatus(gone.getId(), AccountStatus.LOCKED);
        User admin = account(Role.ADMIN, "Admin-pass1");
        users.setPinHash(admin.getId(), PasswordUtil.hash("739158"));
        var ids = pins.cashiersWithPin().stream().map(UserDAO.PinHolder::userId).toList();
        assertTrue(ids.contains(ann.getId()) && ids.contains(bob.getId()));
        assertFalse(ids.contains(gone.getId()));
        assertFalse(ids.contains(admin.getId()));
    }

    @Test
    public void aPinTypedAtTheLoginScreenIsJustAWrongPassword() {
        setPinOf(ann, "482139");
        AuthenticationService login = new AuthenticationService(users, new LoginThrottle(clock::get));
        assertEquals(AuthenticationService.LoginStatus.INVALID_CREDENTIALS, login.login(ann.getUsername(), "482139").status());
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

    // ------------------------------------------------------------------ TODO 2A.46

    private PinService pinsWithTill(TillPinCounter till) {
        return new PinService(users, new EmployeeDAO(), new LoginThrottle(clock::get),
                new AuthenticationService(users, new LoginThrottle(clock::get)), till);
    }

    /** The audit log may be read by an administrator only, so it is read as one and the person on the till is signed in again after. */
    private static List<AuditEntry> auditLines(AuditAction action, User about) {
        User onTheTill = Session.getCurrentUser();
        com.coffeeshop.coffeeshopmanagement.model.Employee employee = Session.getCurrentEmployee();
        TestSession.admin();
        try {
            return new AuditDAO().find(new AuditDAO.Filter(null, null, action, null, about.getUsername()), 20, 0);
        } finally {
            if (onTheTill != null) Session.start(onTheTill, employee); else TestSession.clear();
        }
    }

    private void switchOffPinOf(User victim) {
        for (int i = 0; i < PinRules.MAX_WRONG_IN_A_ROW; i++) {
            pins.signInByPin(victim.getId(), "135792");
            clock.addAndGet(60_000);
        }
        assertFalse(pins.hasPin(victim.getId()));
    }

    @Test
    public void switchingAPinOffLeavesAnAuditLineNamingWhoWasSignedInOnTheTill() {
        setPinOf(bob, "593714");
        signInAs(ann);
        switchOffPinOf(bob);
        List<AuditEntry> lines = auditLines(AuditAction.PIN_SWITCHED_OFF, bob);
        assertEquals(1, lines.size());
        AuditEntry line = lines.get(0);
        assertEquals(Integer.valueOf(ann.getId()), line.actorUserId());
        assertEquals(Integer.valueOf(bob.getId()), line.targetId());
        assertEquals(String.valueOf(PinRules.MAX_WRONG_IN_A_ROW), line.detail());
    }

    @Test
    public void theOwnerOfTheSwitchedOffPinIsToldOnceAndNobodyElseIs() {
        setPinOf(bob, "593714");
        signInAs(ann);
        switchOffPinOf(bob);
        assertFalse("the person who typed the wrong PINs has no notice", pins.takeSwitchedOffNotice(ann.getId()));
        assertTrue(pins.takeSwitchedOffNotice(bob.getId()));
        assertFalse("shown once", pins.takeSwitchedOffNotice(bob.getId()));
    }

    @Test
    public void aPinThatWasNotSwitchedOffByWrongTriesGivesNoNotice() {
        setPinOf(bob, "593714");
        assertFalse(pins.takeSwitchedOffNotice(bob.getId()));
        pins.removeOwnPin(); // bob removed it himself
        assertFalse(pins.takeSwitchedOffNotice(bob.getId()));
    }

    @Test
    public void settingANewPinAnswersTheNotice() {
        setPinOf(bob, "593714");
        signInAs(ann);
        switchOffPinOf(bob);
        assertTrue(AppFlags.has(AppFlags.PIN_OFF_NOTICE_PREFIX + bob.getId()));
        setPinOf(bob, "482139");
        assertFalse(AppFlags.has(AppFlags.PIN_OFF_NOTICE_PREFIX + bob.getId()));
    }

    @Test
    public void wrongPinsForSeveralNamesCloseThePadOfThatTill() {
        User cy = account(Role.EMPLOYEE, "Password3");
        User dee = account(Role.EMPLOYEE, "Password4");
        setPinOf(bob, "593714");
        signInAs(cy);
        pins.setPin("Password3", "739158");
        signInAs(dee);
        pins.setPin("Password4", "264817");
        User[] names = {bob, cy, dee};
        TillPinCounter till = new TillPinCounter(clock::get);
        PinService thisTill = pinsWithTill(till);
        signInAs(ann);
        for (int i = 0; i < 9; i++) { // going round three names: none reaches its own limit of 5 before the till's 10th
            assertEquals(Status.WRONG, thisTill.signInByPin(names[i % 3].getId(), "135792").status());
            clock.addAndGet(60_000);
        }
        assertFalse(thisTill.isPinPadClosed());
        thisTill.signInByPin(names[0].getId(), "135792"); // the tenth wrong PIN on this till
        assertTrue(thisTill.isPinPadClosed());
        List<AuditEntry> closed = auditLines(AuditAction.PIN_TILL_CLOSED, ann);
        assertEquals("one audit line when the pad closed", 1, closed.size());
        assertEquals(Integer.valueOf(ann.getId()), closed.get(0).actorUserId());
    }

    @Test
    public void aClosedPadRefusesEvenTheRightPinWithoutCountingAgainstAnyName() {
        setPinOf(bob, "593714");
        TillPinCounter till = new TillPinCounter(clock::get);
        PinService thisTill = pinsWithTill(till);
        signInAs(ann);
        for (int i = 0; i < TillPinCounter.MAX_WRONG_ON_A_TILL; i++) till.recordWrong();
        assertEquals(Status.TILL_CLOSED, thisTill.signInByPin(bob.getId(), "593714").status());
        assertEquals(Status.TILL_CLOSED, thisTill.signInByPin(bob.getId(), "135792").status());
        assertEquals("bob's own count was not touched", 0, wrongCountOf(bob));
        assertEquals(ann.getId(), Session.getCurrentUser().getId());
        assertEquals("the password still opens the till", AuthenticationService.LoginStatus.SUCCESS,
                thisTill.unlockWithPassword("Password1").status());
    }

    @Test
    public void theTillOpensAgainAfterTheClosedTimeAndARightPinClearsTheCount() {
        setPinOf(bob, "593714");
        TillPinCounter till = new TillPinCounter(clock::get);
        PinService thisTill = pinsWithTill(till);
        signInAs(ann);
        for (int i = 0; i < TillPinCounter.MAX_WRONG_ON_A_TILL; i++) till.recordWrong();
        clock.addAndGet(TillPinCounter.CLOSED_MINUTES * 60_000L + 1);
        assertEquals(Status.OK, thisTill.signInByPin(bob.getId(), "593714").status());
        assertEquals(0, till.wrongInARow());
    }

    @Test
    public void anotherTillsWrongPinsDoNotCloseThisOne() {
        setPinOf(bob, "593714");
        TillPinCounter other = new TillPinCounter(clock::get);
        for (int i = 0; i < TillPinCounter.MAX_WRONG_ON_A_TILL; i++) other.recordWrong();
        PinService thisTill = pinsWithTill(new TillPinCounter(clock::get));
        signInAs(ann);
        assertEquals(Status.OK, thisTill.signInByPin(bob.getId(), "593714").status());
    }

    private int wrongCountOf(User user) {
        try (var connection = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             var statement = connection.prepareStatement("SELECT pin_failures FROM users WHERE id = ?")) {
            statement.setInt(1, user.getId());
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void theLoginScreenCallsTheNoticeButNotThePinService() throws Exception {
        Path main = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");
        String login = Files.readString(main.resolve("LoginController.java"));
        assertTrue(login.contains("PinOffNotice.afterSignIn()"));
        String lock = Files.readString(main.resolve("LockScreen.java"));
        assertTrue("the lock screen tells a person who came back with the password", lock.contains("PinOffNotice::afterSignIn"));
        assertTrue("and closes its PIN box when the till's pad closes", lock.contains("isPinPadClosed"));
    }
}
