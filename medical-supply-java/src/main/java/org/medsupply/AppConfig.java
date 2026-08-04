package org.medsupply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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
    public boolean scannerAutoFocus = true;
    public boolean scannerSound = true;
    public boolean scannerAutoSubmit = false;
    public int scannerDefaultQuantity = 1;
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
        // Attribution is always derived from the authenticated OS session. Never trust persisted input.
        config.actor = defaultActor();
        config.gudidEnabled = !"false".equals(Json.str(values, "gudidEnabled"));
        config.gudidEndpoint = orDefault(Json.str(values, "gudidEndpoint"), config.gudidEndpoint);
        config.reorderWindowDays = intOr(values, "reorderWindowDays", 90, 7, 365);
        config.reorderLeadDays = intOr(values, "reorderLeadDays", 7, 0, 120);
        config.reorderSafetyDays = intOr(values, "reorderSafetyDays", 7, 0, 120);
        config.reorderCoverageDays = intOr(values, "reorderCoverageDays", 28, 1, 365);
        config.staleDays = intOr(values, "staleDays", 30, 1, 365);
        config.scannerMinimumLength = intOr(values, "scannerMinimumLength", 5, 4, 100);
        config.scannerAutoFocus = !"false".equals(Json.str(values, "scannerAutoFocus"));
        config.scannerSound = !"false".equals(Json.str(values, "scannerSound"));
        config.scannerAutoSubmit = "true".equals(Json.str(values, "scannerAutoSubmit"));
        config.scannerDefaultQuantity = intOr(values, "scannerDefaultQuantity", 1, 1, 9999);
        config.activeSessionId = orDefault(Json.str(values, "activeSessionId"), UUID.randomUUID().toString());
        if (!Files.isRegularFile(file)) config.save();
        return config;
    }

    public void save() throws IOException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("sharedRoot", sharedRoot == null ? "" : sharedRoot.toString());
        values.put("deviceId", deviceId);
        values.put("gudidEnabled", gudidEnabled ? "true" : "false");
        values.put("gudidEndpoint", gudidEndpoint);
        values.put("reorderWindowDays", Integer.valueOf(reorderWindowDays));
        values.put("reorderLeadDays", Integer.valueOf(reorderLeadDays));
        values.put("reorderSafetyDays", Integer.valueOf(reorderSafetyDays));
        values.put("reorderCoverageDays", Integer.valueOf(reorderCoverageDays));
        values.put("staleDays", Integer.valueOf(staleDays));
        values.put("scannerMinimumLength", Integer.valueOf(scannerMinimumLength));
        values.put("scannerAutoFocus", Boolean.valueOf(scannerAutoFocus));
        values.put("scannerSound", Boolean.valueOf(scannerSound));
        values.put("scannerAutoSubmit", Boolean.valueOf(scannerAutoSubmit));
        values.put("scannerDefaultQuantity", Integer.valueOf(scannerDefaultQuantity));
        values.put("activeSessionId", activeSessionId);
        Path configRoot = localRoot.resolve("config");
        Files.createDirectories(configRoot);
        Path target = configRoot.resolve("client.json");
        Path temporary = configRoot.resolve("client.json.tmp");
        Files.write(temporary, Json.write(values).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
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
        try {
            // `user.name` and USERDOMAIN are caller-controlled. The Windows system `whoami`
            // executable obtains its value from the process access token instead.
            Path whoami = Paths.get("C:\\Windows\\System32\\whoami.exe");
            if (!Files.isRegularFile(whoami))
                throw new IllegalStateException("Windows identity provider is unavailable");
            Process process = new ProcessBuilder(whoami.toString()).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[256];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > 4096) throw new IllegalStateException("Windows identity response is too large");
                    output.write(buffer, 0, read);
                }
            }
            if (process.waitFor() != 0) throw new IllegalStateException("Windows identity lookup failed");
            String actor = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            if (actor.length() == 0 || actor.length() > 200 || actor.indexOf('\\') < 1)
                throw new IllegalStateException("Windows returned an invalid authenticated principal");
            return actor;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not obtain the authenticated Windows principal", ex);
        }
    }
}
