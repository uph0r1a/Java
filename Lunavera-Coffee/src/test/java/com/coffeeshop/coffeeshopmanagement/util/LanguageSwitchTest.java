package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 1.7: the decision behind the in-app language switch, without JavaFX. */
public class LanguageSwitchTest {

    private final String languageAtStart = I18n.current();

    @Before
    public void startInVietnamese() {
        I18n.setLanguage(I18n.VI);
    }

    @After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageAtStart);
    }

    private static final class FakeWork implements ScreenWork {
        final List<String> lost;
        Object resumedWith;
        FakeWork(List<String> lost) { this.lost = lost; }
        @Override public List<String> unsavedWork() { return lost; }
        @Override public Object snapshot() { return "table 5"; }
        @Override public void resume(Object snapshot) { resumedWith = snapshot; }
    }

    @Test
    public void aScreenWithoutWorkSwitchesAtOnce() {
        List<String> calls = new ArrayList<>();
        boolean changed = LanguageSwitch.change(I18n.EN, null, l -> false, () -> { calls.add("reload"); return null; }, I18n::setLanguage);
        assertTrue(changed);
        assertEquals(I18n.EN, I18n.current());
        assertEquals(List.of("reload"), calls);
    }

    @Test
    public void theLanguageAlreadyInUseOrAnUnknownOneChangesNothing() {
        List<String> calls = new ArrayList<>();
        assertFalse(LanguageSwitch.change(I18n.VI, null, l -> true, () -> { calls.add("reload"); return null; }, I18n::setLanguage));
        assertFalse(LanguageSwitch.change("fr", null, l -> true, () -> { calls.add("reload"); return null; }, I18n::setLanguage));
        assertFalse(LanguageSwitch.change(null, null, l -> true, () -> { calls.add("reload"); return null; }, I18n::setLanguage));
        assertTrue(calls.isEmpty());
        assertEquals(I18n.VI, I18n.current());
    }

    @Test
    public void saidNoToLosingWorkChangesNothingAndRemembersNothing() {
        List<String> remembered = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        boolean changed = LanguageSwitch.change(I18n.EN, new FakeWork(List.of("cart")), l -> false,
                () -> { calls.add("reload"); return null; }, code -> { remembered.add(code); return I18n.setLanguage(code); });
        assertFalse(changed);
        assertTrue(remembered.isEmpty());
        assertTrue(calls.isEmpty());
        assertEquals(I18n.VI, I18n.current());
    }

    @Test
    public void saidYesLoseTheWorkAndTheNewScreenResumesFromTheSnapshot() {
        FakeWork next = new FakeWork(List.of());
        List<List<String>> asked = new ArrayList<>();
        boolean changed = LanguageSwitch.change(I18n.EN, new FakeWork(List.of("cart")), l -> { asked.add(l); return true; },
                () -> next, I18n::setLanguage);
        assertTrue(changed);
        assertEquals(List.of(List.of("cart")), asked);
        assertEquals("table 5", next.resumedWith);
        assertEquals(I18n.EN, I18n.current());
    }

    @Test
    public void nothingToLoseMeansNoQuestion() {
        FakeWork next = new FakeWork(List.of());
        boolean changed = LanguageSwitch.change(I18n.EN, new FakeWork(List.of()), l -> { fail("asked"); return false; },
                () -> next, I18n::setLanguage);
        assertTrue(changed);
        assertEquals("table 5", next.resumedWith);
    }

    @Test
    public void aLanguageThatCannotBeSavedChangesNothing() {
        List<String> calls = new ArrayList<>();
        assertFalse(LanguageSwitch.change(I18n.EN, null, l -> true, () -> { calls.add("reload"); return null; }, code -> false));
        assertTrue(calls.isEmpty());
    }

    @Test
    public void theQuestionListsEveryLineInTheLanguageInUse() {
        String vi = LanguageSwitch.lossMessage(List.of("a", "b"));
        assertTrue(vi.contains("a") && vi.contains("b"));
        I18n.setLanguage(I18n.EN);
        String en = LanguageSwitch.lossMessage(List.of("a", "b"));
        assertNotEquals(vi, en);
        assertTrue(en.contains("Change the language anyway?"));
    }
}
