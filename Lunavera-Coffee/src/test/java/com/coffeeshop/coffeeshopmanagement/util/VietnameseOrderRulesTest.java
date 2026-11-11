package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Assume;
import org.junit.Test;

import java.text.Collator;
import java.text.Normalizer;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.84: {@link VietnameseOrder} does not ask the JDK for a Vietnamese collator, because that lives in the module
 * {@code jdk.localedata}, which a jlink image does not contain unless asked: its rules are the root rules plus the Vietnamese ones written
 * out in the class. Pure Java.
 */
public class VietnameseOrderRulesTest {

    @Test
    public void theRulesAreTheRootRulesPlusTheVietnameseOnesSoNoLocaleDataIsNeeded() {
        Collator made = VietnameseOrder.vietnameseCollator();
        assertTrue(made instanceof RuleBasedCollator);
        String root = ((RuleBasedCollator) Collator.getInstance(Locale.ROOT)).getRules();
        assertEquals(root + VietnameseOrder.VIETNAMESE_RULES, ((RuleBasedCollator) made).getRules());
    }

    @Test
    public void theNamesOfTheItemComeInOrderWithoutAnyLocaleData() {
        List<String> names = new ArrayList<>(List.of("Zung", "Đặng", "Ân", "An", "Dũng"));
        names.sort(VietnameseOrder::compare);
        assertEquals(List.of("An", "Ân", "Dũng", "Đặng", "Zung"), names);
    }

    @Test
    public void itSortsLikeTheJdksOwnVietnameseCollatorWhereThatOneIsInstalled() {
        Collator jdk = Collator.getInstance(Locale.forLanguageTag("vi"));
        Collator root = Collator.getInstance(Locale.ROOT);
        Assume.assumeTrue("the module jdk.localedata is not here, so the JDK has no Vietnamese collator to compare with",
                jdk instanceof RuleBasedCollator j && root instanceof RuleBasedCollator r && !j.getRules().equals(r.getRules()));
        jdk.setStrength(Collator.SECONDARY);
        String letters = "aăâbcdđeêghiklmnoôơpqrstuưvxyz";
        String[] tones = {"", "\u0300", "\u0309", "\u0303", "\u0301", "\u0323"};
        List<String> words = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            for (String tone : tones) {
                String text = Normalizer.normalize(c + tone, Normalizer.Form.NFC);
                words.add(text + "n");
                words.add(text.toUpperCase(Locale.ROOT) + "an");
            }
        }
        List<String> ours = new ArrayList<>(words);
        ours.sort(VietnameseOrder::compare);
        List<String> theirs = new ArrayList<>(words);
        theirs.sort(jdk);
        assertEquals("the same order as the JDK's Vietnamese collator", theirs, ours);
    }
}
