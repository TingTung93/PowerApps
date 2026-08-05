package org.commercialtracking;

public final class QrCode {
    public final int size;
    public final boolean[][] modules; // [row][col], true = dark

    QrCode(int size, boolean[][] modules) {
        this.size = size;
        this.modules = modules;
    }

    // ---- Galois field GF(256), primitive polynomial 0x11D, generator element 2 ----
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    /** Generator polynomial coefficients as alpha exponents, length degree+1, leading term first. */
    static int[] generatorPolynomial(int degree) {
        int[] coeff = new int[degree + 1]; // coefficients as field values
        coeff[0] = 1;
        int len = 1;
        for (int i = 0; i < degree; i++) {
            len++;
            for (int j = len - 1; j > 0; j--)
                coeff[j] = coeff[j - 1] ^ mul(coeff[j], EXP[i]);
            coeff[0] = mul(coeff[0], EXP[i]);
        }
        int[] exps = new int[degree + 1];
        for (int i = 0; i <= degree; i++) exps[i] = LOG[coeff[degree - i]];
        return exps;
    }

    // ECC level M, versions 1..3 (single ECC block).
    private static final int[] VERSION_SIZE   = {0, 21, 25, 29};
    private static final int[] DATA_CODEWORDS = {0, 16, 28, 44};
    private static final int[] ECC_CODEWORDS  = {0, 10, 16, 26};
    private static final int[] ALIGN_CENTER   = {0, 0, 18, 22}; // 0 = no alignment pattern

    public static QrCode encode(String text) {
        byte[] data;
        try { data = text.getBytes("ISO-8859-1"); }
        catch (java.io.UnsupportedEncodingException ex) { throw new IllegalStateException(ex); }
        int version = chooseVersion(data.length);
        int[] codewords = buildCodewords(data, version);
        int n = VERSION_SIZE[version];
        boolean[][] m = new boolean[n][n];
        boolean[][] fn = new boolean[n][n];
        drawFunctionPatterns(version, m, fn);
        drawData(codewordBits(codewords), m, fn);
        int mask = chooseMask(version, m, fn);
        applyMask(mask, m, fn);
        drawFormat(mask, m);
        return new QrCode(n, m);
    }

    private static int chooseVersion(int dataLen) {
        for (int v = 1; v <= 3; v++)
            if (dataLen <= DATA_CODEWORDS[v] - 2) return v;
        throw new IllegalArgumentException("Payload too long for QR version 3: " + dataLen + " bytes");
    }

    private static int[] buildCodewords(byte[] data, int version) {
        int totalData = DATA_CODEWORDS[version];
        boolean[] bits = new boolean[totalData * 8];
        int p = 0;
        p = put(bits, p, 0b0100, 4);            // byte mode
        p = put(bits, p, data.length, 8);       // character count (8 bits, versions 1..9)
        for (byte b : data) p = put(bits, p, b & 0xFF, 8);
        // terminator: up to 4 zero bits, bounded by capacity
        for (int i = 0; i < 4 && p < bits.length; i++) p++;
        // pad to byte boundary (bits already default false)
        while (p % 8 != 0) p++;
        // pad bytes
        int[] out = new int[totalData + ECC_CODEWORDS[version]];
        int bytes = p / 8;
        for (int i = 0; i < bytes; i++) out[i] = bitsToByte(bits, i * 8);
        boolean ec11 = false;
        for (int i = bytes; i < totalData; i++) { out[i] = ec11 ? 0x11 : 0xEC; ec11 = !ec11; }
        int[] ecc = rsRemainder(java.util.Arrays.copyOf(out, totalData), ECC_CODEWORDS[version]);
        System.arraycopy(ecc, 0, out, totalData, ecc.length);
        return out;
    }

    private static int put(boolean[] bits, int p, int value, int len) {
        for (int i = len - 1; i >= 0; i--) bits[p++] = ((value >> i) & 1) != 0;
        return p;
    }

