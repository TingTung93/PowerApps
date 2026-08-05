package org.commercialtracking;

import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TimeFormatTest {
    public static void main(String[] args) {
        // 2026-08-04 21:30:45 UTC. New York is UTC-4 in August -> 17:30 local (5:30 PM).
        String iso = "2026-08-04T21:30:45Z";
        ZoneId ny = ZoneId.of("America/New_York");

        check("2026-08-04".equals(TimeFormat.date(iso, ny)), "date ny = " + TimeFormat.date(iso, ny));
        check("2026-08-04".equals(TimeFormat.date(iso, ZoneOffset.UTC)), "date utc = " + TimeFormat.date(iso, ZoneOffset.UTC));
        check("".equals(TimeFormat.date("", ny)), "date empty input -> empty");
        check("".equals(TimeFormat.date(null, ny)), "date null input -> empty");

        String p12 = TimeFormat.prepared(iso, ny, "12h");
        check("Aug 4, 2026 5:30 PM".equals(p12), "prepared 12h = " + p12);
        String p24 = TimeFormat.prepared(iso, ny, "24h");
        check("Aug 4, 2026 17:30".equals(p24), "prepared 24h = " + p24);
        check(TimeFormat.prepared(iso, ny, null).equals(p12), "prepared null format defaults to 12h");
        check(TimeFormat.prepared(iso, ny, "anything").equals(p12), "prepared unknown format defaults to 12h");
        check(!p12.contains(":45") && p12.indexOf(':') == p12.lastIndexOf(':'), "12h has no seconds");
        check(!p24.contains(":45") && p24.indexOf(':') == p24.lastIndexOf(':'), "24h has no seconds");
        check("".equals(TimeFormat.prepared("", ny, "12h")), "prepared empty input -> empty");

        String utc = TimeFormat.utcMinute(iso);
        check("2026-08-04 21:30 UTC".equals(utc), "utcMinute = " + utc);
        check("".equals(TimeFormat.utcMinute("")), "utcMinute empty input -> empty");

        System.out.println("TimeFormatTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
