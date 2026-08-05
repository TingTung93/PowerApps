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
        System.out.println("QrCodeTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
