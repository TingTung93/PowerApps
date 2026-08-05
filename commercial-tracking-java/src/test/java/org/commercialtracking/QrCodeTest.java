package org.commercialtracking;

import java.util.Arrays;

public final class QrCodeTest {
    public static void main(String[] args) throws Exception {
        // Reed–Solomon generator polynomial for 10 ECC codewords, from the QR spec (Annex A),
        // expressed as alpha exponents, leading coefficient first.
        int[] expected10 = {0, 251, 67, 46, 61, 118, 70, 64, 94, 32, 45};
        int[] actual10 = QrCode.generatorPolynomial(10);
        check(Arrays.equals(expected10, actual10),
                "generator(10) = " + Arrays.toString(actual10));

        QrCode small = QrCode.encode("TRACKING-0001");            // 13 bytes -> version 1
        check(small.size == 21, "v1 size = " + small.size);
        check(finder(small, 0, 0), "top-left finder");
        check(finder(small, 0, small.size - 7), "top-right finder");
        check(finder(small, small.size - 7, 0), "bottom-left finder");

        QrCode mid = QrCode.encode("1Z999AA10123456784-EXTRA-PAYLOAD-XYZ"); // ~36 bytes -> version 3
        check(mid.size == 29, "v3 size = " + mid.size);

        boolean threw = false;
        try { QrCode.encode(repeat("X", 60)); } catch (IllegalArgumentException ex) { threw = true; }
        check(threw, "over-length payload rejected");
        System.out.println("QrCodeTest: PASS");
    }

    // A finder pattern: dark 7x7 border+center at (r,c). Sample the four ring corners and center.
    private static boolean finder(QrCode q, int r, int c) {
        return q.modules[r][c] && q.modules[r][c + 6] && q.modules[r + 6][c]
                && q.modules[r + 6][c + 6] && q.modules[r + 3][c + 3]
                && !q.modules[r + 1][c + 1] && !q.modules[r + 1][c + 5];
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
