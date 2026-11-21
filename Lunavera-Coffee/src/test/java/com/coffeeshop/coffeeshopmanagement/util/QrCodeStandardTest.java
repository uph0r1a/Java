package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (second run, {@link QrCode}): what the decoder in {@link QrDecoder} cannot see because any mask,
 * any padding and any alignment-pattern colour still scans. These tests check the symbol against the standard (ISO/IEC 18004)
 * written out independently: the penalty scores that choose the mask, every mask pattern on its own, and the fixed patterns.
 */
public class QrCodeStandardTest {

    private static final String[] TEXTS = {
            "Lunavera", "LUNAVERA|HD12|125000|20261009051200", "x", "0123456789", "aaaaaaaaaaaaaaaaaaaaaaaa",
            "Hóa đơn – Cà phê ☕", "https://example.com/pay?a=1&b=2", "The quick brown fox jumps over the lazy dog",
            "x".repeat(60), "ab".repeat(75), "Chuyển khoản 150.000đ cho đơn #4821 - cảm ơn quý khách!",
            // texts for which two masks have the same lowest penalty (the first one must win), or for which the share-of-dark
            // rule decides between two masks; found by trying 1,500 texts against the changed encoders
            "LUNAVERA|HD198|27126", "id-205-zzzzz", "id-355-" + "z".repeat(35), "Đơn 1493 cảm ơn",
    };

    // ---- the penalty rules, written from the standard (outside of the symbol counts as light)

    private static boolean dark(QrCode qr, int x, int y) {
        return qr.getModule(x, y);
    }

