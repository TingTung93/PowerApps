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
        Map<String, String> first = settings("Main Receiving", "UTC", "5", "false");
        manager.save(first);
        Map<String, String> second = settings("Mailroom", "America/Los_Angeles", "10", "true");
        manager.save(second);
        check("Mailroom".equals(manager.reload().values.get("locations")), "new settings active");
        Files.write(root.resolve("configuration").resolve("application.json"), "{broken".getBytes(StandardCharsets.UTF_8));
        SharedConfigManager.State retained = manager.reload();
        check("Mailroom".equals(retained.values.get("locations")), "last valid settings retained");
        check(retained.error.length() > 0, "invalid synchronized settings reported");
        manager.rollback();
        check("Main Receiving".equals(manager.reload().values.get("locations")), "prior version restored");
        boolean invalid = false;
        try { manager.save(settings("", "Not/AZone", "0", "maybe")); }
        catch (IllegalArgumentException ex) { invalid = true; }
        check(invalid, "invalid proposal rejected");
        System.out.println("SharedConfigManagerTest: PASS");
    }

    private static Map<String, String> settings(String locations, String zone, String pending, String retain) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("schemaVersion", "1");
        values.put("locations", locations);
        values.put("operationalTimeZone", zone);
        values.put("pendingAttentionMinutes", pending);
        values.put("retainRawBarcode", retain);
        return values;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
