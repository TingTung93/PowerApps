package org.medsupply;

public final class ItemKey {
    private ItemKey() {}

    public static String of(String gtin, String lot, String expirationIso) {
        String digits = expirationIso == null ? "" : expirationIso.replaceAll("[^0-9]", "");
        return safe(gtin) + "|" + safe(lot) + "|" + digits;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
