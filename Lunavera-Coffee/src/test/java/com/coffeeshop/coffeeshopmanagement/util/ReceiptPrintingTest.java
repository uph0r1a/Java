package com.coffeeshop.coffeeshopmanagement.util;

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
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 5.39: printing a receipt without the dialog. What the computer chose is put right whatever was typed or edited, the decision
 * (dialog, default printer, named printer, a printer that is gone) is made from plain lists of names, and the roll width follows the
 * paper. No JavaFX: the printers themselves are asked by {@code HoaDonController}.
 */
public class ReceiptPrintingTest {

    private Path sandbox;
    private String realDataDir;

    @Before
    public void useAnEmptyDataFolder() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("receiptprinting");
        System.setProperty("lunavera.dataDir", sandbox.resolve("data").toString());
    }

    @After
    public void restore() {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
    }

    private static final List<String> PRINTERS = List.of("EPSON TM-T82", "Microsoft Print to PDF", "XP-58");

    @Test
    public void nothingSavedMeansTheDialogEveryTimeAsBefore() {
        Choice choice = ReceiptPrinting.load();
        assertEquals(ReceiptPrinting.ASK_EVERY_TIME, choice);
        assertFalse(choice.direct());
        assertFalse(choice.automatic());
        assertEquals(1, choice.copies());
    }

    @Test
    public void aChoiceIsAlwaysPutRight() {
        assertEquals("a named printer with no name is a question", Where.ASK, new Choice(Where.NAMED, "   ", Paper.AUTO, AfterPayment.ASK, 1).where());
        assertEquals("printing at once needs a printer that is not asked for", AfterPayment.ASK,
                new Choice(Where.ASK, "", Paper.AUTO, AfterPayment.AUTO, 1).after());
        assertEquals(AfterPayment.AUTO, new Choice(Where.DEFAULT, "", Paper.AUTO, AfterPayment.AUTO, 1).after());
        assertEquals(1, new Choice(Where.ASK, "", Paper.AUTO, AfterPayment.ASK, 0).copies());
        assertEquals(1, new Choice(Where.ASK, "", Paper.AUTO, AfterPayment.ASK, -4).copies());
        assertEquals(ReceiptPrinting.MAX_COPIES, new Choice(Where.ASK, "", Paper.AUTO, AfterPayment.ASK, 99).copies());
        Choice nulls = new Choice(null, null, null, null, 2);
        assertEquals(Where.ASK, nulls.where());
        assertEquals("", nulls.printer());
        assertEquals(Paper.AUTO, nulls.paper());
    }

    @Test
    public void aPrinterNameLosesControlCharactersAndIsCut() {
        assertEquals("a control character becomes a space, outer spaces go", "EPSON TM", ReceiptPrinting.cleanName("  EPSON\nTM\u0007 "));
        assertEquals("", ReceiptPrinting.cleanName(null));
        String longName = "P".repeat(500);
        assertEquals(ReceiptPrinting.MAX_NAME, new Choice(Where.NAMED, longName, Paper.AUTO, AfterPayment.ASK, 1).printer().length());
        assertFalse(new Choice(Where.NAMED, "A\nB", Paper.AUTO, AfterPayment.ASK, 1).printer().contains("\n"));
    }

    @Test
    public void askingEveryTimeOrTheOptionsButtonOpensTheDialog() {
        Choice direct = new Choice(Where.NAMED, "XP-58", Paper.AUTO, AfterPayment.ASK, 1);
        assertEquals(Kind.DIALOG, ReceiptPrinting.decide(ReceiptPrinting.ASK_EVERY_TIME, PRINTERS, "XP-58", false).kind());
        assertEquals("the button 'Print with options' always opens it", Kind.DIALOG, ReceiptPrinting.decide(direct, PRINTERS, "XP-58", true).kind());
        assertEquals(Kind.DIALOG, ReceiptPrinting.decide(null, PRINTERS, null, false).kind());
    }

    @Test
    public void theDefaultPrinterIsUsedWhenThereIsOne() {
        Choice choice = new Choice(Where.DEFAULT, "", Paper.AUTO, AfterPayment.ASK, 1);
        Target target = ReceiptPrinting.decide(choice, PRINTERS, "Microsoft Print to PDF", false);
        assertEquals(Kind.DEFAULT_PRINTER, target.kind());
        assertEquals("Microsoft Print to PDF", target.printer());
        assertEquals("no default printer: say so and open the dialog", Kind.DIALOG_PRINTER_MISSING,
                ReceiptPrinting.decide(choice, PRINTERS, null, false).kind());
        assertEquals(Kind.DIALOG_PRINTER_MISSING, ReceiptPrinting.decide(choice, PRINTERS, "  ", false).kind());
    }

    @Test
    public void aNamedPrinterIsFoundAsTypedOrWithoutCapitals() {
        Target exact = ReceiptPrinting.decide(new Choice(Where.NAMED, "XP-58", Paper.AUTO, AfterPayment.ASK, 1), PRINTERS, null, false);
        assertEquals(Kind.NAMED_PRINTER, exact.kind());
        assertEquals("XP-58", exact.printer());
        Target loose = ReceiptPrinting.decide(new Choice(Where.NAMED, "epson tm-t82", Paper.AUTO, AfterPayment.ASK, 1), PRINTERS, null, false);
        assertEquals(Kind.NAMED_PRINTER, loose.kind());
        assertEquals("the real name is used, not the typed one", "EPSON TM-T82", loose.printer());
    }

    @Test
    public void aPrinterThatIsGoneFallsBackToTheDialogAndNamesItself() {
        Target gone = ReceiptPrinting.decide(new Choice(Where.NAMED, "Old printer", Paper.AUTO, AfterPayment.AUTO, 1), PRINTERS, "XP-58", false);
        assertEquals(Kind.DIALOG_PRINTER_MISSING, gone.kind());
        assertEquals("Old printer", gone.printer());
        assertEquals(Kind.DIALOG_PRINTER_MISSING, ReceiptPrinting.decide(new Choice(Where.NAMED, "X", Paper.AUTO, AfterPayment.ASK, 1), null, null, false).kind());
    }

    @Test
    public void aRollIsUsedForNarrowPaperOrWhenTheOwnerSaidSo() {
        assertFalse("a sheet printer reporting a wide page, nothing forced", ReceiptPrinting.useRoll(Paper.AUTO, 540, 300));
        assertTrue("a roll printer reports a narrow page", ReceiptPrinting.useRoll(Paper.AUTO, 200, 300));
        assertTrue("forced 58 mm even on a wide page", ReceiptPrinting.useRoll(Paper.MM58, 540, 300));
        assertTrue(ReceiptPrinting.useRoll(Paper.MM80, 540, 300));
        assertFalse(ReceiptPrinting.useRoll(null, 540, 300));
    }

    @Test
    public void theRollWidthFollowsThePaperButNeverPassesWhatThePrinterCanPrint() {
        assertEquals(200.0, ReceiptPrinting.rollWidth(Paper.AUTO, 200), 0.0001);
        assertEquals("58 mm is about 164 points", 164.41, ReceiptPrinting.rollWidth(Paper.MM58, 540), 0.01);
        assertEquals("80 mm is about 226.8 points", 226.77, ReceiptPrinting.rollWidth(Paper.MM80, 540), 0.01);
        assertEquals("a 58 mm roll that prints 48 mm (136 points)", 136.0, ReceiptPrinting.rollWidth(Paper.MM58, 136), 0.0001);
        assertEquals(0, Paper.AUTO.millimetres());
        assertEquals(0.0, Paper.AUTO.points(), 0.0);
    }

    @Test
    public void theChoiceIsRememberedPerComputerAndOnlyReportedWhenItChanges() {
        Choice chosen = new Choice(Where.NAMED, "XP-58", Paper.MM58, AfterPayment.AUTO, 2);
        assertFalse("the first state is the saved state: nothing changed", ReceiptPrinting.save(ReceiptPrinting.ASK_EVERY_TIME));
        assertTrue(ReceiptPrinting.save(chosen));
        assertEquals(chosen, ReceiptPrinting.load());
        assertTrue(ReceiptPrinting.load().direct());
        assertTrue(ReceiptPrinting.load().automatic());
        assertFalse("the same again", ReceiptPrinting.save(chosen));
        assertTrue(ReceiptPrinting.save(null));
        assertEquals(ReceiptPrinting.ASK_EVERY_TIME, ReceiptPrinting.load());
    }

    @Test
    public void aHandEditedFileNeverGivesANonsenseChoice() throws IOException {
        Path file = sandbox.resolve("data").resolve("ui-settings.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "print.where=NAMED\nprint.printer=\nprint.paper=MM99\nprint.after=AUTO\nprint.copies=lots\n");
        Choice choice = ReceiptPrinting.load();
        assertEquals("a named printer without a name", Where.ASK, choice.where());
        assertEquals(Paper.AUTO, choice.paper());
        assertEquals(AfterPayment.ASK, choice.after());
        assertEquals(1, choice.copies());
        assertEquals(Where.DEFAULT, ReceiptPrinting.parseWhere(" default "));
        assertEquals(Where.ASK, ReceiptPrinting.parseWhere(null));
        assertEquals(3, ReceiptPrinting.parseCopies(" 3 "));
    }
}
