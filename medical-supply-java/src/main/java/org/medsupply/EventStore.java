package org.medsupply;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class EventStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private final Path sharedRoot;
    private final Path pendingRoot;
    private final LocalEventIndex index;

    public EventStore(Path sharedRoot, Path localRoot) throws IOException {
        this.sharedRoot = sharedRoot;
        this.pendingRoot = localRoot.resolve("pending");
        this.index = new LocalEventIndex(localRoot);
        Files.createDirectories(sharedRoot.resolve("events"));
        Files.createDirectories(sharedRoot.resolve("reports"));
        Files.createDirectories(sharedRoot.resolve("configuration"));
        Files.createDirectories(sharedRoot.resolve("diagnostics"));
        Files.createDirectories(pendingRoot);
    }

    public synchronized Path append(SupplyEvent event) throws IOException {
        validate(event);
        String json = SupplyEventJson.write(event);
        Path local = pendingRoot.resolve(event.eventId + ".tmp");
        Files.write(local, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        force(local);
        Path published = finalizeShared(event, local);
        Files.deleteIfExists(local);
        return published;
    }

    private Path finalizeShared(SupplyEvent event, Path local) throws IOException {
        String safeDevice = event.deviceId.replaceAll("[^A-Za-z0-9-]", "_");
        String safeType = event.eventType.replaceAll("[^A-Za-z0-9_]", "_");
        String filename = FILE_TIME.format(Instant.parse(event.occurredUtc)) + "_" + safeDevice + "_"
                + event.eventId + "_" + safeType + ".json";
        Path month = sharedRoot.resolve("events")
                .resolve(event.occurredUtc.substring(0, 4))
                .resolve(event.occurredUtc.substring(5, 7));
        Files.createDirectories(month);
        Path partial = month.resolve(filename + ".partial");
        Path target = month.resolve(filename);
        Files.copy(local, partial, StandardCopyOption.REPLACE_EXISTING);
        force(partial);
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public synchronized RetryResult retryPending() {
        int recovered = 0;
        List<String> errors = new ArrayList<String>();
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(pendingRoot, "*.tmp")) {
            for (Path local : pending) {
                try {
                    if (Files.size(local) > 1024 * 1024) throw new IOException("Pending file exceeds 1 MB");
                    SupplyEvent event = SupplyEventJson.read(
                            new String(Files.readAllBytes(local), StandardCharsets.UTF_8));
                    validate(event);
                    finalizeShared(event, local);
                    Files.deleteIfExists(local);
                    recovered++;
                } catch (Exception ex) {
                    errors.add(local.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Pending scan failed: " + ex.getMessage());
        }
        return new RetryResult(recovered, errors);
    }

    public synchronized LoadResult loadAll() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        List<String> errors = new ArrayList<String>();
        Set<String> ids = new HashSet<String>();
        Map<String, String> hashes = new HashMap<String, String>();
        Map<String, LocalEventIndex.Entry> currentIndex = new HashMap<String, LocalEventIndex.Entry>();
        Path eventsRoot = sharedRoot.resolve("events");
        if (!Files.isDirectory(eventsRoot)) return new LoadResult(events, errors);
        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > 1024 * 1024) throw new IOException("File exceeds 1 MB");
                            String relative = sharedRoot.relativize(path).toString();
                            long size = Files.size(path);
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            LocalEventIndex.Entry cached = index.find(relative, size, modified);
                            String json = cached == null
                                    ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : cached.json;
                            SupplyEvent event = SupplyEventJson.read(json);
                            validate(event);
                            String hash = sha256(SupplyEventJson.write(event).getBytes(StandardCharsets.UTF_8));
                            currentIndex.put(relative, new LocalEventIndex.Entry(relative, size, modified, hash, json));
                            if (ids.add(event.eventId)) {
                                hashes.put(event.eventId, hash);
                                events.add(event);
                            } else if (!hash.equals(hashes.get(event.eventId))) {
                                errors.add(path.getFileName() + ": duplicate event ID has different content");
                            }
                        } catch (Exception ex) {
                            errors.add(path.getFileName() + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            errors.add("Event scan failed: " + ex.getMessage());
        }
        Collections.sort(events, Comparator.comparing((SupplyEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.recordedUtc).thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        try { index.replace(currentIndex); } catch (IOException ex) {
            errors.add("Local index update failed: " + ex.getMessage());
        }
        return new LoadResult(events, errors);
    }

    public Path getSharedRoot() { return sharedRoot; }

    public int pendingCount() {
        int count = 0;
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(pendingRoot, "*.tmp")) {
            for (Path ignored : pending) count++;
        } catch (IOException ignored) { }
        return count;
    }

    public void clearIndex() throws IOException { index.clear(); }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : hash) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static void validate(SupplyEvent event) {
        if (!"1".equals(event.schemaVersion)) throw new IllegalArgumentException("Unsupported schema");
        if (!event.eventId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid event ID");
        if (event.eventType.length() == 0 || event.eventType.length() > 40)
            throw new IllegalArgumentException("Invalid event type");
        Instant.parse(event.occurredUtc);
    }

    public static final class LoadResult {
        public final List<SupplyEvent> events;
        public final List<String> errors;
        LoadResult(List<SupplyEvent> events, List<String> errors) {
            this.events = events;
            this.errors = errors;
        }
    }

    public static final class RetryResult {
        public final int recovered;
        public final List<String> errors;
        RetryResult(int recovered, List<String> errors) {
            this.recovered = recovered;
            this.errors = errors;
        }
    }
}
