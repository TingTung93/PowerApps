package org.commercialtracking;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppConfigTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("commercial-config-test-");
        System.setProperty("commercialtracking.localBase", base.toString());
        AppConfig config = AppConfig.load();
        check(config.localRoot.equals(base.resolve("CommercialTracking")), "spec local root");
        config.sharedRoot = base.resolve("shared");
        config.deviceId = "WS-TEST";
        config.defaultLocation = "Mailroom";
        config.save();
        Path file = base.resolve("CommercialTracking/config/client.json");
        check(Files.isRegularFile(file), "JSON settings location");
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        check(json.trim().startsWith("{"), "JSON settings format");
        AppConfig loaded = AppConfig.load();
        check("WS-TEST".equals(loaded.deviceId), "settings reload");
        check("Mailroom".equals(loaded.defaultLocation), "default location reload");
        System.clearProperty("commercialtracking.localBase");
        System.out.println("AppConfigTest: PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
