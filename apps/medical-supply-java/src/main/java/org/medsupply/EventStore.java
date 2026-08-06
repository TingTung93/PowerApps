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
import java.time.LocalDate;
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
import java.util.UUID;
import java.util.stream.Stream;

public final class EventStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private final Path sharedRoot;
    private final Path pendingRoot;
    private final LocalEventIndex index;
    private final Publisher publisher;

    interface Publisher {
        Path publish(SupplyEvent event, Path local) throws IOException;
    }

    public EventStore(Path sharedRoot, Path localRoot) throws IOException {
        this(sharedRoot, localRoot, null);
    }

    EventStore(Path sharedRoot, Path localRoot, Publisher publisher) throws IOException {
        this.sharedRoot = sharedRoot;
        this.pendingRoot = localRoot.resolve("pending");
        this.index = new LocalEventIndex(localRoot);
        this.publisher = publisher == null ? this::finalizeShared : publisher;
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
        try {
            Path published = publisher.publish(event, local);
            Files.deleteIfExists(local);
            return published;
        } catch (IOException ex) {
            // The operation is durably accepted locally. Returning the pending path avoids an
            // ambiguous 500/retry cycle that could create a second logical inventory operation.
            return local;
        }
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
                    publisher.publish(event, local);
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
        recoverPartials(eventsRoot, errors);
        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > 1024 * 1024) throw new IOException("File exceeds 1 MB");
                            String relative = sharedRoot.relativize(path).toString();
                            long size = Files.size(path);
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            // Always read the source bytes. Size/mtime are not integrity evidence and
                            // a metadata-only cache could conceal a same-size, timestamp-preserving edit.
                            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

    private void recoverPartials(Path eventsRoot, List<String> errors) {
        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json.partial"))
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > 1024 * 1024) throw new IOException("File exceeds 1 MB");
                            SupplyEvent event = SupplyEventJson.read(
                                    new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                            validate(event);
                            Path target = path.resolveSibling(path.getFileName().toString()
                                    .substring(0, path.getFileName().toString().length() - ".partial".length()));
                            if (Files.exists(target)) {
                                String partialHash = sha256(Files.readAllBytes(path));
                                String targetHash = sha256(Files.readAllBytes(target));
                                if (!partialHash.equals(targetHash))
                                    throw new IOException("published event differs from partial");
                                Files.delete(path);
                            } else {
                                try {
                                    Files.move(path, target, StandardCopyOption.ATOMIC_MOVE);
                                } catch (AtomicMoveNotSupportedException ex) {
                                    Files.move(path, target);
                                }
                            }
                        } catch (Exception ex) {
                            errors.add(path.getFileName() + ": unrecovered partial: " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            errors.add("Partial event scan failed: " + ex.getMessage());
        }
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

    static void validate(SupplyEvent event) {
        if (!"1".equals(event.schemaVersion)) throw new IllegalArgumentException("Unsupported schema");
        try {
            if (!UUID.fromString(event.eventId).toString().equalsIgnoreCase(event.eventId))
                throw new IllegalArgumentException("Invalid event ID");
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid event ID"); }
        if (event.eventType.length() == 0 || event.eventType.length() > 40)
            throw new IllegalArgumentException("Invalid event type");
        Instant.parse(event.occurredUtc);
        Instant.parse(event.recordedUtc);
        required(event.deviceId, "deviceId", 100);
        required(event.actor, "actor", 200);
        String type = event.eventType;
        for (Map.Entry<String, String> field : event.payload.entrySet()) {
            if (!allowedPayload(type, field.getKey()))
                throw new IllegalArgumentException("Unknown payload field: " + field.getKey());
            if (field.getValue() != null && field.getValue().length() > 10000)
                throw new IllegalArgumentException(field.getKey() + " is too long");
        }
        if (SupplyEvents.PRODUCT_REGISTERED.equals(type) || SupplyEvents.PRODUCT_UPDATED.equals(type)) {
            required(event.payload(SupplyEvents.K_GTIN), "gtin", 64);
            required(event.payload(SupplyEvents.K_NAME), "name", 500);
            decimal(event.payload(SupplyEvents.K_UNIT_PRICE), "unitPrice", 0.0);
            integer(event.payload(SupplyEvents.K_PAR), "par", -1);
        } else if (SupplyEvents.PRODUCT_RETIRED.equals(type)) {
            required(event.payload(SupplyEvents.K_GTIN), "gtin", 64);
            required(event.payload(SupplyEvents.K_REASON), "reason", 1000);
        } else if (SupplyEvents.DISTRO_UPDATED.equals(type)) {
            if (event.payload(SupplyEvents.K_MEMBERS).length() > 10000)
                throw new IllegalArgumentException("members is too long");
        } else if (isStockType(type)) {
            String gtin = required(event.payload(SupplyEvents.K_GTIN), "gtin", 64);
            String lot = event.payload(SupplyEvents.K_LOT);
            String expiration = event.payload(SupplyEvents.K_EXPIRATION);
            if (lot.length() > 500) throw new IllegalArgumentException("lot is too long");
            if (expiration.length() > 0) {
                try { LocalDate.parse(expiration); }
                catch (RuntimeException ex) { throw new IllegalArgumentException("expiration is invalid"); }
            }
            String expectedKey = ItemKey.of(gtin, lot, expiration);
            if (!expectedKey.equals(event.payload(SupplyEvents.K_ITEM_KEY)))
                throw new IllegalArgumentException("itemKey does not match stock identity");
            if (SupplyEvents.STOCK_RECEIVED.equals(type) || SupplyEvents.STOCK_PICKED.equals(type))
                integer(event.payload(SupplyEvents.K_QUANTITY), "quantity", 1);
            if (SupplyEvents.STOCK_ADJUSTED.equals(type))
                integer(event.payload(SupplyEvents.K_QUANTITY), "quantity", 0);
            if ((SupplyEvents.STOCK_ARCHIVED.equals(type) || SupplyEvents.STOCK_VOIDED.equals(type))
                    && event.payload(SupplyEvents.K_REASON).trim().length() == 0)
                throw new IllegalArgumentException("reason is required");
            if (SupplyEvents.STOCK_RESTORED.equals(type)
                    && event.payload(SupplyEvents.K_REASON).trim().length() == 0)
                throw new IllegalArgumentException("reason is required");
        } else {
            throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    private static boolean allowedPayload(String type, String key) {
        if (SupplyEvents.PRODUCT_REGISTERED.equals(type) || SupplyEvents.PRODUCT_UPDATED.equals(type))
            return oneOf(key, SupplyEvents.K_GTIN, SupplyEvents.K_NAME, SupplyEvents.K_MANUFACTURER,
                    SupplyEvents.K_CATEGORY, SupplyEvents.K_UNIT_PRICE, SupplyEvents.K_PAR,
                    SupplyEvents.K_NOTES, SupplyEvents.K_SOURCE);
        if (SupplyEvents.PRODUCT_RETIRED.equals(type))
            return oneOf(key, SupplyEvents.K_GTIN, SupplyEvents.K_REASON);
        if (SupplyEvents.DISTRO_UPDATED.equals(type)) return SupplyEvents.K_MEMBERS.equals(key);
        if (isStockType(type))
            return oneOf(key, SupplyEvents.K_GTIN, SupplyEvents.K_LOT, SupplyEvents.K_EXPIRATION,
                    SupplyEvents.K_ITEM_KEY, SupplyEvents.K_BARCODE, SupplyEvents.K_QUANTITY,
                    SupplyEvents.K_REASON, SupplyEvents.K_AUTO_ARCHIVE);
        return false;
    }

    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    private static boolean isStockType(String type) {
        return SupplyEvents.STOCK_RECEIVED.equals(type) || SupplyEvents.STOCK_PICKED.equals(type)
                || SupplyEvents.STOCK_ADJUSTED.equals(type) || SupplyEvents.STOCK_ARCHIVED.equals(type)
                || SupplyEvents.STOCK_VOIDED.equals(type) || SupplyEvents.STOCK_RESTORED.equals(type);
    }

    private static String required(String value, String label, int max) {
        if (value == null || value.trim().length() == 0) throw new IllegalArgumentException(label + " is required");
        if (value.length() > max) throw new IllegalArgumentException(label + " is too long");
        return value;
    }

    private static int integer(String value, String label, int min) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min) throw new IllegalArgumentException(label + " must be at least " + min);
            return parsed;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " is invalid"); }
    }

    private static double decimal(String value, String label, double min) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < min)
                throw new IllegalArgumentException(label + " must be at least " + min);
            return parsed;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " is invalid"); }
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
