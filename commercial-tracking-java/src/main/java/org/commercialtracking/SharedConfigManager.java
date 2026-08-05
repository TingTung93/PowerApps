package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SharedConfigManager {
    private final Path configurationRoot;
    private final Path target;
    private Map<String, String> lastValid = defaults();
    private String error = "";

    public SharedConfigManager(Path sharedRoot) throws IOException {
        configurationRoot = sharedRoot.resolve("configuration");
        target = configurationRoot.resolve("application.json");
        Files.createDirectories(configurationRoot.resolve("history"));
        reload();
    }

    public synchronized State reload() {
        if (!Files.isRegularFile(target)) return state();
        try {
            Map<String, String> values = JsonFlat.read(
                    new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
            validate(values);
            lastValid = new LinkedHashMap<String, String>(values);
            error = "";
        } catch (Exception ex) {
            error = "Shared settings are invalid; the last valid values remain active: " + ex.getMessage();
        }
        return state();
    }

    public synchronized State save(Map<String, String> proposed) throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>(proposed);
        values.put("schemaVersion", "1");
        values.put("revisedUtc", Instant.now().toString());
        validate(values);
        if (error.length() == 0) backup();
        write(values);
        lastValid = values;
        error = "";
        return state();
    }

    public synchronized State rollback() throws IOException {
        List<Path> versions = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configurationRoot.resolve("history"), "*.json")) {
            for (Path path : stream) versions.add(path);
        }
        if (versions.isEmpty()) throw new IOException("No prior shared-settings version is available.");
        versions.sort(Comparator.comparing(path -> path.getFileName().toString()));
        Path prior = versions.get(versions.size() - 1);
        Map<String, String> values = JsonFlat.read(new String(Files.readAllBytes(prior), StandardCharsets.UTF_8));
        validate(values);
        if (error.length() == 0) backup();
        write(values);
        lastValid = values;
        error = "";
        return state();
    }

    private void backup() throws IOException {
        if (!Files.isRegularFile(target)) return;
        String name = "application-" + Instant.now().toString().replaceAll("[^0-9]", "") + ".json";
        Files.copy(target, configurationRoot.resolve("history").resolve(name));
    }

    private void write(Map<String, String> values) throws IOException {
        Path partial = configurationRoot.resolve("application.json.partial");
        Files.write(partial, JsonFlat.write(values).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void validate(Map<String, String> values) {
        if (!"1".equals(value(values, "schemaVersion", "1"))) throw new IllegalArgumentException("Unsupported settings schema.");
        String locations = value(values, "locations", "");
        if (locations.length() == 0 || locations.length() > 500) throw new IllegalArgumentException("At least one valid location is required.");
        String timeFormat = value(values, "timeFormat", "12h");
        if (!"12h".equals(timeFormat) && !"24h".equals(timeFormat)) throw new IllegalArgumentException("Time format must be 12h or 24h.");
        int pending = integer(values, "pendingAttentionMinutes", 5);
        if (pending < 1 || pending > 1440) throw new IllegalArgumentException("Pending attention threshold is outside the safe range.");
        String retain = value(values, "retainRawBarcode", "false");
        if (!"true".equals(retain) && !"false".equals(retain)) throw new IllegalArgumentException("Invalid raw-barcode retention value.");
    }

    private State state() { return new State(new LinkedHashMap<String, String>(lastValid), error); }

    private static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("schemaVersion", "1");
        values.put("locations", "Main Receiving|Loading Dock|Mailroom|Warehouse");
        values.put("timeFormat", "12h");
        values.put("pendingAttentionMinutes", "5");
        values.put("retainRawBarcode", "false");
        return values;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        try { return Integer.parseInt(value(values, key, String.valueOf(fallback))); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid " + key + "."); }
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    public static final class State {
        public final Map<String, String> values;
        public final String error;
        State(Map<String, String> values, String error) { this.values = values; this.error = error; }
    }
}
