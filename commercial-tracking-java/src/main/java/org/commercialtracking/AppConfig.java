package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class AppConfig {
    public Path sharedRoot;
    public final Path localRoot;
    public String deviceId;
    public String actor;
    public String scannerMode = "automatic";
    public String scannerTerminator = "Enter";
    public int scannerIdleMs = 120;
    public int scannerBurstMs = 50;
    public int scannerMinimumLength = 6;
    public String activeSessionId = "";
    public boolean soundEnabled = false;
    public String defaultLocation = "Main Receiving";

    private AppConfig(Path localRoot) {
        this.localRoot = localRoot;
    }

    public static AppConfig load() throws IOException {
        String base = System.getProperty("commercialtracking.localBase", "");
        if (base.trim().length() == 0) base = System.getenv("LOCALAPPDATA");
        if (base == null || base.trim().length() == 0) base = System.getProperty("user.home");
        Path local = Paths.get(base, "CommercialTracking");
        Files.createDirectories(local.resolve("config"));
        AppConfig config = new AppConfig(local);
        Path file = local.resolve("config").resolve("client.json");
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            Map<String, String> values = JsonFlat.read(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            for (Map.Entry<String, String> value : values.entrySet())
                props.setProperty(value.getKey(), value.getValue());
        } else {
            Path legacy = Paths.get(base, "CommercialTrackingRC", "client.properties");
            if (Files.isRegularFile(legacy)) {
                try (java.io.InputStream in = Files.newInputStream(legacy)) { props.load(in); }
            }
        }
        String root = props.getProperty("sharedRoot", "");
        config.sharedRoot = root.length() == 0 ? null : Paths.get(root);
        config.deviceId = props.getProperty("deviceId", defaultDevice());
        config.actor = props.getProperty("actor", defaultActor());
        config.scannerMode = props.getProperty("scannerMode", "automatic");
        config.scannerTerminator = props.getProperty("scannerTerminator", "Enter");
        config.scannerIdleMs = integer(props, "scannerIdleMs", 120, 80, 2000);
        config.scannerBurstMs = integer(props, "scannerBurstMs", 50, 10, 500);
        config.scannerMinimumLength = integer(props, "scannerMinimumLength", 6, 4, 100);
        config.activeSessionId = props.getProperty("activeSessionId", "");
        if (config.activeSessionId.length() == 0) config.activeSessionId = java.util.UUID.randomUUID().toString();
        config.soundEnabled = Boolean.parseBoolean(props.getProperty("soundEnabled", "false"));
        config.defaultLocation = props.getProperty("defaultLocation", "Main Receiving");
        if (!Files.isRegularFile(file) && !props.isEmpty()) config.save();
        return config;
    }

    public void save() throws IOException {
        Properties props = new Properties();
        props.setProperty("sharedRoot", sharedRoot == null ? "" : sharedRoot.toString());
        props.setProperty("deviceId", deviceId);
        props.setProperty("actor", actor);
        props.setProperty("scannerMode", scannerMode);
        props.setProperty("scannerTerminator", scannerTerminator);
        props.setProperty("scannerIdleMs", String.valueOf(scannerIdleMs));
        props.setProperty("scannerBurstMs", String.valueOf(scannerBurstMs));
        props.setProperty("scannerMinimumLength", String.valueOf(scannerMinimumLength));
        props.setProperty("activeSessionId", activeSessionId);
        props.setProperty("soundEnabled", String.valueOf(soundEnabled));
        props.setProperty("defaultLocation", defaultLocation);
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String name : props.stringPropertyNames()) values.put(name, props.getProperty(name));
        Path configRoot = localRoot.resolve("config");
        Files.createDirectories(configRoot);
        Files.write(configRoot.resolve("client.json"),
                JsonFlat.write(values).getBytes(StandardCharsets.UTF_8));
    }

    private static String defaultDevice() {
        String machine = System.getenv("COMPUTERNAME");
        if (machine == null || machine.length() == 0) machine = "DEVICE";
        return machine.toUpperCase()
                .replaceAll("[^A-Z0-9-]", "-");
    }

    private static String defaultActor() {
        String user = System.getProperty("user.name", "unknown");
        String domain = System.getenv("USERDOMAIN");
        return domain == null || domain.trim().length() == 0 ? user : domain + "\\" + user;
    }

    private static int integer(Properties values, String key, int fallback, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(values.getProperty(key, String.valueOf(fallback)));
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException ex) { return fallback; }
    }
}
