package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppServiceSettingsTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-settings");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();
        AppService service = new AppService(config, null);

        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("deviceId", "COUNT-CART-1");
        values.put("actor", "Supply Team");
        values.put("gudidEnabled", "false");
        values.put("reorderWindowDays", "60");
        values.put("reorderLeadDays", "10");
        values.put("reorderSafetyDays", "5");
        values.put("reorderCoverageDays", "30");
        values.put("staleDays", "45");
        values.put("scannerMinimumLength", "8");
        service.updateSettings(values);

        Map<String, Object> settings = Json.asMap(
                service.snapshot(Instant.parse("2026-08-04T00:00:00Z")).get("settings"));
        check("COUNT-CART-1".equals(Json.str(settings, "deviceId")), "device");
        check("60".equals(Json.str(settings, "reorderWindowDays")), "window");
        check(Boolean.FALSE.equals(settings.get("gudidEnabled")), "gudid disabled");

        AppConfig reloaded = AppConfig.load();
        check(reloaded.staleDays == 45, "persisted stale days");
        check(reloaded.scannerMinimumLength == 8, "persisted scanner length");

        boolean rejected = false;
        values.put("reorderWindowDays", "2");
        try {
            service.updateSettings(values);
        } catch (AppService.BadRequest ex) {
            rejected = true;
        }
        check(rejected, "invalid range rejected");
        System.out.println("AppServiceSettingsTest: PASS");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
