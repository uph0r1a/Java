package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * TODO 5.42, in the way of 6.76: the log filter of the support zip against thousands of random log lines. Whatever way a phone number,
 * an e-mail address, a secret, a long key or a user name in a file path is written into a line, it must not come out; the ordinary
 * lines a helper needs (times, class names, short numbers, amounts) must come out exactly as they went in; and filtering twice changes
 * nothing. The filter is the one place free text can reach the zip, so this is the test that protects the owner's customers.
 * See {@link RandomCases} to replay a failure.
 */
public class SupportReportPropertyTest {

    private static final String[] BEFORE = {"INFO: ", "SEVERE: customer ", "Could not save for ", "WARNING: sent to ", "", "Order paid by "};
    private static final String[] AFTER = {" today", ".", " (retrying)", "", " in 120 ms"};

    private static String digits(Random random, int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) out.append(random.nextInt(10));
        return out.toString();
    }

    /** A Vietnamese phone number as {@code national} (10 digits with the leading 0, or a landline with area code) written one of many ways. */
    private static String phone(Random random) {
        String[] mobile = {"03", "05", "07", "08", "09"};
        String national = random.nextInt(4) == 0 ? "02" + digits(random, 1 + random.nextInt(2)) + digits(random, 8) : mobile[random.nextInt(5)] + digits(random, 8);
        if (national.length() > 11) national = national.substring(0, 11);
        String sep = new String[]{" ", ".", "-", "  "}[random.nextInt(4)];
        String body = national.substring(1);
        String written = switch (random.nextInt(7)) {
            case 0 -> national;
            case 1 -> "+84" + body;
            case 2 -> "84" + body;
            case 3 -> "+84" + sep + body.substring(0, 3) + sep + body.substring(3, 6) + sep + body.substring(6);
            case 4 -> national.substring(0, 4) + sep + national.substring(4, 7) + sep + national.substring(7);
            case 5 -> national.substring(0, 3) + sep + national.substring(3, 7) + sep + national.substring(7);
            default -> "(" + national.substring(0, 3) + ")" + sep + national.substring(3, 7) + sep + national.substring(7);
        };
        return written;
    }

    private static String lettersAndDigits(Random random, int n, String alphabet) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return out.toString();
    }

    private static String onlyDigits(String text) {
        return text.replaceAll("\\D", "");
    }

    @Test
    public void aPhoneNumberWrittenAnyWayIsGone() {
        RandomCases.run("SupportReport.scrub, phones", 8000, random -> {
            String number = phone(random);
            String before = BEFORE[random.nextInt(BEFORE.length)];
            String line = before + number + AFTER[random.nextInt(AFTER.length)];
            String out = SupportReport.scrub(line);
            String last7 = onlyDigits(number);
            last7 = last7.substring(last7.length() - 7);
            assertFalse("the last seven digits of '" + number + "' are still in: " + out, onlyDigits(out).contains(last7));
            assertTrue("the words before the number are kept: " + out, out.startsWith(before));
        });
    }

    @Test
    public void anEmailAddressIsGone() {
        RandomCases.run("SupportReport.scrub, e-mail", 4000, random -> {
            String local = lettersAndDigits(random, 3 + random.nextInt(10), "abcdefghijklmnopqrstuvwxyz0123456789._+-");
            String host = lettersAndDigits(random, 3 + random.nextInt(8), "abcdefghijklmnopqrstuvwxyz") + "." + new String[]{"com", "vn", "com.vn", "org"}[random.nextInt(4)];
            String email = local + "@" + host;
            String out = SupportReport.scrub("sent to " + email + " today");
            assertFalse("'" + email + "' is still in: " + out, out.contains(host) || out.contains("@"));
        });
    }

    @Test
    public void theValueAfterASecretWordIsGoneWhateverTheSeparatorAndQuotes() {
        String[] words = {"password", "Password", "PASSWD", "pin", "PIN", "secret", "token", "hash", "salt", "passphrase", "api_key", "apikey", "authorization", "m\u1eadt kh\u1ea9u", "m\u00e3 PIN"};
        String[] separators = {"=", ": ", " = ", ":", "\": \"", "\"=\"", "': '"};
        RandomCases.run("SupportReport.scrub, secrets", 6000, random -> {
            String value = "X" + lettersAndDigits(random, 4 + random.nextInt(16), "abcdefghijklmnopqrstuvwxyz0123456789@#%&*!");
            String line = "login " + words[random.nextInt(words.length)] + separators[random.nextInt(separators.length)] + value + " failed";
            String out = SupportReport.scrub(line);
            assertFalse("the value '" + value + "' is still in: " + out, out.contains(value));
        });
    }

    @Test
    public void aLongKeyOrHashIsGoneIncludingOnesWithSlashes() {
        RandomCases.run("SupportReport.scrub, keys", 4000, random -> {
            String alphabet = random.nextBoolean() ? "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" : "0123456789abcdef";
            String key = lettersAndDigits(random, 40 + random.nextInt(80), alphabet);
            String out = SupportReport.scrub("stored " + key + " for the account");
            assertFalse("a 20-character piece of the key is still in: " + out, out.contains(key.substring(10, 30)));
        });
    }

    @Test
    public void theUserNameInAHomeFolderPathIsGone() {
        String[] names = {"alice", "Nguyen Van A", "Tr\u1ea7n Th\u1ecb B", "cashier1", "Le_Van-C", "my.name"};
        RandomCases.run("SupportReport.scrub, paths", 4000, random -> {
            String name = names[random.nextInt(names.length)];
            String path = switch (random.nextInt(4)) {
                case 0 -> "C:\\Users\\" + name + "\\AppData\\Roaming\\Lunavera\\data\\lunavera.db";
                case 1 -> "c:/users/" + name + "/AppData/Local/x.log";
                case 2 -> "/home/" + name.replace(' ', '_') + "/.lunavera/data/app.log";
                default -> "/Users/" + name.replace(' ', '_') + "/Library/Lunavera/data";
            };
            String shown = name.replace(' ', '_');
            String out = SupportReport.scrub("Could not write " + path + " (disk full)");
            for (String part : new String[]{name, shown}) {
                assertFalse("the user name '" + part + "' is still in: " + out, out.contains(part));
            }
            assertTrue("the rest of the path is kept so a helper can see what failed: " + out, out.contains("lunavera") || out.contains("Lunavera") || out.contains("x.log") || out.contains("app.log"));
        });
    }

    @Test
    public void theDataFolderAndTheHomeFolderAreHiddenInEitherSlashStyleAndAnyCapitals() {
        String[] folders = {"D:\\Lunavera\\data", "C:\\Users\\Nguyen Van A\\AppData\\Roaming\\Lunavera", "/home/shop/.lunavera-coffee", "/var/lib/lunavera/data/", "E:/Shop Data/lunavera"};
        RandomCases.run("SupportReport.hidePath", 3000, random -> {
            String folder = folders[random.nextInt(folders.length)];
            String written = switch (random.nextInt(4)) {
                case 0 -> folder;
                case 1 -> folder.replace('\\', '/');
                case 2 -> folder.replace('/', '\\');
                default -> folder.toUpperCase();
            };
            String line = "Using data folder " + written + "\\logs\\app-1.log for the log";
            String out = SupportReport.hidePath(line, folder, "[data folder]");
            assertTrue("the folder is named as words: " + out, out.contains("[data folder]"));
            assertTrue("the rest of the line is kept: " + out, out.startsWith("Using data folder ") && out.endsWith("for the log"));
            String keyPart = folder.replaceAll("^.*[\\\\/]", "");
            assertFalse("no piece of the path is left: " + out, out.toLowerCase().contains(keyPart.toLowerCase()) && !keyPart.isEmpty() && keyPart.length() > 6);
        });
        assertEquals("a drive root is too short to hide", "C:\\ is full", SupportReport.hidePath("C:\\ is full", "C:\\", "[x]"));
        assertEquals("no path, nothing to hide", "text", SupportReport.hidePath("text", null, "[x]"));
        assertEquals("only the exact folder", "see [x] and D:\\Other", SupportReport.hidePath("see D:\\Lunavera and D:\\Other", "D:\\Lunavera", "[x]"));
    }

    private static final String[] WORDS = {"Order", "paid", "total", "customer", "table", "closed", "shift", "backup", "restore", "saved", "printer", "retry",
            "database", "locked", "stock", "refund", "voucher", "loaded", "clock", "report"};

    private static String ordinaryLine(Random random) {
        StringBuilder out = new StringBuilder();
        switch (random.nextInt(4)) {
            case 0 -> out.append("Nov 0").append(1 + random.nextInt(9)).append(", 2026 ").append(1 + random.nextInt(11)).append(':').append(10 + random.nextInt(49)).append(":00 PM ");
            case 1 -> out.append("2026-11-0").append(1 + random.nextInt(9)).append(' ').append(10 + random.nextInt(13)).append(':').append(10 + random.nextInt(49)).append(":01 ");
            case 2 -> out.append("INFO: ");
            default -> out.append("\tat com.coffeeshop.coffeeshopmanagement.service.OrderService.pay(OrderService.java:").append(1 + random.nextInt(1500)).append(") ");
        }
        int n = 1 + random.nextInt(5);
        for (int i = 0; i < n; i++) {
            out.append(WORDS[random.nextInt(WORDS.length)]).append(' ');
            if (random.nextBoolean()) out.append(1 + random.nextInt(9_999_999)).append(' '); // up to seven digits: an id, a count, a size
            if (random.nextInt(4) == 0) out.append(1 + random.nextInt(99)).append('.').append(String.format("%03d", random.nextInt(1000))).append(" VND ");
        }
        return out.toString().stripTrailing();
    }

    @Test
    public void ordinaryLinesAreKeptExactlyAsTheyWere() {
        RandomCases.run("SupportReport.scrub, ordinary lines", 6000, random -> {
            String line = ordinaryLine(random);
            assertEquals(line, SupportReport.scrub(line));
        });
    }

    @Test
    public void filteringTwiceChangesNothingAndNothingEverThrows() {
        String alphabet = "abc XYZ 0123456789 +-.()@:=/\\_\"'\t\u1ea1\u0111\u00e3";
        RandomCases.run("SupportReport.scrub, idempotent", 6000, random -> {
            String line = lettersAndDigits(random, random.nextInt(300), alphabet);
            String once = SupportReport.scrub(line);
            assertEquals(once, SupportReport.scrub(once));
        });
    }

    @Test
    public void theTailAndTheCrashOfARandomLogLeakNothingEither() {
        RandomCases.run("SupportReport.scrubbedTail / lastCrash", 1500, random -> {
            List<String> lines = new ArrayList<>();
            String secretPhone = phone(random);
            String secretEmail = lettersAndDigits(random, 8, "abcdefghij") + "@shop.example.com";
            int n = 5 + random.nextInt(60);
            int crashAt = random.nextInt(n);
            for (int i = 0; i < n; i++) {
                String line = ordinaryLine(random);
                if (i == crashAt) {
                    lines.add("Nov 07, 2026 3:12:00 PM com.coffeeshop.X run");
                    lines.add("SEVERE: Unhandled error on thread main: customer " + secretPhone + " " + secretEmail);
                    lines.add("java.lang.IllegalStateException: password=hunter2xyz");
                } else if (random.nextInt(5) == 0) {
                    lines.add(line + " " + secretPhone + " " + secretEmail);
                } else {
                    lines.add(line);
                }
            }
            int max = 1 + random.nextInt(80);
            String tail = SupportReport.scrubbedTail(lines, max);
            String crash = SupportReport.lastCrash(lines);
            String last7 = onlyDigits(secretPhone);
            last7 = last7.substring(last7.length() - 7);
            for (String text : new String[]{tail, crash == null ? "" : crash}) {
                assertFalse("an e-mail is in: " + text, text.contains("@shop.example.com"));
                assertFalse("a password is in: " + text, text.contains("hunter2xyz"));
                for (String line : text.split("\n")) { // line by line: the digits of many ordinary numbers must not be joined into a "phone"
                    assertFalse("a phone is in the line '" + line + "'", onlyDigits(line).contains(last7));
                }
            }
            assertTrue("at most " + max + " lines", tail.split("\n", -1).length - 1 <= max);
        });
    }
}
