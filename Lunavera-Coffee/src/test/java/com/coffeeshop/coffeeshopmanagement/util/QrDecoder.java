package com.coffeeshop.coffeeshopmanagement.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * TODO 6.60: a QR reader written for the tests, from the standard and not from {@link QrCode}: it shares no table and no helper
 * with the encoder (the alignment-pattern positions, the capacity per version and the error-correction block structure are typed
 * in here separately). It reads a module grid the way a phone does and checks every step on the way: both copies of the format
 * information and their BCH check, level M, the mask, both copies of the version information (version 7 and up), the zig-zag
 * read, the de-interleaving of the blocks, the Reed-Solomon codewords of every block, the byte-mode header (with the 8-bit or
 * 16-bit count), the terminator and the padding bytes. Anything wrong throws {@link AssertionError} saying which step failed.
 * Test code only.
 */
final class QrDecoder {

    /** Largest number of bytes one symbol holds at level M, by version 1..40 (ISO/IEC 18004 capacity table, byte mode). */
    static final int[] BYTE_CAPACITY_M = {
            14, 26, 42, 62, 84, 106, 122, 152, 180, 213, 251, 287, 331, 362, 412, 450, 504, 560, 624, 666,
            711, 779, 857, 911, 997, 1059, 1125, 1190, 1264, 1370, 1452, 1538, 1628, 1722, 1809, 1911, 1989, 2099, 2213, 2331};

    /** Error-correction codewords in each block, level M, by version 1..40. */
    private static final int[] ECC_PER_BLOCK = {
            10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28};

    /** Number of blocks, level M, by version 1..40. */
    private static final int[] BLOCKS = {
            1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49};

    /** Centres of the alignment patterns by version 1..40 (the table of the standard, not a formula). */
    private static final int[][] ALIGNMENT = {
            {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50},
            {6, 30, 54}, {6, 32, 58}, {6, 34, 62}, {6, 26, 46, 66}, {6, 26, 48, 70}, {6, 26, 50, 74}, {6, 30, 54, 78},
            {6, 30, 56, 82}, {6, 30, 58, 86}, {6, 34, 62, 90}, {6, 28, 50, 72, 94}, {6, 26, 50, 74, 98},
            {6, 30, 54, 78, 102}, {6, 28, 54, 80, 106}, {6, 32, 58, 84, 110}, {6, 30, 58, 86, 114}, {6, 34, 62, 90, 118},
            {6, 26, 50, 74, 98, 122}, {6, 30, 54, 78, 102, 126}, {6, 26, 52, 78, 104, 130}, {6, 30, 56, 82, 108, 134},
            {6, 34, 60, 86, 112, 138}, {6, 30, 58, 86, 114, 142}, {6, 34, 62, 90, 118, 146},
            {6, 30, 54, 78, 102, 126, 150}, {6, 24, 50, 76, 102, 128, 154}, {6, 28, 54, 80, 106, 132, 158},
            {6, 32, 58, 84, 110, 136, 162}, {6, 26, 54, 82, 110, 138, 166}, {6, 30, 58, 86, 114, 142, 170}};

    /** What a successful read found, besides the text. */
    record Result(String text, int version, int mask) {
    }

    private final boolean[][] grid; // [y][x], true = dark
    private final int size;
    private final int version;
    private final boolean[][] function;

    private QrDecoder(boolean[][] grid) {
        this.grid = grid;
        this.size = grid.length;
        if (size < 21 || size > 177 || (size - 17) % 4 != 0) throw new AssertionError("not a QR size: " + size);
        this.version = (size - 17) / 4;
        this.function = functionModules();
    }

    static Result decode(boolean[][] grid) {
        return new QrDecoder(grid).read();
    }

