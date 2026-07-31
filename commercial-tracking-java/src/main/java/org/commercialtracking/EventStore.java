package org.commercialtracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
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
        Files.createDirectories(sharedRoot.resolve("manifests"));
        Files.createDirectories(sharedRoot.resolve("reports"));
        Files.createDirectories(sharedRoot.resolve("configuration"));
        Files.createDirectories(sharedRoot.resolve("reconciliation"));
        Files.createDirectories(sharedRoot.resolve("diagnostics"));
        Files.createDirectories(pendingRoot);
    }

    public synchronized Path append(TrackingEvent event) throws IOException {
        validate(event);
        String json = EventJson.write(event);
        Path local = pendingRoot.resolve(event.eventId + ".tmp");
        Files.write(local, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        force(local);
        return finalizeShared(event, local);
    }

    private Path finalizeShared(TrackingEvent event, Path local) throws IOException {
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
                    TrackingEvent event = EventJson.read(
                            new String(Files.readAllBytes(local), StandardCharsets.UTF_8));
                    validate(event);
                    finalizeShared(event, local);
                    recovered++;
                } catch (Exception ex) {
                    errors.add(local.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) { errors.add("Pending scan failed: " + ex.getMessage()); }
        return new RetryResult(recovered, errors);
    }

    public synchronized LoadResult loadAll() {
        List<TrackingEvent> events = new ArrayList<TrackingEvent>();
        List<String> errors = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        Set<String> ids = new HashSet<String>();
        Map<String, String> hashes = new HashMap<String, String>();
        Map<String, LocalEventIndex.Entry> currentIndex = new HashMap<String, LocalEventIndex.Entry>();
        Path eventsRoot = sharedRoot.resolve("events");
        if (!Files.isDirectory(eventsRoot)) return new LoadResult(events, errors, warnings);
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
                                    ? new String(readStable(path), StandardCharsets.UTF_8) : cached.json;
                            TrackingEvent event = EventJson.read(json);
                            validate(event);
                            if (Instant.parse(event.occurredUtc).isAfter(Instant.now().plusSeconds(300)))
                                warnings.add(path.getFileName() + ": event time is more than five minutes in the future; event retained.");
                            String hash = sha256(EventJson.write(event).getBytes(StandardCharsets.UTF_8));
                            String fileHash = sha256(json.getBytes(StandardCharsets.UTF_8));
                            currentIndex.put(relative, new LocalEventIndex.Entry(relative, size, modified, fileHash, json));
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
        Collections.sort(events, Comparator.comparing((TrackingEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.recordedUtc).thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        try { index.replace(currentIndex); }
        catch (IOException ex) { errors.add("Local index update failed: " + ex.getMessage()); }
        cleanupObservedPending(ids, errors);
        return new LoadResult(events, errors, warnings);
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

    private void cleanupObservedPending(Set<String> ids, List<String> errors) {
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(pendingRoot, "*.tmp")) {
            for (Path path : pending) {
                String name = path.getFileName().toString();
                String id = name.substring(0, name.length() - 4);
                if (ids.contains(id)) Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            errors.add("Pending cleanup failed: " + ex.getMessage());
        }
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static byte[] readStable(Path path) throws IOException {
        if (System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() > 500)
            return Files.readAllBytes(path);
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                long size = Files.size(path);
                long modified = Files.getLastModifiedTime(path).toMillis();
                byte[] bytes = Files.readAllBytes(path);
                try { Thread.sleep(25L * (attempt + 1)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Interrupted while waiting for file stability.", ex); }
                if (size == Files.size(path) && modified == Files.getLastModifiedTime(path).toMillis())
                    return bytes;
                last = new IOException("File is still changing.");
            } catch (java.nio.file.AccessDeniedException ex) {
                last = ex;
                try { Thread.sleep(50L * (1L << attempt)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
        }
        throw last == null ? new IOException("File did not stabilize.") : last;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : hash) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static void validate(TrackingEvent event) {
        if (!"1".equals(event.schemaVersion)) throw new IllegalArgumentException("Unsupported schema");
        if (!event.eventId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid event ID");
        if (event.eventType.length() == 0 || event.eventType.length() > 40)
            throw new IllegalArgumentException("Invalid event type");
        Instant.parse(event.occurredUtc);
        if ((event.eventType.startsWith("PACKAGE_") || event.eventType.startsWith("MANIFEST_"))
                && (event.trackingNumber.length() == 0 || event.trackingNumber.length() > 100))
            throw new IllegalArgumentException("Invalid tracking number");
    }

    public static final class LoadResult {
        public final List<TrackingEvent> events;
        public final List<String> errors;
        public final List<String> warnings;
        LoadResult(List<TrackingEvent> events, List<String> errors, List<String> warnings) {
            this.events = events;
            this.errors = errors;
            this.warnings = warnings;
        }
    }

    public static final class RetryResult {
        public final int recovered;
        public final List<String> errors;
        RetryResult(int recovered, List<String> errors) { this.recovered = recovered; this.errors = errors; }
    }
}
