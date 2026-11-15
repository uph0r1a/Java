package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.14: how many database calls the screens still make on the screen thread. A call counts when a screen class calls a DAO, or a
 * method of a service that reaches the database (list below), and the call is not inside the first argument of
 * {@code Async.run} / {@code Async.runOrdered} / {@code inBackground} (the part that runs in the background) or inside a {@code new Thread(...)}. The test reads
 * the source files, like {@code ScreensStayAwayFromTheDatabaseTest}, and compares each screen with {@link #STILL_ON_SCREEN_THREAD}.
 *
 * <p>The list can only go down: a screen with more calls than the list says fails (move the call into {@code Async.run}, or, if it
 * truly has to stay, say why in {@code TODO.md} and raise the number), and a screen with fewer fails too, so the list is lowered and
 * stays true. It is a text scan, not a proof: a call through a variable of an unknown type, or through a method added to a service
 * after the list below was made, is not seen.
 */
public class DatabaseOffTheScreenThreadTest {

    private static final Path MAIN = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement");

    /** Service methods that reach the database (or a file the database lives in), by class. Every method of a DAO counts. */
    private static final Map<String, Set<String>> SERVICE_DB_METHODS = Map.ofEntries(
            Map.entry("AccountService", Set.of("create", "findAllEmployees", "findEmployee", "setLocked", "update")),
            Map.entry("AppFlags", Set.of("clear", "has", "mark")),
            Map.entry("AuditService", Set.of("count", "find")),
            Map.entry("AuthenticationService", Set.of("login")),
            Map.entry("AutoPromotionService", Set.of("evaluate", "preview")),
            Map.entry("BackupService", Set.of("cancelPendingRestore", "createBackup", "createBeforeCleanupBackup", "createEncryptedBackup", "inspect", "isEncryptedBackup", "pendingSince", "scheduleRestore")),
            Map.entry("CategoryService", Set.of("countProductsByCategory", "delete", "findAll", "move", "productCount", "save")),
            Map.entry("CheckoutService", Set.of("pay", "paySplit")),
            Map.entry("ComplimentaryService", Set.of("cupsGivenToday", "staffToChoose")),
            Map.entry("CustomerMergeService", Set.of("anonymise", "impact", "merge")),
            Map.entry("CustomerService", Set.of("currentPoints", "figures", "findAll", "findById", "findByPhone", "save", "search")),
            Map.entry("ExpenseService", Set.of("add", "delete", "find")),
            Map.entry("GiftCardService", Set.of("findAll", "findByCode", "openBalance", "sell", "usesForOrder", "voidCard")),
            Map.entry("IngredientService", Set.of("countLow", "delete", "findAll", "findLow", "insert", "receive", "recipeCostOf", "recipeOf", "saveRecipe", "setCounted", "update", "useRecipeCostAsProductCost")),
            Map.entry("OrderHistoryService", Set.of("countHistory", "exportOrders", "findHistoryPage", "findItemsByOrderId")),
            Map.entry("OrderService", Set.of("autosave", "lastOrderOf", "load", "start")),
            Map.entry("PasswordChangeService", Set.of("change")),
            Map.entry("PinService", Set.of("cashiersWithPin", "hasPin", "removeOwnPin", "setPin", "signInByPin", "unlockWithPassword")),
            Map.entry("PosService", Set.of("markSeated", "menu", "productsForSale", "stockShortages", "tableMap")),
            Map.entry("ProductOptionService", Set.of("copyToCategory", "findAllActive", "findByProduct", "save")),
            Map.entry("ProductService", Set.of("countUsingImage", "deactivate", "delete", "existsByNameInCategory", "findAll", "findById", "insertAll", "move", "orderReferences", "save")),
            Map.entry("PromotionService", Set.of("delete", "findAll", "save", "setActive")),
            Map.entry("ReceiptPrintService", Set.of("recordPrint")),
            Map.entry("RefundService", Set.of("forOrder", "load", "refund")),
            Map.entry("ReportService", Set.of("allProducts", "categorySales", "channelSales", "dailySales", "discountsByCashier", "employeeSales", "expenses", "optionSales", "paidOrders", "paymentSplit", "profit", "revenueBook", "summary", "waste")),
            Map.entry("ReservationService", Set.of("add", "close", "closeBefore", "closeStale", "findWaiting", "problemFor")),
            // SessionGuard reads the account again, from the database
            Map.entry("SessionGuard", Set.of("validateNow")),
            Map.entry("ShiftService", Set.of("cashOut", "close", "currentShiftStart", "findRecent", "giftCardsOpenBalance", "lastCloseEnd", "lastCountedCash", "openOrders", "paymentSplit", "sheetExtras", "summarize")),
            Map.entry("ShopSettings", Set.of("saveAll", "setName")),
            Map.entry("StaffService", Set.of("hasHistory", "makeAnonymous", "remove", "save", "setWorking")),
            Map.entry("StartSellingService", Set.of("preview", "start")),
            Map.entry("StockService", Set.of("findRecent", "ofIngredient", "ofProduct", "receive", "writeOff")),
            Map.entry("TableService", Set.of("add", "findAll", "markEmpty", "markOccupied", "remove", "update")),
            Map.entry("TimeClockService", Set.of("addMissing", "clockIn", "clockOut", "clockOutFor", "closeForgotten", "correct", "findBetween", "findClosedBetween", "findOpen", "hours", "openFor", "salaries")),
            Map.entry("VoucherService", Set.of("add", "findAll", "findByCode", "setActive"))
    );

    /**
     * Screens that still make calls on the screen thread, and how many (70 calls in 30 screens when this list was made). Each is a place to move into {@code Async.run}, except the
     * ones TODO 6.14 names as staying on purpose: the saves in the OK-button filters of the add/edit boxes, which must decide at once
     * whether to keep the box open.
     */
    private static final Map<String, Integer> STILL_ON_SCREEN_THREAD = Map.ofEntries(
            Map.entry("AccountController", 2),
            Map.entry("AdminScreen", 1),
            Map.entry("CategoryDialog", 1),
            Map.entry("ComplimentaryDialog", 2), // the count of cups given, in the OK-button filter (it must decide at once) and in the hint under the choice
            Map.entry("CustomerController", 1),
            Map.entry("ExpenseDialog", 2),
            Map.entry("FirstRunDialog", 1),
            Map.entry("LockScreen", 4),
            Map.entry("OrderController", 1),
            Map.entry("OrderHistoryWindow", 1),
            Map.entry("PasswordChangeDialog", 1),
            Map.entry("PinSetupDialog", 2),
            Map.entry("ProductController", 2),
            Map.entry("PromotionDialog", 1),
            Map.entry("ReportWindow", 1),
            Map.entry("SetupWizard", 5),
            Map.entry("ShiftCloseDialog", 1),
            Map.entry("StaffController", 1),
            Map.entry("VoucherDialog", 1)
    );

    // ------------------------------------------------------------------ the scan

    /** Which lines were counted, by screen (for the message of a failure and for the hand-run main). */
    static final Map<String, List<String>> WHERE = new TreeMap<>();

    static Map<String, Integer> counts() throws IOException {
        Set<String> daos = new java.util.HashSet<>();
        try (Stream<Path> files = Files.list(MAIN.resolve("dao"))) {
            files.forEach(f -> {
                String n = f.getFileName().toString();
                if (n.endsWith("DAO.java")) daos.add(n.replace(".java", ""));
            });
        }
        Map<String, Integer> result = new TreeMap<>();
        offScreen = new HashMap<>();
        try (Stream<Path> files = Files.list(MAIN.resolve("controller"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java")).sorted()::iterator) {
                collectMarked(file.getFileName().toString().replace(".java", ""), strip(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            }
        }
        try (Stream<Path> files = Files.list(MAIN.resolve("controller"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java")).sorted()::iterator) {
                currentClass = file.getFileName().toString().replace(".java", "");
                int n = scan(strip(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)), daos);
                if (n > 0) {
                    result.put(file.getFileName().toString().replace(".java", ""), n);
                    WHERE.put(file.getFileName().toString().replace(".java", ""), new ArrayList<>(LAST_HITS));
                }
            }
        }
        return result;
    }

    private static boolean isDb(String type, String method, Set<String> daos) {
        if (type == null || method.isEmpty() || Character.isUpperCase(method.charAt(0))) return false; // a record or enum made by name
        if (daos.contains(type)) return true;
        Set<String> methods = SERVICE_DB_METHODS.get(type);
        return methods != null && methods.contains(method);
    }

    /** Methods marked {@code @OffScreenThread}, by class name (TODO 6.14): their bodies are background, their callers must be too. */
    static Map<String, Set<String>> offScreen = new HashMap<>();

    private static final Pattern MARKED = Pattern.compile(
            "@(?:[\\w.]+\\.)?OffScreenThread\\s+(?:(?:public|private|protected|static|final)\\s+)*[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*\\(");

    /** Collects the marked methods of every class in {@code controller/} before any screen is scanned. */
    static void collectMarked(String className, String stripped) {
        Matcher m = MARKED.matcher(stripped);
        while (m.find()) offScreen.computeIfAbsent(className, k -> new java.util.HashSet<>()).add(m.group(1));
    }

    /** For a person reading a failure: the lines the last scan counted. */
    static final List<String> LAST_HITS = new ArrayList<>();

    /** The class being scanned (for unqualified calls to its marked methods). */
    static String currentClass = "";

    private static int scan(String s, Set<String> daos) {
        LAST_HITS.clear();
        List<int[]> background = new ArrayList<>();
        // the body of a method marked @OffScreenThread is background, and the mark itself and the declaration are not calls
        List<int[]> declarations = new ArrayList<>();
        Matcher marked = MARKED.matcher(s);
        while (marked.find()) {
            int paramsOpen = marked.end() - 1;
            int paramsClose = closing(s, paramsOpen);
            int bodyOpen = s.indexOf('{', paramsClose);
            int bodyClose = braceClosing(s, bodyOpen);
            background.add(new int[]{bodyOpen, bodyClose});
            declarations.add(new int[]{marked.start(), bodyOpen});
        }
        Matcher bg = Pattern.compile("\\b(?:Async\\s*\\.\\s*(?:run|runOrdered)|inBackground|new\\s+Thread)\\s*\\(").matcher(s);
        while (bg.find()) {
            int open = bg.end() - 1;
            int close = closing(s, open);
            background.add(!bg.group().startsWith("new") ? new int[]{open + 1, firstArgumentEnd(s, open, close)} : new int[]{open + 1, close});
        }
        Map<String, String> typeOf = new HashMap<>();
        Matcher decl = Pattern.compile("\\b([A-Z]\\w*)\\s+(\\w+)\\s*(?:=|;|,|\\))").matcher(s);
        while (decl.find()) typeOf.put(decl.group(2), decl.group(1));
        int count = 0;
        Matcher call = Pattern.compile("\\b(\\w+)\\s*\\.\\s*(\\w+)\\s*\\(").matcher(s);
        while (call.find()) {
            String type = typeOf.getOrDefault(call.group(1), call.group(1));
            if (isDb(type, call.group(2), daos) && !inside(background, call.start())) {
                count++;
                LAST_HITS.add("line " + lineOf(s, call.start()) + ": " + call.group(1) + "." + call.group(2));
            }
        }
        Matcher inline = Pattern.compile("\\bnew\\s+(\\w+)\\s*\\([^()]*\\)\\s*\\.\\s*(\\w+)\\s*\\(").matcher(s);
        while (inline.find()) {
            if (isDb(inline.group(1), inline.group(2), daos) && !inside(background, inline.start())) {
                count++;
                LAST_HITS.add("line " + lineOf(s, inline.start()) + ": new " + inline.group(1) + "()." + inline.group(2));
            }
        }
        for (Map.Entry<String, Set<String>> e : offScreen.entrySet()) {
            for (String method : e.getValue()) {
                String own = e.getKey().equals(currentClass) ? "(?:\\b(?<![.\\w])|\\b" + e.getKey() + "\\s*\\.\\s*)" : "\\b" + e.getKey() + "\\s*\\.\\s*";
                Matcher use = Pattern.compile(own + method + "\\s*\\(").matcher(s);
                while (use.find()) {
                    if (!inside(background, use.start()) && !inside(declarations, use.start())) {
                        count++;
                        LAST_HITS.add("line " + lineOf(s, use.start()) + ": " + e.getKey() + "." + method + " (marked @OffScreenThread)");
                    }
                }
            }
        }
        return count;
    }

    private static int braceClosing(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}' && --depth == 0) return i;
        }
        return s.length() - 1;
    }

    private static int lineOf(String s, int at) {
        int line = 1;
        for (int i = 0; i < at; i++) if (s.charAt(i) == '\n') line++;
        return line;
    }

    private static boolean inside(List<int[]> spans, int at) {
        for (int[] span : spans) if (span[0] <= at && at < span[1]) return true;
        return false;
    }

    private static int closing(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')' && --depth == 0) return i;
        }
        return s.length() - 1;
    }

    private static int firstArgumentEnd(String s, int open, int close) {
        int depth = 0;
        for (int i = open; i <= close; i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 1) return i;
        }
        return close;
    }

    /** The source with comments and the inside of string and character literals blanked out (same length, same line breaks). */
    static String strip(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (src.startsWith("//", i)) {
                int j = src.indexOf('\n', i);
                j = j < 0 ? n : j;
                out.append(" ".repeat(j - i));
                i = j;
            } else if (src.startsWith("/*", i)) {
                int j = src.indexOf("*/", i);
                j = j < 0 ? n : j + 2;
                for (int k = i; k < j; k++) out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                i = j;
            } else if (src.startsWith("\"\"\"", i)) {
                int j = src.indexOf("\"\"\"", i + 3);
                j = j < 0 ? n : j + 3;
                for (int k = i; k < j; k++) out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c) j += src.charAt(j) == '\\' ? 2 : 1;
                out.append(c).append(" ".repeat(Math.max(0, Math.min(j, n) - i - 1))).append(c);
                i = j + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ the checks

    @Test
    public void noScreenMakesMoreDatabaseCallsOnTheScreenThreadThanTheListSays() throws IOException {
        Map<String, Integer> now = counts();
        List<String> worse = new ArrayList<>();
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            int allowed = STILL_ON_SCREEN_THREAD.getOrDefault(e.getKey(), 0);
            if (e.getValue() > allowed) worse.add(e.getKey() + " " + e.getValue() + " (allowed " + allowed + ") " + WHERE.get(e.getKey()));
        }
        assertTrue("these screens call the database on the screen thread more than the list allows; do the call inside Async.run: " + worse,
                worse.isEmpty());
    }

    @Test
    public void theListIsLoweredWhenACallMovesOffTheScreenThread() throws IOException {
        Map<String, Integer> now = counts();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Integer> e : STILL_ON_SCREEN_THREAD.entrySet()) {
            int found = now.getOrDefault(e.getKey(), 0);
            if (found < e.getValue()) stale.add(e.getKey() + " now " + found + " (list says " + e.getValue() + ")");
        }
        assertTrue("good: these screens have fewer calls on the screen thread; lower their numbers in STILL_ON_SCREEN_THREAD (remove the line at 0): "
                + stale, stale.isEmpty());
    }

    @Test
    public void theMovedDialogsStayMoved() throws IOException {
        // the dialogs finished under TODO 6.14 keep no list or save on the screen thread; the account check that opens an admin window
        // (SessionGuard.validateNow) is the one call they still make, and has its own line in the note of TODO 6.14
        counts();
        for (String done : List.of("VoucherDialog", "IngredientDialog", "PromotionDialog", "StockLogDialog", "ExpenseDialog")) {
            List<String> others = new ArrayList<>(WHERE.getOrDefault(done, List.of()));
            others.removeIf(hit -> hit.endsWith("SessionGuard.validateNow"));
            if (done.equals("ExpenseDialog")) { // the cash-out box is an OK-button filter: it must decide at once, so its save stays
                assertEquals(done + " keeps only the cash-out save: " + others, 1, others.size());
            } else {
                assertTrue(done + " reads and writes the database only in the background: " + others, others.isEmpty());
            }
        }
    }

    @Test
    public void theScannerSeesACallOnTheScreenThreadAndNotOneInsideAsyncRun() {
        Set<String> daos = Set.of("ThingDAO");
        String onScreen = "void a(){ ThingDAO dao = new ThingDAO(); dao.findAll(); }";
        String inside = "void a(){ ThingDAO dao = new ThingDAO(); Async.run(() -> dao.findAll(), r -> show(r), e -> fail(e)); }";
        String inCallback = "void a(){ ThingDAO dao = new ThingDAO(); Async.run(() -> 1, r -> dao.findAll(), e -> fail(e)); }";
        String inline = "void a(){ new ThingDAO().findAll(); }";
        String ignored = "void a(){ /* dao.findAll(); */ String s = \"dao.findAll()\"; }";
        assertEquals(1, scan(strip(onScreen), daos));
        assertEquals(0, scan(strip(inside), daos));
        assertEquals("the callback runs on the screen thread", 1, scan(strip(inCallback), daos));
        assertEquals(1, scan(strip(inline), daos));
        assertEquals(0, scan(strip(ignored), daos));
    }

    @Test
    public void aMethodMarkedOffScreenThreadIsBackgroundAndItsCallersMustBeToo() {
        Set<String> daos = Set.of("ThingDAO");
        String source = "class X { @OffScreenThread private static int count(){ return new ThingDAO().count(); }"
                + " void ok(){ Async.run(() -> count(), r -> show(r), e -> fail(e)); }"
                + " void bad(){ int n = count(); } }";
        Map<String, Set<String>> before = offScreen;
        String beforeClass = currentClass;
        try {
            offScreen = new HashMap<>();
            currentClass = "X";
            collectMarked("X", strip(source));
            assertEquals(Set.of("count"), offScreen.get("X"));
            assertEquals("only the call outside Async.run counts; the body and the declaration do not", 1, scan(strip(source), daos));
            String other = "class Y { void bad(){ X.count(); } void ok(){ Async.run(() -> X.count(), r -> show(r), e -> fail(e)); } }";
            currentClass = "Y";
            assertEquals("a call from another class is seen too", 1, scan(strip(other), daos));
        } finally {
            offScreen = before;
            currentClass = beforeClass;
        }
    }

    /** Prints the counts in the form of the list; run by hand when the list has to be made again. */
    public static void main(String[] args) throws IOException {
        counts().forEach((k, v) -> System.out.println("Map.entry(\"" + k + "\", " + v + "), // " + WHERE.get(k)));
    }
}
