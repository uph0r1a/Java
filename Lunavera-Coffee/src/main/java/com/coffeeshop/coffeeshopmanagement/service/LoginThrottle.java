package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptStore;
import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptStore.Attempts;
import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptsDAO;
import com.coffeeshop.coffeeshopmanagement.util.MonotonicClock;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Slows down password guessing without ever locking anybody out: the first {@value #FREE_FAILURES}
 * wrong attempts in a row for a username are free (typos), then every further wrong attempt makes
 * the next one wait longer: 1, 2, 4, 8, 16 and then 30 seconds at most. A correct login resets it,
 * and so does {@value #FORGET_AFTER_MILLIS} ms without any wrong attempt. The counters live in memory and, for the running
 * program ({@link #persistent}), also in the database through a {@link LoginAttemptStore}, so closing and reopening the app does
 * not clear a delay (TODO 2A.19) and, with MariaDB, every till sees the same delay. If the database cannot be read or written
 * the throttle carries on in memory, with one warning a minute in the log.
 *
 * <p><b>The wait can never exceed {@value #MAX_DELAY_MILLIS} ms, whatever is stored</b> (TODO 2A.27): anybody at the till can
 * keep a known name on the delay by typing wrong passwords, and the real owner of the name then waits at most that long per
 * attempt. The stored times are calendar times, converted by the store (which cuts every delay to the ceiling, never reads a
 * failure from the future and corrects a bad row in the table), so a row written while the PC clock was wrong, or edited by
 * hand, can never become a lock-out.
 *
 * <p>Usernames that do not exist are throttled exactly like real ones, so the throttle itself does
 * not reveal which usernames exist.
 *
 * <p>An account is reached by its user name or by its staff phone, written in many ways, so the wait is also kept
 * <b>per account</b> ({@link #secondsUntilAllowedForAccount}): every way of typing it shares one wait (TODO 2A.28).
 *
 * <p>Time is read from {@link MonotonicClock}, not from the computer's clock, so setting the Windows clock forward or back
 * neither clears a delay nor stretches it (TODO 2A.37). Memory is bounded to {@value #MAX_TRACKED} names; when the table is
 * full, a new name pushes out the least important entry (see {@link #makeRoom}) and never everything at once, so typing
 * many different names cannot wipe the delay of the name that is really being attacked (TODO 2A.31).
 */
public final class LoginThrottle {

    static final int FREE_FAILURES = 2;
    private static final long BASE_DELAY_MILLIS = 1_000;
    private static final long MAX_DELAY_MILLIS = 30_000;
    private static final long FORGET_AFTER_MILLIS = 15 * 60_000;
    static final int MAX_TRACKED = 1000;

    private static final class State {
        int failures;
        long lastFailure;
        /** Whether {@link #blockedUntil} means anything: the first free failures set no delay (and a clock's zero is arbitrary). */
        boolean delayed;
        long blockedUntil;
    }

    private static final Logger LOGGER = Logger.getLogger(LoginThrottle.class.getName());

    private final LongSupplier clock;
    private final Map<String, State> states = new HashMap<>();
    private final LoginAttemptStore store;
    private final String scope;
    private long lastStoreWarning;
    private boolean warnedBefore;

    public LoginThrottle() {
        this(MonotonicClock::millis);
    }

    public LoginThrottle(LongSupplier clockMillis) {
        this(clockMillis, null, "memory");
    }

    /** Counters also kept in {@code store} under {@code scope}; a {@code null} store means memory only. */
    public LoginThrottle(LongSupplier clockMillis, LoginAttemptStore store, String scope) {
        this.clock = clockMillis;
        this.store = store;
        this.scope = scope;
    }

    /** A throttle whose counters are also kept in the database under {@code scope} and survive a restart (TODO 2A.19). */
    public static LoginThrottle persistent(String scope) {
        return new LoginThrottle(MonotonicClock::millis, new LoginAttemptsDAO(), scope);
    }

    /**
     * The bucket of a typed text. A phone number is reduced to one form first (TODO 2A.28): {@code +84 901 111 222},
     * {@code 84901111222}, {@code 0901.111.222} and {@code 0901111222} sign in to the same account, so they must share one
     * counter. Control characters are dropped so a typed text can never equal an account's own bucket name.
     */
    private static String key(String username) {
        if (username == null) return "";
        String typed = username.replaceAll("\\p{Cntrl}", "").trim();
        return ValidationUtil.normalizePhone(typed).toLowerCase(Locale.ROOT);
    }

    /** The bucket of an account, whatever the person typed to reach it (user name or phone). Cannot clash with {@link #key}. */
    private static String accountKey(int userId) {
        return "\0account:" + userId;
    }

    /** Seconds still to wait for this account (all the ways of typing its name share the wait); 0 when allowed. */
    public synchronized long secondsUntilAllowedForAccount(int userId) {
        return remainingSeconds(accountKey(userId));
    }

    public synchronized void recordFailureForAccount(int userId) {
        failure(accountKey(userId));
    }

    public synchronized void recordSuccessForAccount(int userId) {
        forget(accountKey(userId));
    }

    /** Seconds the caller must still wait for this username, or 0 when an attempt is allowed. */
    public synchronized long secondsUntilAllowed(String username) {
        return remainingSeconds(key(username));
    }

    private long remainingSeconds(String bucket) {
        State state = lookup(bucket);
        if (state == null || !state.delayed) return 0;
        long remaining = state.blockedUntil - clock.getAsLong();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public synchronized void recordFailure(String username) {
        failure(key(username));
    }

    private void failure(String bucket) {
        long now = clock.getAsLong();
        State state = lookup(bucket);
        if (state == null) {
            if (states.size() >= MAX_TRACKED) {
                makeRoom(now);
            }
            state = new State();
            states.put(bucket, state);
        }
        if (state.failures > 0 && now - state.lastFailure > FORGET_AFTER_MILLIS) state.failures = 0;
        state.lastFailure = now;
        state.failures++;
        int extra = state.failures - FREE_FAILURES;
        if (extra > 0) {
            long delay = Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << Math.min(extra - 1, 5));
            state.delayed = true;
            state.blockedUntil = now + delay;
        }
        storeSave(bucket, state, now);
    }

    /**
     * Frees one place for a new name. First every entry that has been quiet for longer than {@value #FORGET_AFTER_MILLIS} ms
     * goes (it would be forgotten at its next failure anyway). If that frees nothing, the weakest entry goes: one that is not
     * waiting right now before one that is, then the one with fewer wrong attempts, then the one that failed longest ago.
     * Names typed in a flood have one wrong attempt and no wait, so they push out each other and not the name that has
     * a delay running.
     */
    private void makeRoom(long now) {
        Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastFailure > FORGET_AFTER_MILLIS) {
                it.remove();
            }
        }
        if (states.size() < MAX_TRACKED) {
            return;
        }
        String weakest = null;
        State weakestState = null;
        for (Map.Entry<String, State> entry : states.entrySet()) {
            if (weakestState == null || weaker(entry.getValue(), weakestState, now)) {
                weakest = entry.getKey();
                weakestState = entry.getValue();
            }
        }
        states.remove(weakest);
    }

    /** True when {@code a} matters less than {@code b} (see {@link #makeRoom}). */
    private static boolean weaker(State a, State b, long now) {
        boolean aWaiting = a.delayed && a.blockedUntil > now;
        boolean bWaiting = b.delayed && b.blockedUntil > now;
        if (aWaiting != bWaiting) return !aWaiting;
        if (a.failures != b.failures) return a.failures < b.failures;
        return a.lastFailure < b.lastFailure;
    }

    /** How many names are remembered (for tests). */
    synchronized int trackedNames() {
        return states.size();
    }

    public synchronized void recordSuccess(String username) {
        forget(key(username));
    }

    private void forget(String bucket) {
        states.remove(bucket);
        if (store != null) {
            try {
                store.delete(scope, bucket);
            } catch (RuntimeException e) {
                storeFailed(e);
            }
        }
    }

    /**
     * The counters of a bucket: the memory copy, brought up to date from the store when the store knows more (another till,
     * or the run before a restart). A store that fails is not allowed to stop a sign-in or to switch the delay off.
     */
    private State lookup(String bucket) {
        State memory = states.get(bucket);
        if (store == null) {
            return memory;
        }
        Attempts stored;
        try {
            stored = store.load(scope, bucket, clock.getAsLong());
        } catch (RuntimeException e) {
            storeFailed(e);
            return memory;
        }
        if (stored == null) {
            return memory;
        }
        if (memory == null || stored.failures() > memory.failures || stored.lastFailure() > memory.lastFailure + 2_000) {
            State fromStore = new State();
            fromStore.failures = stored.failures();
            fromStore.lastFailure = stored.lastFailure();
            fromStore.delayed = stored.delayed();
            fromStore.blockedUntil = stored.blockedUntil();
            if (memory == null && states.size() >= MAX_TRACKED) {
                makeRoom(clock.getAsLong());
            }
            states.put(bucket, fromStore);
            return fromStore;
        }
        return memory;
    }

    private void storeSave(String bucket, State state, long now) {
        if (store == null) {
            return;
        }
        try {
            store.save(scope, bucket, new Attempts(state.failures, state.lastFailure, state.delayed, state.blockedUntil), now);
        } catch (RuntimeException e) {
            storeFailed(e);
        }
    }

    private void storeFailed(RuntimeException e) {
        long now = clock.getAsLong();
        if (!warnedBefore || now - lastStoreWarning > 60_000) {
            warnedBefore = true;
            lastStoreWarning = now;
            LOGGER.log(Level.WARNING, "The login delay could not use the database; it works in memory until that is fixed", e);
        }
    }
}
