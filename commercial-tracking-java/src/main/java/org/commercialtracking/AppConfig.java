package org.commercialtracking;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

public final class AppConfig {
    public Path sharedRoot;
    public final Path localRoot;
    public String deviceId;
    public String actor;

    private AppConfig(Path localRoot) {
        this.localRoot = localRoot;
    }

    public static AppConfig load() throws IOException {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.trim().length() == 0) base = System.getProperty("user.home");
        Path local = Paths.get(base, "CommercialTrackingRC");
        Files.createDirectories(local);
        AppConfig config = new AppConfig(local);
        Path file = local.resolve("client.properties");
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { props.load(in); }
        }
        String root = props.getProperty("sharedRoot", "");
        config.sharedRoot = root.length() == 0 ? null : Paths.get(root);
        config.deviceId = props.getProperty("deviceId", defaultDevice());
        config.actor = props.getProperty("actor", System.getProperty("user.name", "unknown"));
        return config;
    }

    public void save() throws IOException {
        Properties props = new Properties();
        props.setProperty("sharedRoot", sharedRoot == null ? "" : sharedRoot.toString());
        props.setProperty("deviceId", deviceId);
        props.setProperty("actor", actor);
        try (OutputStream out = Files.newOutputStream(localRoot.resolve("client.properties"))) {
            props.store(out, "Commercial Tracking RC client settings");
        }
    }

    private static String defaultDevice() {
        String machine = System.getenv("COMPUTERNAME");
        if (machine == null || machine.length() == 0) machine = "DEVICE";
        return (machine + "-" + UUID.randomUUID().toString().substring(0, 6)).toUpperCase()
                .replaceAll("[^A-Z0-9-]", "-");
    }
}
