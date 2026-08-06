package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Shared, append-only address book. Each update is independently sync-safe. */
public final class AddressBookStore {
    private final Path eventsRoot;

    public AddressBookStore(Path sharedRoot) throws IOException {
        eventsRoot = sharedRoot.resolve("address-book").resolve("events");
        Files.createDirectories(eventsRoot);
    }

    public synchronized List<Map<String, String>> load() {
        Map<String, Map<String, String>> latest = new LinkedHashMap<String, Map<String, String>>();
        if (!Files.isDirectory(eventsRoot)) return new ArrayList<Map<String, String>>();
        try (java.util.stream.Stream<Path> files = Files.list(eventsRoot)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    Map<String, String> entry = JsonFlat.read(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                    String key = normalize(entry.get("name"));
                    Map<String, String> current = latest.get(key);
                    if (key.length() > 0 && (current == null || order(entry).compareTo(order(current)) > 0)) latest.put(key, entry);
                } catch (Exception ignored) { /* A partially synchronized entry is retried on the next state refresh. */ }
            });
        } catch (IOException ignored) { }
        List<Map<String, String>> values = new ArrayList<Map<String, String>>(latest.values());
        Collections.sort(values, Comparator.comparing(value -> value.get("name").toLowerCase(Locale.ROOT)));
        return values;
    }

    public synchronized Map<String, String> save(String name, String department, String contactInfo, String notes) throws IOException {
        name = clean(name, 200);
        if (name.length() == 0) throw new IllegalArgumentException("Address book name is required.");
        Map<String, String> existing = find(name);
        Map<String, String> entry = new LinkedHashMap<String, String>();
        entry.put("entryId", UUID.randomUUID().toString());
        entry.put("name", name);
        entry.put("department", merge(department, existing, "department", 200));
        entry.put("contactInfo", merge(contactInfo, existing, "contactInfo", 500));
        entry.put("notes", merge(notes, existing, "notes", 2000));
        entry.put("updatedUtc", Instant.now().toString());
        Path target = eventsRoot.resolve(entry.get("updatedUtc").replaceAll("[^0-9]", "") + "-" + entry.get("entryId") + ".json");
        Path pending = eventsRoot.resolve("." + entry.get("entryId") + ".tmp");
        Files.write(pending, JsonFlat.write(entry).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(pending, target);
        }
        return entry;
    }

    private Map<String, String> find(String name) {
        String key = normalize(name);
        for (Map<String, String> entry : load()) if (normalize(entry.get("name")).equals(key)) return entry;
        return null;
    }

    private static String merge(String value, Map<String, String> existing, String key, int max) {
        String cleaned = clean(value, max);
        return cleaned.length() == 0 && existing != null ? clean(existing.get(key), max) : cleaned;
    }

    private static String clean(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Address book value is too long.");
        return result;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String order(Map<String, String> value) { return value.get("updatedUtc") + "|" + value.get("entryId"); }
}
