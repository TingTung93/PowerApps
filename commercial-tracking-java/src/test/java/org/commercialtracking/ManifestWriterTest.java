package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ManifestWriterTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("commercialtracking.noDesktop", "true");
        Path root = Files.createTempDirectory("commercial-manifest-test-");
        verify(root, 1);
        verify(root, 20);
        verify(root, 21);
        verify(root, 50);
        verify(root, 100);
        custodyGrouping(root);
        timeFormatting(root);
        System.out.println("ManifestWriterTest: PASS");
    }

    private static void verify(Path root, int count) throws Exception {
        boolean detailed = count <= 20;
        String id = "IN-TEST-" + count;
        ManifestWriter.Result result = new ManifestWriter()
                .write(root, id, "inbound", "Main Receiving", "2026-07-15", "24h", events(count));

        String html = new String(Files.readAllBytes(result.htmlPath), StandardCharsets.UTF_8);
        check(html.contains("class=\"" + (detailed ? "detailed" : "high-density") + "\""), count + " layout class");
        check(html.contains(id), "manifest identity in html");
        check(html.contains("signature"), "signature certification");
        check(html.contains("2026-07-15"), "scoped date in html");
        check(html.contains("<svg"), "header QR svg present");
        int svg = countOccurrences(html, "<svg");
        check(svg == (detailed ? count + 1 : 1), "html svg count = " + svg + " for " + count);
        if (!detailed) check(html.contains("omitted at high density"), "high-density QR omission note");

        check(result.path.toString().endsWith(".docx"), "result path is .docx");
        try (ZipFile zip = new ZipFile(result.path.toFile())) {
            check(zip.getEntry("word/document.xml") != null, "docx document part");
            int media = countMedia(zip);
            check(media == (detailed ? count + 1 : 1), "docx media count = " + media + " for " + count);
            String doc = new String(readAll(zip, zip.getEntry("word/document.xml")), StandardCharsets.UTF_8);
            check(doc.contains("2026-07-15"), "scoped date in docx");
            check(doc.contains(id), "manifest identity in docx");
        }
        check(result.checksum.equals(ManifestWriter.checksum(result.path)), "checksum computed over docx");
    }

    private static void custodyGrouping(Path root) throws Exception {
        List<TrackingEvent> mixed = new ArrayList<TrackingEvent>();
        mixed.add(evt("CU-1", "Bravo"));
        mixed.add(evt("CU-2", "Alpha"));
        mixed.add(evt("CU-3", ""));
        mixed.add(evt("CU-4", "Alpha"));
        ManifestWriter.Result custody = new ManifestWriter()
                .write(root, "CU-TEST", "custody", "All recipients", "2026-07-15", "12h", mixed);
        String html = new String(Files.readAllBytes(custody.htmlPath), StandardCharsets.UTF_8);
        check(html.contains("Recipient Custody Manifest"), "custody heading");
        check(html.contains("<h2>Alpha</h2>"), "Alpha section heading");
        check(html.contains("<h2>Bravo</h2>"), "Bravo section heading");
        check(html.contains("<h2>Unassigned</h2>"), "Unassigned section heading");
        int alpha = html.indexOf("<h2>Alpha</h2>");
        int bravo = html.indexOf("<h2>Bravo</h2>");
        int unassigned = html.indexOf("<h2>Unassigned</h2>");
        check(alpha < bravo && bravo < unassigned, "recipients alphabetical, Unassigned last");
        try (ZipFile zip = new ZipFile(custody.path.toFile())) {
            String doc = new String(readAll(zip, zip.getEntry("word/document.xml")), StandardCharsets.UTF_8);
            check(doc.contains("Unassigned"), "docx custody Unassigned section");
            check(doc.contains("Alpha") && doc.contains("Bravo"), "docx custody recipient sections");
        }
    }

    private static void timeFormatting(Path root) throws Exception {
        // TimeFormat (Plan 4) renders 12h with AM/PM, 24h without, and drops seconds.
        String p12 = prepared(new String(Files.readAllBytes(new ManifestWriter()
                .write(root, "IN-12H", "inbound", "Main Receiving", "2026-07-15", "12h", events(1)).htmlPath),
                StandardCharsets.UTF_8));
        String p24 = prepared(new String(Files.readAllBytes(new ManifestWriter()
                .write(root, "IN-24H", "inbound", "Main Receiving", "2026-07-15", "24h", events(1)).htmlPath),
                StandardCharsets.UTF_8));
        check(p12.contains("AM") || p12.contains("PM"), "12h prepared line has meridiem: " + p12);
        check(!(p24.contains("AM") || p24.contains("PM")), "24h prepared line has no meridiem: " + p24);
        check(!p12.matches(".*\\d:\\d\\d:\\d\\d.*"), "12h prepared line has no seconds: " + p12);
        check(!p24.matches(".*\\d:\\d\\d:\\d\\d.*"), "24h prepared line has no seconds: " + p24);
    }

    private static List<TrackingEvent> events(int count) {
        List<TrackingEvent> values = new ArrayList<TrackingEvent>();
        for (int i = 0; i < count; i++) values.add(evt("TRACKING-" + String.format("%04d", i), ""));
        return values;
    }

    private static TrackingEvent evt(String tracking, String recipient) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_RECEIVED";
        event.trackingNumber = tracking;
        event.carrier = "Carrier";
        event.location = "Main Receiving";
        event.recipient = recipient;
        event.deviceId = "WS-TEST";
        return event;
    }

    private static String prepared(String html) {
        String marker = "class=\"prepared\">";
        int a = html.indexOf(marker);
        if (a < 0) throw new AssertionError("prepared span missing");
        a += marker.length();
        return html.substring(a, html.indexOf("</span>", a));
    }

    private static int countOccurrences(String value, String sub) {
        int n = 0, i = 0;
        while ((i = value.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    private static int countMedia(ZipFile zip) {
        int n = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("word/media/image") && name.endsWith(".png")) n++;
        }
        return n;
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws Exception {
        java.io.InputStream in = zip.getInputStream(entry);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        return out.toByteArray();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
