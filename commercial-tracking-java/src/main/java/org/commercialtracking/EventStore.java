package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class EventStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private final Path sharedRoot;
    private final Path pendingRoot;

    public EventStore(Path sharedRoot, Path localRoot) throws IOException {
        this.sharedRoot = sharedRoot;
        this.pendingRoot = localRoot.resolve("pending");
        Files.createDirectories(sharedRoot.resolve("events"));
        Files.createDirectories(sharedRoot.resolve("manifests"));
        Files.createDirectories(pendingRoot);
    }

    public synchronized Path append(TrackingEvent event) throws IOException {
        validate(event);
        String json = JsonFlat.write(event.fields());
        String safeDevice = event.deviceId.replaceAll("[^A-Za-z0-9-]", "_");
        String safeType = event.eventType.replaceAll("[^A-Za-z0-9_]", "_");
        String filename = FILE_TIME.format(Instant.parse(event.occurredUtc)) + "_" + safeDevice + "_"
                + event.eventId + "_" + safeType + ".json";
        Path local = pendingRoot.resolve(event.eventId + ".tmp");
        Files.write(local, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        Path month = sharedRoot.resolve("events")
                .resolve(event.occurredUtc.substring(0, 4))
                .resolve(event.occurredUtc.substring(5, 7));
        Files.createDirectories(month);
        Path partial = month.resolve(filename + ".partial");
        Path target = month.resolve(filename);
        Files.copy(local, partial, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(local);
        return target;
    }

    public synchronized LoadResult loadAll() {
        List<TrackingEvent> events = new ArrayList<TrackingEvent>();
        List<String> errors = new ArrayList<String>();
        Set<String> ids = new HashSet<String>();
        Path eventsRoot = sharedRoot.resolve("events");
        if (!Files.isDirectory(eventsRoot)) return new LoadResult(events, errors);
        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > 1024 * 1024) throw new IOException("File exceeds 1 MB");
                            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                            TrackingEvent event = TrackingEvent.from(JsonFlat.read(json));
                            validate(event);
                            if (ids.add(event.eventId)) events.add(event);
                        } catch (Exception ex) {
                            errors.add(path.getFileName() + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            errors.add("Event scan failed: " + ex.getMessage());
        }
        Collections.sort(events, Comparator.comparing((TrackingEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        return new LoadResult(events, errors);
    }

    public Path getSharedRoot() { return sharedRoot; }

    private static void validate(TrackingEvent event) {
        if (!"1".equals(event.schemaVersion)) throw new IllegalArgumentException("Unsupported schema");
        if (!event.eventId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid event ID");
        if (event.eventType.length() == 0 || event.eventType.length() > 40)
            throw new IllegalArgumentException("Invalid event type");
        Instant.parse(event.occurredUtc);
        if (event.trackingNumber.length() == 0 || event.trackingNumber.length() > 100)
            throw new IllegalArgumentException("Invalid tracking number");
    }

    public static final class LoadResult {
        public final List<TrackingEvent> events;
        public final List<String> errors;
        LoadResult(List<TrackingEvent> events, List<String> errors) {
            this.events = events;
            this.errors = errors;
        }
    }
}