    private static int penalty(QrCode qr) {
        int size = qr.getSize();
        int result = 0;
        // N1: five or more modules of one colour in a row or a column: 3 for five, 1 more for each further one
        for (int dir = 0; dir < 2; dir++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean same = dir == 0 ? dark(qr, b, a) == dark(qr, b - 1, a) : dark(qr, a, b) == dark(qr, a, b - 1);
                    if (same) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result += 1;
                    } else {
                        run = 1;
                    }
                }
            }
        }
        // N2: every 2 x 2 block of one colour: 3
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = dark(qr, x, y);
                if (c == dark(qr, x + 1, y) && c == dark(qr, x, y + 1) && c == dark(qr, x + 1, y + 1)) result += 3;
            }
        }
        // N3: dark-light-dark-dark-dark-light-dark with four light modules before or after it: 40
        boolean[] pattern = {true, false, true, true, true, false, true};
        for (int dir = 0; dir < 2; dir++) {
            for (int a = 0; a < size; a++) {
                for (int b = 0; b + 7 <= size; b++) {
                    boolean match = true;
                    for (int k = 0; k < 7 && match; k++) {
                        boolean m = dir == 0 ? dark(qr, b + k, a) : dark(qr, a, b + k);
                        match = m == pattern[k];
                    }
                    if (!match) continue;
                    if (light(qr, dir, a, b - 4, b) || light(qr, dir, a, b + 7, b + 11)) result += 40;
                }
            }
        }
        // N4: the share of dark modules: 10 for every full 5 % away from 50 %, counted from the next lower multiple of 5
        int darkCount = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (dark(qr, x, y)) darkCount++;
        int total = size * size;
        double percent = darkCount * 100.0 / total;
        int k = (int) Math.ceil(Math.abs(percent - 50) / 5) - 1;
        result += Math.max(k, 0) * 10;
        return result;
    }

    private static boolean light(QrCode qr, int dir, int line, int from, int to) {
        for (int p = from; p < to; p++) {
            boolean m = dir == 0 ? dark(qr, p, line) : dark(qr, line, p);
            if (m) return false;
        }
        return true;
    }

    @Test
    public void theMaskChosenIsTheFirstOneWithTheLowestPenalty() {
        int differentMasks = 0;
        boolean[] seen = new boolean[8];
        for (String text : TEXTS) {
            int best = -1;
            int bestPenalty = Integer.MAX_VALUE;
            for (int mask = 0; mask < 8; mask++) {
                int penalty = penalty(QrCode.encodeText(text, mask));
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    best = mask;
                }
            }
            QrCode chosen = QrCode.encodeText(text);
            assertEquals("the mask for \"" + text + "\"", best, chosen.getMask());
            if (!seen[best]) {
                seen[best] = true;
                differentMasks++;
            }
        }
        assertTrue("the texts must make the choice matter: " + differentMasks + " different masks were chosen", differentMasks >= 3);
    }

    @Test
    public void everyMaskPatternOnItsOwnProducesASymbolTheDecoderReads() {
        for (String text : new String[]{"Lunavera", "x".repeat(40), "Hóa đơn – Cà phê ☕ " + "y".repeat(120)}) {
            for (int mask = 0; mask < 8; mask++) {
                QrDecoder.Result result = QrDecoder.decode(QrCode.encodeText(text, mask));
                assertEquals("mask " + mask, text, result.text());
                assertEquals(mask, result.mask());
            }
        }
    }

    // ---- the fixed patterns

    private static List<Integer> alignmentPositions(int version) {
        List<Integer> positions = new ArrayList<>();
        if (version == 1) return positions;
        int size = version * 4 + 17;
        int count = version / 7 + 2;
        int step = version == 32 ? 26 : (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2;
        for (int i = 0, pos = size - 7; i < count - 1; i++, pos -= step) positions.add(0, pos);
        positions.add(0, 6);
        return positions;
    }

    @Test
    public void everyAlignmentPatternIsADarkCentreInALightRingInADarkRing() {
        for (int version : new int[]{2, 5, 6, 7, 10, 14, 21, 28, 32, 40}) {
            QrCode qr = QrCode.encodeText(version == 40 ? "x".repeat(QrCode.MAX_BYTES) : versionText(version));
            assertEquals(version, qr.getVersion());
            List<Integer> positions = alignmentPositions(version);
            int last = positions.size() - 1;
            int patterns = 0;
            for (int i = 0; i <= last; i++) {
                for (int j = 0; j <= last; j++) {
                    if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue; // the finder corners
                    int cx = positions.get(i);
                    int cy = positions.get(j);
                    patterns++;
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            boolean expected = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                            assertEquals("version " + version + " alignment pattern at " + cx + "," + cy + " offset " + dx + "," + dy,
                                    expected, qr.getModule(cx + dx, cy + dy));
                        }
                    }
                }
            }
            assertTrue(patterns >= 1);
        }
    }

    private static String versionText(int version) {
        // the longest text that still needs this version, found by trying
        int n = 1;
        while (QrCode.encodeText("v".repeat(n)).getVersion() < version) n++;
        return "v".repeat(n);
    }

    @Test
    public void everyFinderPatternHasItsRingsAndItsLightSeparator() {
        QrCode qr = QrCode.encodeText("Lunavera");
        int size = qr.getSize();
        int[][] corners = {{0, 0}, {size - 7, 0}, {0, size - 7}};
        for (int[] corner : corners) {
            for (int dy = 0; dy < 7; dy++) {
                for (int dx = 0; dx < 7; dx++) {
                    int ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                    boolean expected = ring != 2;
                    assertEquals("finder at " + corner[0] + "," + corner[1] + " offset " + dx + "," + dy, expected, qr.getModule(corner[0] + dx, corner[1] + dy));
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            assertFalse("separator of the top-left finder, right of it", qr.getModule(7, i));
            assertFalse("separator of the top-left finder, below it", qr.getModule(i, 7));
            assertFalse("separator of the top-right finder", qr.getModule(size - 8, i));
            assertFalse("separator of the bottom-left finder", qr.getModule(i, size - 8));
        }
    }

    @Test
    public void theTimingLinesAlternateStartingWithDarkAndTheFixedDarkModuleIsThere() {
        for (String text : new String[]{"Lunavera", "x".repeat(200)}) {
            QrCode qr = QrCode.encodeText(text);
            int size = qr.getSize();
            for (int i = 8; i < size - 8; i++) {
                assertEquals("row timing at " + i, i % 2 == 0, qr.getModule(i, 6));
                assertEquals("column timing at " + i, i % 2 == 0, qr.getModule(6, i));
            }
            assertTrue("the one module that is always dark", qr.getModule(8, size - 8));
        }
    }

    @Test
    public void theEdgesOfTheSymbolAreInsideAndTheNextModuleOutsideIsLight() {
        QrCode qr = QrCode.encodeText("Lunavera");
        int size = qr.getSize();
        assertTrue("top-left corner of a finder", qr.getModule(0, 0));
        assertTrue("top-right corner", qr.getModule(size - 1, 0));
        assertTrue("bottom-left corner", qr.getModule(0, size - 1));
        assertFalse(qr.getModule(-1, 0));
        assertFalse(qr.getModule(0, -1));
        assertFalse(qr.getModule(size, 0));
        assertFalse(qr.getModule(0, size));
        assertFalse(qr.getModule(size, size));
    }
}
