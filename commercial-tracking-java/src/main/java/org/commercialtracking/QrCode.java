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
}
