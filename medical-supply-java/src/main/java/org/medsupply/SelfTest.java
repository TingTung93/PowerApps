package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SelfTest {
    private SelfTest() {}

    public static void run() throws Exception {
        if (!"1".equals(SupplyMeta.SCHEMA_VERSION))
            throw new AssertionError("Unexpected schema version");

        Path shared = Files.createTempDirectory("medsupply-selftest-shared");
        Path local = Files.createTempDirectory("medsupply-selftest-local");
        EventStore store = new EventStore(shared, local);

        SupplyEvent event = new SupplyEvent();
        event.eventType = "STOCK_RECEIVED";
        event.deviceId = "SELFTEST";
        event.occurredUtc = "2026-08-03T12:00:00Z";
        event.recordedUtc = "2026-08-03T12:00:00Z";
        event.payload.put("gtin", "00380740000010");
        event.payload.put("quantityDelta", "3");
        store.append(event);

        EventStore.LoadResult loaded = store.loadAll();
        if (!loaded.errors.isEmpty()) throw new AssertionError("Load errors: " + loaded.errors);
        if (loaded.events.size() != 1)
            throw new AssertionError("Expected 1 event, got " + loaded.events.size());
        if (!"3".equals(loaded.events.get(0).payload("quantityDelta")))
            throw new AssertionError("Payload roundtrip failed");
    }
}
