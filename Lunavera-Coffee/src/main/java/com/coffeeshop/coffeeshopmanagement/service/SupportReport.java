package com.coffeeshop.coffeeshopmanagement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The text parts of the zip an administrator saves for whoever looks after the program (TODO 5.42): the facts sheet, the log lines with
 * everything that could identify a person taken out, and the last crash. Pure (no database, no files, no screen), so a test can feed
 * it a log full of names, phones and hashes and look at what is left.
 *
 * <p>What is NOT in the zip, on purpose: customer or staff data of any kind (only how many rows each table has), password hashes, PINs,
 * the bank-transfer account and picture, the security log (it names accounts), and the full path of the data folder (it may hold the
 * Windows user's name). The log lines are the one place free text can reach, so each line is filtered by {@link #scrub}.
 */
public final class SupportReport {

    /** The most log lines kept from one file: the end of it, where the trouble is. */
    public static final int LOG_TAIL_LINES = 3000;
    /** The longest crash block, in lines. */
    static final int CRASH_LINES = 80;
    static final String REMOVED = "[removed]";

    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)\\b(password|passphrase|passwd|pin|secret|token|hash|salt|api[_-]?key|authorization|m\u1eadt kh\u1ea9u|mat khau|m\u00e3 pin)\\b([\"']?\\s*[=:]\\s*)\\S+");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern LONG_OPAQUE = Pattern.compile("[A-Za-z0-9+/=$:_-]{40,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\+?\\d{8,}");
    private static final Pattern GROUPED_DIGITS = Pattern.compile("\\+?\\d{2,4}[ .-]\\d{3}[ .-]\\d{3,4}\\b");
    /**
     * A Vietnamese phone number in any grouping: a 0, or +84, or 84 followed by a separator, then 8 to 10 digits with spaces, dots, dashes
     * or brackets between them. (A plain run of eight or more digits is caught by {@link #LONG_DIGITS}; an 84 straight in front of
     * more digits is not taken for a country code, so that an id next to an amount is left alone.)
     */
    private static final Pattern PHONE_VN = Pattern.compile("(?<!\\d)(?:\\+\\s?84|\\(\\+?84\\)|84(?=[ .\\-])|0)(?:[ .\\-()]*\\d){8,10}(?!\\d)");
    /** The user name in the path of a home folder: C:\\Users\\Name, /home/name, /Users/name. */
    private static final Pattern WINDOWS_USER = Pattern.compile("(?i)([A-Z]:[\\\\/]Users[\\\\/])[^\\\\/\"'<>|:*?\\r\\n]+");
    private static final Pattern UNIX_USER = Pattern.compile("(?<![\\w.])(/home/|/Users/)[^/\\s\"']+");
    private static final Pattern RECORD_HEAD = Pattern.compile("^[A-Z][a-z]{2} \\d{1,2}, \\d{4} .*");

    private SupportReport() {
    }

    /**
     * One log line with what can identify a person removed: a value after password, PIN, secret, token or hash; e-mail addresses;
     * long opaque strings (hashes, keys); runs of eight or more digits, phone numbers written in groups and the user name in a home
     * folder path ({@code C:\Users\Name}). Ordinary words are kept:
     * the program never writes a customer's name to its log (TODO 2A.39), so the filter does not try to guess names.
     */
    public static String scrub(String line) {
        if (line == null) return "";
        String text = SECRET_PAIR.matcher(line).replaceAll("$1$2" + REMOVED);
        text = EMAIL.matcher(text).replaceAll("[email]");
        text = WINDOWS_USER.matcher(text).replaceAll("$1[user]");
        text = UNIX_USER.matcher(text).replaceAll("$1[user]");
        text = LONG_OPAQUE.matcher(text).replaceAll(REMOVED);
        text = PHONE_VN.matcher(text).replaceAll("[number]");
        text = GROUPED_DIGITS.matcher(text).replaceAll("[number]");
        text = LONG_DIGITS.matcher(text).replaceAll("[number]");
        return text;
    }

    /**
     * Every place a folder's path is written in {@code text}, in either slash style and whatever the capitals, replaced by
     * {@code replacement}: the data folder and the user's home folder may hold a Windows user name. A path shorter than four characters
     * (a drive root, {@code /}) is left alone, so that it does not blank out half of the text.
     */
    public static String hidePath(String text, String path, String replacement) {
        if (text == null || path == null) return text;
        String clean = path.strip();
        if (clean.length() < 4) return text;
        while (clean.length() > 3 && (clean.endsWith("\\") || clean.endsWith("/"))) clean = clean.substring(0, clean.length() - 1);
        String out = text;
        for (String variant : new java.util.LinkedHashSet<>(List.of(clean, clean.replace('\\', '/'), clean.replace('/', '\\')))) {
            out = Pattern.compile(Pattern.quote(variant), Pattern.CASE_INSENSITIVE).matcher(out).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
        }
        return out;
    }

    /** The last {@code max} lines of a log, each one scrubbed, as one text. */
    public static String scrubbedTail(List<String> lines, int max) {
        int from = Math.max(0, lines.size() - Math.max(1, max));
        StringBuilder out = new StringBuilder();
        for (String line : lines.subList(from, lines.size())) out.append(scrub(line)).append('\n');
        return out.toString();
    }

    /**
     * The newest crash in these lines of one log (oldest first): the record that says "Unhandled error on thread", with the date line
     * before it and the stack trace after it, up to the next record, scrubbed; or null when there is none.
     */
    public static String lastCrash(List<String> lines) {
        int at = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains("Unhandled error on thread")) {
                at = i;
                break;
            }
        }
        if (at < 0) return null;
        int start = at > 0 && RECORD_HEAD.matcher(lines.get(at - 1)).matches() ? at - 1 : at;
        int end = at + 1;
        while (end < lines.size() && end - start < CRASH_LINES && !RECORD_HEAD.matcher(lines.get(end)).matches()) end++;
        StringBuilder out = new StringBuilder();
        for (String line : lines.subList(start, end)) out.append(scrub(line)).append('\n');
        return out.toString();
    }

    /** What the facts sheet is made from. {@code tableRows} is table name to row count; {@code problem} is empty or why the database could not be read. */
    public record Facts(String program, String javaVersion, String javaVendor, String system, String language, String databaseKind,
                        int schemaVersion, Map<String, Long> tableRows, String problem, String madeAt, int logFiles) {
    }

    /** The facts sheet ({@code support-info.txt}): plain text, English, in the order a helper reads it. */
    public static String sheet(Facts facts) {
        StringBuilder out = new StringBuilder();
        out.append("Lunavera Coffee - details for support\n");
        out.append("Made: ").append(facts.madeAt()).append("\n\n");
        out.append("Program: ").append(facts.program()).append('\n');
        out.append("Java: ").append(facts.javaVersion()).append(" (").append(facts.javaVendor()).append(")\n");
        out.append("System: ").append(facts.system()).append('\n');
        out.append("Language of the screens: ").append(facts.language()).append("\n\n");
        out.append("Database: ").append(facts.databaseKind());
        out.append(facts.schemaVersion() > 0 ? ", table layout version " + facts.schemaVersion() : ", table layout version not read").append('\n');
        if (facts.problem() != null && !facts.problem().isEmpty()) out.append("Database problem: ").append(facts.problem()).append('\n');
        out.append("Rows in each table (numbers only):\n");
        if (facts.tableRows().isEmpty()) out.append("  (none could be counted)\n");
        for (Map.Entry<String, Long> row : facts.tableRows().entrySet()) {
            out.append("  ").append(row.getKey()).append(": ").append(row.getValue()).append('\n');
        }
        out.append("\nLog files included: ").append(facts.logFiles()).append(" (last ").append(LOG_TAIL_LINES).append(" lines of each, filtered), and last-crash.txt\n\n");
        out.append("NOT in this file: the contents of any table (customers, staff, orders, accounts: only the row counts above), password hashes, PINs, ");
        out.append("the bank-transfer account and picture, the security log, and the path of the data folder.\n");
        out.append("The log lines are filtered for phone numbers, e-mail addresses, long keys, user names in file paths and values after password or PIN; free text cannot be checked, so a name ");
        out.append("that the program wrote to its log by mistake would still be there.\n");
        return out.toString();
    }

    /** Table names that may go into a {@code SELECT COUNT(*)} as they are: letters, digits and underscores only. */
    public static boolean safeTableName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,64}");
    }

    /** A log file's text as lines, for {@link #scrubbedTail} and {@link #lastCrash}. */
    public static List<String> linesOf(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        for (String line : text.split("\\R", -1)) lines.add(line);
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return lines;
    }
}
