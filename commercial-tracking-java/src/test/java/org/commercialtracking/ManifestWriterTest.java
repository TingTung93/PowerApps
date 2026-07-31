package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ManifestWriterTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("commercialtracking.noDesktop", "true");
        Path root = Files.createTempDirectory("commercial-manifest-test-");
        verify(root, 1, "detailed");
        verify(root, 20, "detailed");
        verify(root, 21, "high-density");
        verify(root, 50, "high-density");
        verify(root, 100, "high-density");
        ManifestWriter.Result custody = new ManifestWriter().write(root, "CU-TEST", "custody", "J. Smith", events(2));
        String custodyHtml = new String(Files.readAllBytes(custody.htmlPath), StandardCharsets.UTF_8);
        check(custodyHtml.contains("Recipient Custody Manifest"), "custody heading");
        check(custodyHtml.contains("J. Smith"), "custody recipient");
        check(custody.checksum.equals(ManifestWriter.checksum(custody.path)), "stable checksum");
        System.out.println("ManifestWriterTest: PASS");
    }

    private static void verify(Path root, int count, String layout) throws Exception {
        String id = "IN-TEST-" + count;
        ManifestWriter.Result result = new ManifestWriter().write(root, id, "inbound", "Main Receiving", events(count));
        String html = new String(Files.readAllBytes(result.htmlPath), StandardCharsets.UTF_8);
        check(html.contains("class=\"" + layout + "\""), count + " package layout");
        check(html.contains(id), "manifest identity");
        check(html.contains("signature"), "signature certification");
        byte[] pdf = Files.readAllBytes(result.path);
        check(new String(pdf, 0, 8, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"), "valid PDF header");
    }

    private static List<TrackingEvent> events(int count) {
        List<TrackingEvent> values = new ArrayList<TrackingEvent>();
        for (int i = 0; i < count; i++) {
            TrackingEvent event = new TrackingEvent();
            event.eventType = "PACKAGE_RECEIVED";
            event.trackingNumber = "TRACKING-" + String.format("%04d", i);
            event.carrier = "Carrier";
            event.location = "Main Receiving";
            event.deviceId = "WS-TEST";
            values.add(event);
        }
        return values;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
