package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 6.71: the guides for the cashier and for the owner ({@code src/main/resources/help/}) and the feature list in the README stay
 * true. Reads the files as text, so it needs no JavaFX and no database. A button that is renamed in the message files, or a figure
 * that changes, fails here until the guide is fixed.
 */
public class HelpPagesTest {

    private static final Path HELP = Path.of("src", "main", "resources", "help");
    private static final Path I18N = Path.of("src", "main", "resources", "i18n");
    private static final String[] PAGES = {"cashier_vi.md", "cashier_en.md", "owner_vi.md", "owner_en.md"};
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<String> messageValues(String language) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(I18N.resolve("messages_" + language + ".properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        List<String> values = new ArrayList<>();
        for (String value : properties.stringPropertyNames().stream().map(properties::getProperty).toList()) {
            values.add(value.replaceAll("\\s+", " ").trim());
        }
        return values;
    }

    private static long sections(String page) {
        return page.lines().filter(line -> line.startsWith("## ")).count();
    }

    @Test
    public void bothGuidesExistInBothLanguages() throws IOException {
        for (String page : PAGES) {
            Path file = HELP.resolve(page);
            assertTrue("missing help page " + page, Files.isRegularFile(file));
            String text = read(file);
            assertTrue(page + " is too short to be a guide", text.length() > 1500);
            assertTrue(page + " must start with a title", text.startsWith("# "));
        }
    }

    @Test
    public void theReadmeListsTheFeaturesAndLinksEveryGuide() throws IOException {
        String readme = read(Path.of("README.md"));
        assertTrue("the feature list is missing", readme.contains("## What it does"));
        assertTrue("the feature list must come before 'Run and test'", readme.indexOf("## What it does") < readme.indexOf("## Run and test"));
        for (String page : PAGES) {
            assertTrue("README does not link " + page, readme.contains("src/main/resources/help/" + page));
        }
        // the features the README is asked to name (TODO 6.71)
        for (String feature : new String[]{"gift cards", "clock in and out", "birthday", "voucher", "table bookings", "split a bill",
                "several ways", "ingredients and recipes", "PIN lock screen", "audit log"}) {
            assertTrue("README does not mention: " + feature, readme.toLowerCase().contains(feature.toLowerCase()));
        }
    }

    @Test
    public void everyButtonNameInAGuideIsAWordOfTheScreen() throws IOException {
        for (String language : new String[]{"vi", "en"}) {
            List<String> values = messageValues(language);
            for (String role : new String[]{"cashier", "owner"}) {
                String page = role + "_" + language + ".md";
                Matcher bold = BOLD.matcher(read(HELP.resolve(page)));
                TreeSet<String> missing = new TreeSet<>();
                int count = 0;
                while (bold.find()) {
                    count++;
                    String label = bold.group(1).replaceAll("\\s+", " ").trim();
                    if (values.stream().noneMatch(value -> value.contains(label))) missing.add(label);
                }
                assertTrue(page + " names no buttons at all", count >= 20);
                assertTrue(page + " names buttons that are not on any screen (renamed?): " + missing, missing.isEmpty());
            }
        }
    }

    @Test
    public void theTwoLanguagesOfAGuideHaveTheSameSections() throws IOException {
        for (String role : new String[]{"cashier", "owner"}) {
            assertEquals(role + ": the Vietnamese and English guides must cover the same sections",
                    sections(read(HELP.resolve(role + "_vi.md"))), sections(read(HELP.resolve(role + "_en.md"))));
        }
    }

    @Test
    public void theFiguresTheCashierGuideQuotesAreTheOnesInTheCode() throws IOException {
        ShopRules.Values defaults = ShopRules.DEFAULTS;
        assertEquals("a cashier may discount up to 10% by default", 10, defaults.staffDiscountPercent());
        assertEquals("1 point per 10.000đ", 10_000, defaults.earnDong());
        assertEquals("1 point is worth 1.000đ", 1_000, defaults.spendDong());
        assertEquals("at most 50% of a bill in points", 50, defaults.maxPercentOfBill());
        assertEquals("a shift open for 16 hours counts as forgotten", 16, TimeClockRules.forgottenAfterHours());
        for (String page : new String[]{"cashier_vi.md", "cashier_en.md"}) {
            String text = read(HELP.resolve(page));
            assertTrue(page + " should say 10%", text.contains("10%"));
            assertTrue(page + " should say 50%", text.contains("50%"));
            assertTrue(page + " should say 16", text.contains("16 "));
            assertTrue(page + " should say 10.000", text.contains("10.000"));
            assertTrue(page + " should say 1.000", text.contains("1.000"));
        }
    }
}
