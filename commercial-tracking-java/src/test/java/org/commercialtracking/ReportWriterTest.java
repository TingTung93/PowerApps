package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public final class ReportWriterTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("commercialtracking.noDesktop", "true");
        Path root = Files.createTempDirectory("commercial-report-test-");
        List<TrackingEvent> events = new ArrayList<TrackingEvent>();
        events.add(event("PACKAGE_RECEIVED", "2026-03-08T09:59:59Z", "=TRACKING", "Main Receiving"));
        events.add(event("PACKAGE_RECEIVED", "2026-03-08T10:00:00Z", "+IN-RANGE", "Mailroom"));
        events.add(event("PACKAGE_RELEASED", "2026-03-08T11:00:00Z", "OUTBOUND", "Mailroom"));
        Projection projection = new Projection();
        projection.replay(events);
        ReportWriter.Result receiving = new ReportWriter().write(root, "Receiving Activity", "day",
                "America/Los_Angeles", Instant.parse("2026-03-08T10:00:00Z"),
                Instant.parse("2026-03-09T07:00:00Z"), events, projection);
        check(receiving.count == 1, "DST-bounded receiving row count");
        String csv = new String(Files.readAllBytes(receiving.csv), StandardCharsets.UTF_8);
        check(csv.contains("' +IN-RANGE".replace(" ", "")), "spreadsheet-safe included row");
        check(!csv.contains("=TRACKING"), "exclusive lower boundary");
        String html = new String(Files.readAllBytes(receiving.html), StandardCharsets.UTF_8);
        check(html.contains("Reporting Extract — not an audited manifest."), "extract label");
        check(html.contains("America/Los_Angeles"), "operational time zone");
        check(Files.exists(receiving.docx), "report DOCX exists");
        try (ZipFile zip = new ZipFile(receiving.docx.toFile())) {
            check(zip.getEntry("[Content_Types].xml") != null, "docx content-types part");
            check(zip.getEntry("word/document.xml") != null, "docx document part");
        }
        ReportWriter.Result outbound = new ReportWriter().write(root, "Outbound/Custody Activity", "custom",
                "UTC", Instant.parse("2026-03-08T00:00:00Z"), Instant.parse("2026-03-09T00:00:00Z"),
                events, projection);
        check(outbound.count == 1, "outbound event filtering");
        System.out.println("ReportWriterTest: PASS");
    }

    private static TrackingEvent event(String type, String occurred, String tracking, String location) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = type;
        event.occurredUtc = occurred;
        event.recordedUtc = occurred;
        event.trackingNumber = tracking;
        event.location = location;
        event.carrier = "Carrier";
        event.deviceId = "WS-TEST";
        event.actor = "DOMAIN\\user";
        event.status = "PACKAGE_RELEASED".equals(type) ? "PICKED_UP" : "READY_FOR_PICKUP";
        return event;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
