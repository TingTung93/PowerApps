package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

public final class EventStoreTest {
    public static void main(String[] args) throws Exception {
        Path shared = Files.createTempDirectory("medsupply-shared");
        Path localA = Files.createTempDirectory("medsupply-localA");
        Path localB = Files.createTempDirectory("medsupply-localB");

        EventStore a = new EventStore(shared, localA);
        SupplyEvent e1 = event("STOCK_RECEIVED", "WS-A", "2026-08-03T10:00:00Z", "5");
        SupplyEvent e2 = event("STOCK_PICKED", "WS-A", "2026-08-03T11:00:00Z", "-2");
        a.append(e1);
        a.append(e2);

        // A second workstation observes the same shared folder.
        EventStore b = new EventStore(shared, localB);
        EventStore.LoadResult loaded = b.loadAll();
        check(loaded.errors.isEmpty(), "no errors: " + loaded.errors);
        check(loaded.events.size() == 2, "two events, got " + loaded.events.size());
        check("STOCK_RECEIVED".equals(loaded.events.get(0).eventType), "sorted first");
        check("STOCK_PICKED".equals(loaded.events.get(1).eventType), "sorted second");
        check(a.pendingCount() == 0, "pending drained");

        Path firstEvent;
        try (Stream<Path> paths = Files.walk(shared.resolve("events"))) {
            firstEvent = paths.filter(path -> path.getFileName().toString().contains(e1.eventId)).findFirst().get();
        }
        FileTime originalTime = Files.getLastModifiedTime(firstEvent);
        String originalJson = new String(Files.readAllBytes(firstEvent), "UTF-8");
        String tamperedJson = originalJson.replace("DOMAIN\\\\tester", "DOMAIN\\\\hacker");
        check(tamperedJson.length() == originalJson.length(), "tamper fixture preserves size");
        Files.write(firstEvent, tamperedJson.getBytes("UTF-8"));
        Files.setLastModifiedTime(firstEvent, originalTime);
        EventStore.LoadResult tampered = b.loadAll();
        SupplyEvent changed = null;
        for (SupplyEvent event : tampered.events) if (e1.eventId.equals(event.eventId)) changed = event;
        check(changed != null && "DOMAIN\\hacker".equals(changed.actor),
                "source bytes reread despite same size and mtime");

        SupplyEvent unattributed = event("STOCK_RECEIVED", "WS-A", "2026-08-03T12:00:00Z", "1");
        unattributed.actor = "";
        boolean rejected = false;
        try { a.append(unattributed); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "unattributed event rejected");
        SupplyEvent invalidDate = event("STOCK_RECEIVED", "WS-A", "2026-08-03T12:00:00Z", "1");
        invalidDate.payload.put("expiration", "2026-99-99");
        invalidDate.payload.put("itemKey", ItemKey.of("00380740000010", "LOT-1", "2026-99-99"));
        rejected = false;
        try { a.append(invalidDate); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "invalid expiration rejected");

        Path queuedLocal = Files.createTempDirectory("medsupply-queued-local");
        EventStore unavailable = new EventStore(shared, queuedLocal, (event, local) -> {
            throw new java.io.IOException("sync unavailable");
        });
        SupplyEvent queuedEvent = event("STOCK_RECEIVED", "WS-Q", "2026-08-03T13:00:00Z", "1");
        Path accepted = unavailable.append(queuedEvent);
        check(accepted.getFileName().toString().endsWith(".tmp"), "publish failure returns queued outcome");
        check(unavailable.pendingCount() == 1, "durably accepted operation remains pending");
        check(!unavailable.retryPending().errors.isEmpty(), "retry failure remains visible");

        Path published;
        try (Stream<Path> paths = Files.walk(shared.resolve("events"))) {
            published = paths.filter(path -> path.toString().endsWith(".json")).findFirst().get();
        }
        Path partial = published.resolveSibling(published.getFileName() + ".partial");
        Files.copy(published, partial, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(published);
        EventStore.LoadResult recovered = b.loadAll();
        check(recovered.errors.isEmpty(), "valid partial recovered: " + recovered.errors);
        check(Files.exists(published), "partial published");

        Path corrupt = published.resolveSibling("corrupt.json");
        Files.write(corrupt, "not-json".getBytes("UTF-8"));
        EventStore.LoadResult incomplete = b.loadAll();
        check(!incomplete.errors.isEmpty(), "corrupt event marks trail incomplete");
        System.out.println("EventStoreTest: PASS");
    }

    private static SupplyEvent event(String type, String device, String occurred, String delta) {
        SupplyEvent e = new SupplyEvent();
        e.eventType = type;
        e.deviceId = device;
        e.actor = "DOMAIN\\tester";
        e.sessionId = "test-session";
        e.occurredUtc = occurred;
        e.recordedUtc = occurred;
        e.payload.put("gtin", "00380740000010");
        e.payload.put("lot", "LOT-1");
        e.payload.put("expiration", "2026-12-31");
        e.payload.put("itemKey", ItemKey.of("00380740000010", "LOT-1", "2026-12-31"));
        e.payload.put("quantity", delta.startsWith("-") ? delta.substring(1) : delta);
        return e;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
