package org.commercialtracking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deterministic, host-zone-aware date/time formatting. No seconds in human-facing times. */
public final class TimeFormat {
    private TimeFormat() { }

    private static final DateTimeFormatter PREPARED_24 = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm", Locale.US);
    private static final DateTimeFormatter PREPARED_12 = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a", Locale.US);
    private static final DateTimeFormatter UTC_MINUTE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'", Locale.US);

    /** yyyy-MM-dd in the supplied zone; "" when the instant is null/empty. */
    public static String date(String instantIso, ZoneId zone) {
        if (instantIso == null || instantIso.length() == 0) return "";
        return Instant.parse(instantIso).atZone(zone).toLocalDate().toString();
    }

    /** Legible date-time with NO seconds; 24h uses HH:mm, otherwise 12h with AM/PM. "" when null/empty. */
    public static String prepared(String instantIso, ZoneId zone, String timeFormat) {
        if (instantIso == null || instantIso.length() == 0) return "";
        ZonedDateTime zoned = Instant.parse(instantIso).atZone(zone);
        DateTimeFormatter formatter = "24h".equals(timeFormat) ? PREPARED_24 : PREPARED_12;
        return formatter.format(zoned);
    }

    /** yyyy-MM-dd HH:mm 'UTC' at minute precision. "" when null/empty. */
    public static String utcMinute(String instantIso) {
        if (instantIso == null || instantIso.length() == 0) return "";
        return Instant.parse(instantIso).atZone(ZoneOffset.UTC).format(UTC_MINUTE);
    }
}
