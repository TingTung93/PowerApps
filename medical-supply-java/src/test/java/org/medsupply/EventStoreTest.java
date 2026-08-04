package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

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
        System.out.println("EventStoreTest: PASS");
    }

    private static SupplyEvent event(String type, String device, String occurred, String delta) {
        SupplyEvent e = new SupplyEvent();
        e.eventType = type;
        e.deviceId = device;
        e.occurredUtc = occurred;
        e.recordedUtc = occurred;
        e.payload.put("gtin", "00380740000010");
        e.payload.put("quantityDelta", delta);
        return e;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