    private static int bitsToByte(boolean[] bits, int off) {
        int v = 0;
        for (int i = 0; i < 8; i++) v = (v << 1) | (bits[off + i] ? 1 : 0);
        return v;
    }

    private static int[] rsRemainder(int[] data, int eccLen) {
        int[] gen = generatorPolynomial(eccLen); // exponents, length eccLen+1
        int[] rem = new int[eccLen];
        for (int i = 0; i < data.length; i++) {
            int factor = data[i] ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, eccLen - 1);
            rem[eccLen - 1] = 0;
            if (factor != 0) {
                int lf = LOG[factor];
                for (int j = 0; j < eccLen; j++) rem[j] ^= EXP[lf + gen[j + 1]];
            }
        }
        return rem;
    }

    private static boolean[] codewordBits(int[] codewords) {
        boolean[] bits = new boolean[codewords.length * 8];
        int p = 0;
        for (int cw : codewords) p = put(bits, p, cw, 8);
        return bits;
    }

    // ---- matrix construction ----
    private static void set(boolean[][] m, int r, int c, boolean v) { m[r][c] = v; }

    private static void setFn(boolean[][] m, boolean[][] fn, int r, int c, boolean v) {
        if (r < 0 || c < 0 || r >= m.length || c >= m.length) return;
        m[r][c] = v; fn[r][c] = true;
    }

    private static void drawFunctionPatterns(int version, boolean[][] m, boolean[][] fn) {
        int n = m.length;
        drawFinder(m, fn, 0, 0);
        drawFinder(m, fn, 0, n - 7);
        drawFinder(m, fn, n - 7, 0);
        for (int i = 8; i < n - 8; i++) {
            boolean dark = (i % 2 == 0);
            setFn(m, fn, 6, i, dark);
            setFn(m, fn, i, 6, dark);
        }
        setFn(m, fn, n - 8, 8, true); // dark module
        int c = ALIGN_CENTER[version];
        if (c != 0)
            for (int dr = -2; dr <= 2; dr++)
                for (int dc = -2; dc <= 2; dc++)
                    setFn(m, fn, c + dr, c + dc, Math.max(Math.abs(dr), Math.abs(dc)) != 1);
        // reserve format-info modules (values assigned later); mark as function so data skips them
        for (int i = 0; i <= 8; i++) { reserve(fn, 8, i); reserve(fn, i, 8); }
        for (int i = 0; i < 8; i++) { reserve(fn, 8, n - 1 - i); reserve(fn, n - 1 - i, 8); }
    }

    private static void reserve(boolean[][] fn, int r, int c) {
        if (r >= 0 && c >= 0 && r < fn.length && c < fn.length) fn[r][c] = true;
    }

    private static void drawFinder(boolean[][] m, boolean[][] fn, int row, int col) {
        for (int r = -1; r <= 7; r++)
            for (int c = -1; c <= 7; c++) {
                boolean dark = (r >= 0 && r <= 6 && c >= 0 && c <= 6)
                        && (r == 0 || r == 6 || c == 0 || c == 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
                setFn(m, fn, row + r, col + c, dark);
            }
    }

    private static void drawData(boolean[] bits, boolean[][] m, boolean[][] fn) {
        int n = m.length, bit = 0;
        boolean upward = true;
        for (int col = n - 1; col > 0; col -= 2) {
            if (col == 6) col = 5; // skip the vertical timing column
            for (int i = 0; i < n; i++) {
                int row = upward ? n - 1 - i : i;
                for (int c = 0; c < 2; c++) {
                    int cc = col - c;
                    if (!fn[row][cc]) {
                        boolean dark = bit < bits.length && bits[bit];
                        bit++;
                        m[row][cc] = dark;
                    }
                }
            }
            upward = !upward;
        }
    }

    private static boolean maskCondition(int mask, int r, int c) {
        switch (mask) {
            case 0: return (r + c) % 2 == 0;
            case 1: return r % 2 == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return (r / 2 + c / 3) % 2 == 0;
            case 5: return (r * c) % 2 + (r * c) % 3 == 0;
            case 6: return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            default: return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
        }
    }

    private static void applyMask(int mask, boolean[][] m, boolean[][] fn) {
        for (int r = 0; r < m.length; r++)
            for (int c = 0; c < m.length; c++)
                if (!fn[r][c] && maskCondition(mask, r, c)) m[r][c] = !m[r][c];
    }

    private static int chooseMask(int version, boolean[][] m, boolean[][] fn) {
        int best = 0, bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] t = new boolean[m.length][];
            for (int r = 0; r < m.length; r++) t[r] = m[r].clone();
            applyMask(mask, t, fn);
            drawFormat(mask, t);
            int p = penalty(t);
            if (p < bestPenalty) { bestPenalty = p; best = mask; }
        }
        return best;
    }

    private static int penalty(boolean[][] m) {
        int n = m.length, score = 0;
        // Rule 1: runs of >=5 in rows and columns
        for (int r = 0; r < n; r++) { score += runPenalty(m[r]); }
        for (int c = 0; c < n; c++) {
            boolean[] col = new boolean[n];
            for (int r = 0; r < n; r++) col[r] = m[r][c];
            score += runPenalty(col);
        }
        // Rule 2: 2x2 blocks
        for (int r = 0; r < n - 1; r++)
            for (int c = 0; c < n - 1; c++)
                if (m[r][c] == m[r][c + 1] && m[r][c] == m[r + 1][c] && m[r][c] == m[r + 1][c + 1])
                    score += 3;
        // Rule 3: finder-like 1:1:3:1:1 patterns (with 4 light either side)
        boolean[] a = {true, false, true, true, true, false, true, false, false, false, false};
        boolean[] b = {false, false, false, false, true, false, true, true, true, false, true};
        for (int r = 0; r < n; r++)
            for (int c = 0; c <= n - 11; c++) {
                if (matches(m, r, c, a, true) || matches(m, r, c, b, true)) score += 40;
            }
        for (int c = 0; c < n; c++)
            for (int r = 0; r <= n - 11; r++) {
                if (matches(m, r, c, a, false) || matches(m, r, c, b, false)) score += 40;
            }
        // Rule 4: dark proportion
        int dark = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (m[r][c]) dark++;
        int percent = dark * 100 / (n * n);
        score += Math.min(Math.abs(percent - 50) / 5 * 10, 100);
        return score;
    }

    private static int runPenalty(boolean[] line) {
        int score = 0, run = 1;
        for (int i = 1; i < line.length; i++) {
            if (line[i] == line[i - 1]) { run++; }
            else { if (run >= 5) score += 3 + (run - 5); run = 1; }
        }
        if (run >= 5) score += 3 + (run - 5);
        return score;
    }

    private static boolean matches(boolean[][] m, int r, int c, boolean[] pattern, boolean horizontal) {
        for (int i = 0; i < pattern.length; i++) {
            boolean v = horizontal ? m[r][c + i] : m[r + i][c];
            if (v != pattern[i]) return false;
        }
        return true;
    }

    private static int formatBits(int mask) {
        int data = (0b00 << 3) | mask; // EC level M = 00
        int dividend = data << 10;
        for (int i = 14; i >= 10; i--)
            if (((dividend >> i) & 1) == 1) dividend ^= (0x537 << (i - 10));
        return ((data << 10) | (dividend & 0x3FF)) ^ 0x5412;
    }

    private static void drawFormat(int mask, boolean[][] m) {
        int n = m.length, bits = formatBits(mask);
        for (int i = 0; i < 15; i++) {
            boolean v = ((bits >> i) & 1) != 0;
            // copy 1 (around top-left finder)
            if (i < 6) set(m, 8, i, v);
            else if (i < 8) set(m, 8, i + 1, v);
            else if (i == 8) set(m, 7, 8, v);
            else set(m, 14 - i, 8, v);
            // copy 2 (split across top-right and bottom-left)
            if (i < 8) set(m, 8, n - 1 - i, v);
            else set(m, n - 15 + i, 8, v);
        }
        m[n - 8][8] = true; // dark module (re-assert)
    }
}
