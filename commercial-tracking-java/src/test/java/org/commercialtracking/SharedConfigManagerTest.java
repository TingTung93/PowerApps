package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedConfigManagerTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("commercial-config-test-");
        SharedConfigManager manager = new SharedConfigManager(root);
        Map<String, String> first = settings("Main Receiving", "12h", "5", "false");
        manager.save(first);
        Map<String, String> second = settings("Mailroom", "24h", "10", "true");
        manager.save(second);
        check("Mailroom".equals(manager.reload().values.get("locations")), "new settings active");
        check("24h".equals(manager.reload().values.get("timeFormat")), "time format persisted");
        Files.write(root.resolve("configuration").resolve("application.json"), "{broken".getBytes(StandardCharsets.UTF_8));
        SharedConfigManager.State retained = manager.reload();
        check("Mailroom".equals(retained.values.get("locations")), "last valid settings retained");
        check(retained.error.length() > 0, "invalid synchronized settings reported");
        manager.rollback();
        check("Main Receiving".equals(manager.reload().values.get("locations")), "prior version restored");

        // A configuration WITHOUT operationalTimeZone must validate (the setting was removed).
        SharedConfigManager.validate(settings("Dock", "12h", "5", "false"));

        // A configuration still carrying a legacy operationalTimeZone must be accepted (ignored, not rejected).
        Map<String, String> legacy = settings("Dock", "12h", "5", "false");
        legacy.put("operationalTimeZone", "Not/AZone");
        SharedConfigManager.validate(legacy);

        // timeFormat gate: invalid rejected; both 12h and 24h accepted.
        boolean rejectedFormat = false;
        try { SharedConfigManager.validate(settings("Dock", "36h", "5", "false")); }
        catch (IllegalArgumentException ex) { rejectedFormat = true; }
        check(rejectedFormat, "invalid time format rejected");
        SharedConfigManager.validate(settings("Dock", "12h", "5", "false"));
        SharedConfigManager.validate(settings("Dock", "24h", "5", "false"));

        boolean invalid = false;
        try { manager.save(settings("", "12h", "0", "maybe")); }
        catch (IllegalArgumentException ex) { invalid = true; }
        check(invalid, "invalid proposal rejected");
        System.out.println("SharedConfigManagerTest: PASS");
    }

    private static Map<String, String> settings(String locations, String timeFormat, String pending, String retain) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("schemaVersion", "1");
        values.put("locations", locations);
        values.put("timeFormat", timeFormat);
        values.put("pendingAttentionMinutes", pending);
        values.put("retainRawBarcode", retain);
        return values;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