    static Result decode(QrCode code) {
        boolean[][] grid = new boolean[code.getSize()][code.getSize()];
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid.length; x++) grid[y][x] = code.getModule(x, y);
        }
        return decode(grid);
    }

    // ------------------------------------------------------------------ steps

    private Result read() {
        int mask = readFormat();
        if (version >= 7) checkVersionInformation();
        byte[] codewords = readCodewords(mask);
        byte[] data = deinterleaveAndCheck(codewords);
        return new Result(readPayload(data), version, mask);
    }

    private boolean at(int x, int y) {
        return grid[y][x];
    }

    /** Both copies of the 15 format bits; each must pass the BCH check and say level M; returns the mask. */
    private int readFormat() {
        int first = 0;
        for (int i = 0; i <= 5; i++) first |= bit(at(8, i), i);
        first |= bit(at(8, 7), 6) | bit(at(8, 8), 7) | bit(at(7, 8), 8);
        for (int i = 9; i < 15; i++) first |= bit(at(14 - i, 8), i);
        int second = 0;
        for (int i = 0; i < 8; i++) second |= bit(at(size - 1 - i, 8), i);
        for (int i = 8; i < 15; i++) second |= bit(at(8, size - 15 + i), i);
        if (first != second) throw new AssertionError("the two copies of the format information differ");
        if (!at(8, size - 8)) throw new AssertionError("the dark module is missing");
        int data = (first ^ 0x5412) >> 10;
        if (formatBits(data) != first) throw new AssertionError("format information fails its BCH check");
        if ((data >> 3) != 0) throw new AssertionError("error-correction level is not M (bits " + (data >> 3) + ")");
        return data & 7;
    }

    /** The 15 format bits for the 5 data bits (level and mask), with the standard's XOR pattern. */
    private static int formatBits(int data) {
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        return ((data << 10) | rem) ^ 0x5412;
    }

    private void checkVersionInformation() {
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
        int expected = (version << 12) | rem;
        int topRight = 0;
        int bottomLeft = 0;
        for (int i = 0; i < 18; i++) {
            topRight |= bit(at(size - 11 + i % 3, i / 3), i);
            bottomLeft |= bit(at(i / 3, size - 11 + i % 3), i);
        }
        if (topRight != expected) throw new AssertionError("version information (top right) is wrong for version " + version);
        if (bottomLeft != expected) throw new AssertionError("version information (bottom left) is wrong for version " + version);
    }

    private static int bit(boolean dark, int position) {
        return dark ? 1 << position : 0;
    }

    /** Everything that is not data: finders with their separators, timing, alignment patterns, format and version areas. */
    private boolean[][] functionModules() {
        boolean[][] f = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean finderArea = (x < 9 && y < 9) || (x >= size - 8 && y < 9) || (x < 9 && y >= size - 8);
                f[y][x] = finderArea || x == 6 || y == 6;
            }
        }
        int[] centres = ALIGNMENT[version - 1];
        for (int i = 0; i < centres.length; i++) {
            for (int j = 0; j < centres.length; j++) {
                boolean overlapsFinder = (i == 0 && j == 0) || (i == 0 && j == centres.length - 1)
                        || (i == centres.length - 1 && j == 0);
                if (overlapsFinder) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) f[centres[i] + dy][centres[j] + dx] = true;
                }
            }
        }
        if (version >= 7) {
            for (int a = 0; a < 6; a++) {
                for (int b = size - 11; b < size - 8; b++) {
                    f[a][b] = true;
                    f[b][a] = true;
                }
            }
        }
        return f;
    }

    /** Un-masks and reads the data modules in the standard's zig-zag order, 8 bits per codeword. */
    private byte[] readCodewords(int mask) {
        int dataModules = 0;
        for (boolean[] row : function) {
            for (boolean isFunction : row) if (!isFunction) dataModules++;
        }
        byte[] out = new byte[dataModules / 8];
        int bitIndex = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5; // the timing column is skipped
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (function[y][x] || bitIndex >= out.length * 8) continue;
                    boolean dark = at(x, y) ^ masked(mask, x, y);
                    if (dark) out[bitIndex >> 3] |= (byte) (0x80 >> (bitIndex & 7));
                    bitIndex++;
                }
            }
        }
        if (bitIndex != out.length * 8) throw new AssertionError("read " + bitIndex + " bits, expected " + out.length * 8);
        return out;
    }

    private static boolean masked(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (x + y) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (x + y) % 3 == 0;
            case 4 -> (x / 3 + y / 2) % 2 == 0;
            case 5 -> x * y % 2 + x * y % 3 == 0;
            case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
            case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
            default -> throw new AssertionError("mask " + mask);
        };
    }

    /** Splits the interleaved codewords back into blocks, checks each block's Reed-Solomon codewords, returns the data in order. */
    private byte[] deinterleaveAndCheck(byte[] codewords) {
        int blocks = BLOCKS[version - 1];
        int eccLen = ECC_PER_BLOCK[version - 1];
        int total = codewords.length;
        int shortBlockLen = total / blocks;
        int shortBlocks = blocks - total % blocks;
        byte[][] data = new byte[blocks][];
        byte[][] ecc = new byte[blocks][eccLen];
        for (int b = 0; b < blocks; b++) data[b] = new byte[shortBlockLen - eccLen + (b < shortBlocks ? 0 : 1)];
        int k = 0;
        int longest = shortBlockLen - eccLen + 1;
        for (int i = 0; i < longest; i++) {
            for (int b = 0; b < blocks; b++) {
                if (i < data[b].length) data[b][i] = codewords[k++];
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int b = 0; b < blocks; b++) ecc[b][i] = codewords[k++];
        }
        if (k != total) throw new AssertionError("de-interleaving used " + k + " of " + total + " codewords");
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (int b = 0; b < blocks; b++) {
            byte[] expected = reedSolomon(data[b], eccLen);
            for (int i = 0; i < eccLen; i++) {
                if (expected[i] != ecc[b][i]) {
                    throw new AssertionError("Reed-Solomon check failed in block " + (b + 1) + " of " + blocks);
                }
            }
            joined.writeBytes(data[b]);
        }
        return joined.toByteArray();
    }

    /** The {@code eccLen} error-correction codewords of one block: the remainder of data*x^eccLen divided by the generator, in GF(256). */
    private static byte[] reedSolomon(byte[] data, int eccLen) {
        int[] generator = new int[eccLen + 1];
        generator[0] = 1; // coefficients, highest power first; starts as the polynomial 1
        int degree = 0;
        int root = 1;
        for (int r = 0; r < eccLen; r++) {
            int[] next = new int[eccLen + 1];
            for (int i = 0; i <= degree; i++) {
                next[i] ^= generator[i];                       // x * p
                next[i + 1] ^= multiply(generator[i], root);   // root * p  (minus is plus in GF(2^8))
            }
            generator = next;
            degree++;
            root = multiply(root, 2);
        }
        int[] remainder = new int[eccLen];
        for (byte d : data) {
            int factor = (d & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, eccLen - 1);
            remainder[eccLen - 1] = 0;
            for (int i = 0; i < eccLen; i++) remainder[i] ^= multiply(generator[i + 1], factor);
        }
        byte[] out = new byte[eccLen];
        for (int i = 0; i < eccLen; i++) out[i] = (byte) remainder[i];
        return out;
    }

    /** Multiplication in GF(256) with the polynomial x^8+x^4+x^3+x^2+1 (0x11D), bit by bit. */
    private static int multiply(int a, int b) {
        int product = 0;
        for (int i = 7; i >= 0; i--) {
            product = (product << 1) ^ ((product >> 7) * 0x11D);
            if (((b >> i) & 1) != 0) product ^= a;
        }
        return product;
    }

    /** Byte mode header, the bytes, the terminator and the 0xEC/0x11 padding. */
    private String readPayload(byte[] data) {
        int totalBits = data.length * 8;
        int pos = 0;
        int mode = bits(data, pos, 4);
        pos += 4;
        if (mode != 0b0100) throw new AssertionError("mode indicator " + mode + " is not byte mode");
        int countBits = version <= 9 ? 8 : 16;
        int count = bits(data, pos, countBits);
        pos += countBits;
        if (pos + count * 8 > totalBits) throw new AssertionError("count " + count + " does not fit the symbol");
        byte[] text = new byte[count];
        for (int i = 0; i < count; i++, pos += 8) text[i] = (byte) bits(data, pos, 8);
        int terminator = Math.min(4, totalBits - pos);
        if (bits(data, pos, terminator) != 0) throw new AssertionError("terminator is not zero");
        pos += terminator;
        if (pos % 8 != 0) {
            if (bits(data, pos, 8 - pos % 8) != 0) throw new AssertionError("bits up to the byte boundary are not zero");
            pos += 8 - pos % 8;
        }
        for (int pad = 0xEC; pos < totalBits; pos += 8, pad ^= 0xEC ^ 0x11) {
            if (bits(data, pos, 8) != pad) throw new AssertionError("padding byte at bit " + pos + " is not " + Integer.toHexString(pad));
        }
        return new String(text, StandardCharsets.UTF_8);
    }

    private static int bits(byte[] data, int from, int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int index = from + i;
            value = (value << 1) | ((data[index >> 3] >> (7 - (index & 7))) & 1);
        }
        return value;
    }
}
