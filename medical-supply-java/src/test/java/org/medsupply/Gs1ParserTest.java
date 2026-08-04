package org.medsupply;

public final class Gs1ParserTest {
    private static final char GS = (char) 29;

    public static void main(String[] args) {
        parsesConcatenatedFixedThenVariable();
        parsesWithGsSeparator();
        parsesParenthesizedHumanReadable();
        lotContainingSeventeenNotMisparsed();
        variableLotEmbeddingAiNotMisparsed();
        variableSerialEmbeddingAiNotMisparsed();
        endOfMonthDayZero();
        failsWithoutGtin();
        System.out.println("Gs1ParserTest: PASS");
    }

    // 01 GTIN(14) 17 exp(6=261130) 10 lot(AB17CD) — lot has "17" inside, no GS.
    private static void parsesConcatenatedFixedThenVariable() {
        Gs1Scan s = Gs1Parser.parse("010038074000001017261130" + "10AB17CD");
        check(s.success, "success");
        check("00380740000010".equals(s.gtin), "gtin: " + s.gtin);
        check("261130".equals(s.expirationRaw), "expRaw: " + s.expirationRaw);
        check("2026-11-30".equals(s.expirationIso), "expIso: " + s.expirationIso);
        check("AB17CD".equals(s.lot), "lot: " + s.lot);
    }

    private static void parsesWithGsSeparator() {
        String raw = "0100380740000010" + "10LOT" + GS + "17261130";
        Gs1Scan s = Gs1Parser.parse(raw);
        check("00380740000010".equals(s.gtin), "gtin gs");
        check("LOT".equals(s.lot), "lot gs: " + s.lot);
        check("2026-11-30".equals(s.expirationIso), "exp gs");
    }

    private static void parsesParenthesizedHumanReadable() {
        Gs1Scan s = Gs1Parser.parse("(01)00380740000010(17)261130(10)AB17CD");
        check("00380740000010".equals(s.gtin), "gtin paren");
        check("AB17CD".equals(s.lot), "lot paren: " + s.lot);
        check("2026-11-30".equals(s.expirationIso), "exp paren");
    }

    private static void lotContainingSeventeenNotMisparsed() {
        // Lot printed before expiry, terminated by GS. Lot value literally "1799".
        String raw = "0100380740000010" + "101799" + GS + "17270101";
        Gs1Scan s = Gs1Parser.parse(raw);
        check("1799".equals(s.lot), "lot literal 1799: " + s.lot);
        check("2027-01-01".equals(s.expirationIso), "exp after lot");
    }

    // A lot value that embeds the digits "10" (itself an AI) must not be split when
    // there is no GS separator; a variable field runs to the end of the barcode.
    private static void variableLotEmbeddingAiNotMisparsed() {
        Gs1Scan s = Gs1Parser.parse("0100380740000010" + "10AB10CD");
        check(s.success, "success embed");
        check("AB10CD".equals(s.lot), "lot embed 10: " + s.lot);
    }

    // Same for a serial (AI 21) value that embeds "21".
    private static void variableSerialEmbeddingAiNotMisparsed() {
        Gs1Scan s = Gs1Parser.parse("0100380740000010" + "21X21Y");
        check("X21Y".equals(s.serial), "serial embed 21: " + s.serial);
    }

    private static void endOfMonthDayZero() {
        // AI 17 day "00" means end of month; 260200 -> 2026-02-28.
        Gs1Scan s = Gs1Parser.parse("010038074000001017260200");
        check("2026-02-28".equals(s.expirationIso), "eom: " + s.expirationIso);
    }

    private static void failsWithoutGtin() {
        Gs1Scan s = Gs1Parser.parse("17261130");
        check(!s.success, "no gtin fails");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
