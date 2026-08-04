package org.medsupply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AppConfig {
    public Path sharedRoot;
    public final Path localRoot;
    public String deviceId;
    public String actor;
    public boolean gudidEnabled = true;
    public String gudidEndpoint = "https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json";
    public int reorderWindowDays = 90;
    public int reorderLeadDays = 7;
    public int reorderSafetyDays = 7;
    public int reorderCoverageDays = 28;
    public int staleDays = 30;
    public int scannerMinimumLength = 5;
    public String activeSessionId = "";

    private AppConfig(Path localRoot) { this.localRoot = localRoot; }

    public static AppConfig load() throws IOException {
        String base = System.getProperty("medsupply.localBase", "");
        if (base.trim().length() == 0) base = System.getenv("LOCALAPPDATA");
        if (base == null || base.trim().length() == 0) base = System.getProperty("user.home");
        Path local = Paths.get(base, "MedicalSupply");
        Files.createDirectories(local.resolve("config"));
        AppConfig config = new AppConfig(local);
        Path file = local.resolve("config").resolve("client.json");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (Files.isRegularFile(file))
            values = Json.asMap(Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));

        String root = Json.str(values, "sharedRoot");
        config.sharedRoot = root.length() == 0 ? null : Paths.get(root);
        config.deviceId = orDefault(Json.str(values, "deviceId"), defaultDevice());
        config.actor = orDefault(Json.str(values, "actor"), defaultActor());
        config.gudidEnabled = !"false".equals(Json.str(values, "gudidEnabled"));
        config.gudidEndpoint = orDefault(Json.str(values, "gudidEndpoint"), config.gudidEndpoint);
        config.reorderWindowDays = intOr(values, "reorderWindowDays", 90, 7, 365);
        config.reorderLeadDays = intOr(values, "reorderLeadDays", 7, 0, 120);
        config.reorderSafetyDays = intOr(values, "reorderSafetyDays", 7, 0, 120);
        config.reorderCoverageDays = intOr(values, "reorderCoverageDays", 28, 1, 365);
        config.staleDays = intOr(values, "staleDays", 30, 1, 365);
        config.scannerMinimumLength = intOr(values, "scannerMinimumLength", 5, 4, 100);
        config.activeSessionId = orDefault(Json.str(values, "activeSessionId"), UUID.randomUUID().toString());
        if (!Files.isRegularFile(file)) config.save();
        return config;
    }

    public void save() throws IOException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("sharedRoot", sharedRoot == null ? "" : sharedRoot.toString());
        values.put("deviceId", deviceId);
        values.put("actor", actor);
        values.put("gudidEnabled", gudidEnabled ? "true" : "false");
        values.put("gudidEndpoint", gudidEndpoint);
        values.put("reorderWindowDays", Integer.valueOf(reorderWindowDays));
        values.put("reorderLeadDays", Integer.valueOf(reorderLeadDays));
        values.put("reorderSafetyDays", Integer.valueOf(reorderSafetyDays));
        values.put("reorderCoverageDays", Integer.valueOf(reorderCoverageDays));
        values.put("staleDays", Integer.valueOf(staleDays));
        values.put("scannerMinimumLength", Integer.valueOf(scannerMinimumLength));
        values.put("activeSessionId", activeSessionId);
        Path configRoot = localRoot.resolve("config");
        Files.createDirectories(configRoot);
        Files.write(configRoot.resolve("client.json"), Json.write(values).getBytes(StandardCharsets.UTF_8));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static int intOr(Map<String, Object> values, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(Json.str(values, key));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) { return fallback; }
    }

    private static String defaultDevice() {
        String machine = System.getenv("COMPUTERNAME");
        if (machine == null || machine.length() == 0) machine = "DEVICE";
        return machine.toUpperCase().replaceAll("[^A-Z0-9-]", "-");
    }

    private static String defaultActor() {
        String user = System.getProperty("user.name", "unknown");
        String domain = System.getenv("USERDOMAIN");
        return domain == null || domain.trim().length() == 0 ? user : domain + "\\" + user;
    }
}
