package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.36: the state of the lock (the screen itself cannot be seen without a display). */
public class TillLockTest {

    @Before
    public void setUp() {
        User user = new User();
        user.setId(7);
        user.setUsername("lock-test");
        user.setRole(Role.EMPLOYEE);
        Session.start(user, null);
    }

    @After
    public void tearDown() {
        TillLock.setPresenter(null);
        Session.clear();
    }

    @Test
    public void nothingLocksWithoutALockScreenOrWithoutASignedInPerson() {
        assertFalse(TillLock.canLock());
        TillLock.setPresenter(() -> { });
        assertTrue(TillLock.canLock());
        Session.clear();
        assertFalse(TillLock.canLock());
        assertFalse(TillLock.lock());
    }

    @Test
    public void lockShowsTheScreenKeepsTheSessionAndEndsUnlocked() {
        boolean[] shown = {false};
        TillLock.setPresenter(() -> {
            shown[0] = true;
            assertTrue(TillLock.isLocked());
            assertNotNull("the person stays signed in while locked", Session.getCurrentUser());
            assertFalse("a second lock while locked is refused", TillLock.lock());
            assertFalse("an idle till that is already locked is signed out instead", TillLock.lockForIdle());
        });
        assertTrue(TillLock.lock());
        assertTrue(shown[0]);
        assertFalse(TillLock.isLocked());
        assertNotNull(Session.getCurrentUser());
    }

    @Test
    public void aLockScreenThatFailsStillLeavesTheTillUnlockedState() {
        TillLock.setPresenter(() -> { throw new IllegalStateException("boom"); });
        try {
            TillLock.lock();
            fail();
        } catch (IllegalStateException expected) {
            assertFalse("the flag is not stuck", TillLock.isLocked());
        }
    }
}
