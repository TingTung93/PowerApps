package org.medsupply;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Gs1Parser {
    private Gs1Parser() {}

    private static final char GS = (char) 29;
    // Application Identifiers that can begin a new field; used to terminate a
    // variable-length field when no GS separator is present.
    private static final Set<String> KNOWN_AIS =
            new HashSet<String>(Arrays.asList("01", "10", "11", "15", "17", "21", "30", "240", "91"));

    public static Gs1Scan parse(String raw) {
        Gs1Scan scan = new Gs1Scan();
        scan.raw = raw == null ? "" : raw;
        String s = scan.raw.replace("(", "").replace(")", "");
        int i = 0;
        boolean sawUnknown = false;
        while (i + 2 <= s.length()) {
            if (s.charAt(i) == GS) { i++; continue; }
            String ai = s.substring(i, i + 2);
            i += 2;
            if ("01".equals(ai)) {
                String v = fixed(s, i, 14);
                scan.gtin = v;
                i += v.length();
            } else if ("17".equals(ai)) {
                String v = fixed(s, i, 6);
                scan.expirationRaw = v;
                i += v.length();
            } else if ("11".equals(ai) || "15".equals(ai)) {
                i += fixed(s, i, 6).length(); // production / best-before date: skip
            } else if ("10".equals(ai)) {
                int[] span = variable(s, i);
                scan.lot = s.substring(i, span[0]);
                i = span[1];
            } else if ("21".equals(ai)) {
                int[] span = variable(s, i);
                scan.serial = s.substring(i, span[0]);
                i = span[1];
            } else if ("30".equals(ai)) {
                int[] span = variable(s, i);
                scan.count = s.substring(i, span[0]);
                i = span[1];
            } else {
                sawUnknown = true;
                break; // unknown AI: stop rather than mis-slice
            }
        }
        scan.expirationIso = toIso(scan.expirationRaw);
        scan.success = scan.gtin.length() == 14 && scan.gtin.matches("[0-9]{14}");
        if (!scan.success) {
            scan.note = scan.gtin.length() == 0 ? "No GTIN (AI 01) found." : "GTIN is not 14 digits.";
        } else if (sawUnknown || (scan.expirationRaw.length() > 0 && scan.expirationIso.length() == 0)) {
            scan.requiresConfirmation = true;
            scan.note = "Barcode partially recognized; please confirm values.";
        }
        return scan;
    }

    private static String fixed(String s, int start, int len) {
        int end = Math.min(s.length(), start + len);
        return s.substring(start, end);
    }

    // Returns {contentEnd, nextIndex}: content is s[start..contentEnd), and
    // nextIndex resumes after any GS separator that terminated the field.
    private static int[] variable(String s, int start) {
        int gs = s.indexOf(GS, start);
        if (gs >= 0) return new int[] {gs, gs + 1};
        // No GS: terminate at the next known AI boundary, else end of string.
        for (int p = start + 1; p + 2 <= s.length(); p++) {
            String twoDigitAi = s.substring(p, p + 2);
            if (KNOWN_AIS.contains(twoDigitAi) && plausibleValue(s, p, twoDigitAi))
                return new int[] {p, p};
            if (p + 3 <= s.length()) {
                String threeDigitAi = s.substring(p, p + 3);
                if (KNOWN_AIS.contains(threeDigitAi) && plausibleValue(s, p, threeDigitAi))
                    return new int[] {p, p};
            }
        }
        return new int[] {s.length(), s.length()};
    }

    private static boolean plausibleValue(String s, int aiStart, String ai) {
        int valueStart = aiStart + ai.length();
        if ("01".equals(ai))
            return hasDigits(s, valueStart, 14);
        if ("11".equals(ai) || "15".equals(ai) || "17".equals(ai))
            return hasDigits(s, valueStart, 6)
                    && toIso(s.substring(valueStart, valueStart + 6)).length() > 0;
        if ("30".equals(ai))
            return valueStart < s.length() && Character.isDigit(s.charAt(valueStart));
        return valueStart < s.length();
    }

    private static boolean hasDigits(String s, int start, int length) {
        int end = start + length;
        if (start < 0 || end > s.length()) return false;
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static String toIso(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6 || !yymmdd.matches("[0-9]{6}")) return "";
        int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        if (month < 1 || month > 12) return "";
        int lastDay = java.time.YearMonth.of(year, month).lengthOfMonth();
        if (day == 0) day = lastDay;        // GS1: DD=00 means end of month
        if (day < 1 || day > lastDay) return "";
        return String.format("%04d-%02d-%02d", year, month, day);
    }
}
