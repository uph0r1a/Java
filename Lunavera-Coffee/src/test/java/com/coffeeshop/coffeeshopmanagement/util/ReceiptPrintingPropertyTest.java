package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.RandomCases;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.AfterPayment;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.Choice;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.Kind;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.Paper;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.Target;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting.Where;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * TODO 5.39, in the way of 6.76: what a computer chose for printing receipts, and which printer a print goes to, for random settings and
 * random lists of installed printers (names with other capitals, spaces, control characters, very long ones; no default; none installed).
 * Whatever is typed or edited by hand, the choice that comes out makes sense, a print never goes to a printer the computer does not have,
 * and a roll is never laid out zero wide. See {@link RandomCases} to replay a failure.
 */
public class ReceiptPrintingPropertyTest {

    private String realDataDir;
    private Path sandbox;

    @Before
    public void ownDataFolder() throws IOException {
        realDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        sandbox = Files.createTempDirectory("receipt-printing-");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, sandbox.resolve("data").toString());
    }

    @After
    public void putBack() {
        if (realDataDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, realDataDir);
    }

    private static String randomName(Random random) {
        return switch (random.nextInt(8)) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "   ";
            case 3 -> "EPSON TM-T20II";
            case 4 -> "  Epson tm-t20ii \t";
            case 5 -> "Bad\nName\u0000With\u2028Controls";
            case 6 -> "X".repeat(RandomCases.between(random, 150, 400));
            default -> "Printer " + random.nextInt(5);
        };
    }

    private static <E> E pick(Random random, E[] values) {
        return random.nextInt(8) == 0 ? null : values[random.nextInt(values.length)];
    }

    private static Choice randomChoice(Random random) {
        return new Choice(pick(random, Where.values()), randomName(random), pick(random, Paper.values()), pick(random, AfterPayment.values()),
                RandomCases.between(random, -50, 50));
    }

    @Test
    public void whateverIsTypedTheChoiceMakesSenseAndStaysTheSameWhenMadeAgain() {
        RandomCases.run("ReceiptPrinting.Choice", 5000, random -> {
            Choice c = randomChoice(random);
            assertTrue("copies 1 to " + ReceiptPrinting.MAX_COPIES + ": " + c, c.copies() >= 1 && c.copies() <= ReceiptPrinting.MAX_COPIES);
            assertNotNull(c.where());
            assertNotNull(c.paper());
            assertNotNull(c.after());
            assertNotNull(c.printer());
            assertEquals("no outer spaces", c.printer(), c.printer().strip());
            assertTrue("no control characters in a printer name", c.printer().chars().noneMatch(ch -> Character.isISOControl(ch)));
            assertTrue("a printer name is cut at " + ReceiptPrinting.MAX_NAME, c.printer().length() <= ReceiptPrinting.MAX_NAME);
            if (c.where() == Where.NAMED) assertFalse("a named printer has a name: " + c, c.printer().isEmpty());
            if (c.where() == Where.ASK) assertEquals("printing at once needs a printer that is not asked for each time", AfterPayment.ASK, c.after());
            assertEquals(c.where() != Where.ASK, c.direct());
            if (c.automatic()) assertTrue(c.direct());
            assertEquals("putting a right choice right changes nothing", c,
                    new Choice(c.where(), c.printer(), c.paper(), c.after(), c.copies()));
        });
    }

    private static List<String> randomPrinters(Random random) {
        List<String> installed = new ArrayList<>();
        int n = random.nextInt(5);
        for (int i = 0; i < n; i++) {
            String name = randomName(random);
            installed.add(name); // may be null or blank: a driver can report anything
        }
        return installed;
    }

    @Test
    public void aPrintNeverGoesToAPrinterTheComputerDoesNotHave() {
        RandomCases.run("ReceiptPrinting.decide", 8000, random -> {
            Choice c = randomChoice(random);
            List<String> installed = random.nextInt(10) == 0 ? null : randomPrinters(random);
            String defaultName = random.nextInt(3) == 0 ? null : randomName(random);
            boolean force = random.nextInt(4) == 0;
            Target t = ReceiptPrinting.decide(c, installed, defaultName, force);
            assertNotNull(t);
            assertNotNull(t.kind());
            assertNotNull(t.printer());

            if (force || c.where() == Where.ASK) {
                assertEquals("the button \"Print with options\" and \"ask\" both open the dialog", Kind.DIALOG, t.kind());
                return;
            }
            if (c.where() == Where.DEFAULT) {
                boolean has = defaultName != null && !defaultName.strip().isEmpty();
                assertEquals(has ? Kind.DEFAULT_PRINTER : Kind.DIALOG_PRINTER_MISSING, t.kind());
                if (has) assertEquals(defaultName, t.printer());
                return;
            }
            // a named printer: found as typed, else without capitals and outer spaces, else missing
            List<String> names = installed == null ? List.of() : installed;
            boolean exact = names.stream().anyMatch(x -> x != null && x.equals(c.printer()));
            boolean loose = names.stream().anyMatch(x -> x != null && x.strip().equalsIgnoreCase(c.printer().strip()));
            if (exact || loose) {
                assertEquals(Kind.NAMED_PRINTER, t.kind());
                assertTrue("the printer used is one the computer reported: '" + t.printer() + "' in " + names, names.contains(t.printer()));
                if (exact) assertEquals("an exact name wins over a loose one", c.printer(), t.printer());
            } else {
                assertEquals(Kind.DIALOG_PRINTER_MISSING, t.kind());
                assertEquals("the message names what was asked for", c.printer(), t.printer());
            }
        });
    }

    @Test
    public void aRollIsNeverLaidOutZeroWideOrWiderThanThePrinterCanPrint() {
        RandomCases.run("ReceiptPrinting.rollWidth", 8000, random -> {
            Paper paper = pick(random, Paper.values());
            double printable = switch (random.nextInt(6)) {
                case 0 -> 0;
                case 1 -> -RandomCases.between(random, 1, 100);
                case 2 -> Double.NaN;
                case 3 -> RandomCases.between(random, 1, 100);
                default -> RandomCases.between(random, 100, 800);
            };
            double width = ReceiptPrinting.rollWidth(paper, printable);
            if (paper == null || paper == Paper.AUTO) {
                assertEquals("as the printer says: what it says", Double.doubleToLongBits(printable), Double.doubleToLongBits(width));
                return;
            }
            assertTrue("a 58 or 80 mm roll is never zero wide (printer said " + printable + "): " + width, width > 0);
            assertTrue("never wider than the roll", width <= paper.points() + 1e-9);
            if (printable > 0) assertTrue("never wider than the printer can print", width <= printable + 1e-9);
            assertTrue("a fixed paper always means a roll", ReceiptPrinting.useRoll(paper, printable, 200));
        });
    }

    @Test
    public void aPrinterThatReportsNoWidthGivesTheRollsOwnWidth() {
        assertEquals(Paper.MM58.points(), ReceiptPrinting.rollWidth(Paper.MM58, 0), 1e-9);
        assertEquals(Paper.MM80.points(), ReceiptPrinting.rollWidth(Paper.MM80, -5), 1e-9);
        assertEquals(Paper.MM80.points(), ReceiptPrinting.rollWidth(Paper.MM80, Double.NaN), 1e-9);
        assertEquals("as the printer says stays as it says", 0.0, ReceiptPrinting.rollWidth(Paper.AUTO, 0), 1e-9);
    }

    @Test
    public void whatIsKeptInTheSettingsFileIsAlwaysAChoiceThatMakesSenseAndSavingItAgainChangesNothing() {
        RandomCases.run("ReceiptPrinting.load", 400, random -> {
            String[] garbage = {"", " ", "ASK", "default", "NAMED", "named ", "nonsense", "AUTO", "mm58", "MM80", "5", "-3", "99", "1e3", "x", "\u0000"};
            UiSettings.put(ReceiptPrinting.KEY_WHERE, garbage[random.nextInt(garbage.length)]);
            String printer = randomName(random);
            UiSettings.put(ReceiptPrinting.KEY_PRINTER, printer == null ? "" : printer); // a settings file holds text, never null
            UiSettings.put(ReceiptPrinting.KEY_PAPER, garbage[random.nextInt(garbage.length)]);
            UiSettings.put(ReceiptPrinting.KEY_AFTER, garbage[random.nextInt(garbage.length)]);
            UiSettings.put(ReceiptPrinting.KEY_COPIES, garbage[random.nextInt(garbage.length)]);
            Choice c = ReceiptPrinting.load();
            assertNotNull(c);
            assertTrue(c.copies() >= 1 && c.copies() <= ReceiptPrinting.MAX_COPIES);
            if (c.where() == Where.NAMED) assertFalse(c.printer().isEmpty());
            if (c.where() == Where.ASK) assertEquals(AfterPayment.ASK, c.after());
            assertFalse("saving what was just loaded changes nothing", ReceiptPrinting.save(c));
            assertEquals("and loading it again gives the same", c, ReceiptPrinting.load());
        });
    }
}
