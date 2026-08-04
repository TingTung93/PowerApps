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

        // Domain pipeline smoke: parse a GS1 label, receive stock, project, analyze, advise.
        Gs1Scan parsed = Gs1Parser.parse("010038074000001017261130" + "10LOT1");
        if (!parsed.success) throw new AssertionError("GS1 parse failed");

        Path shared2 = Files.createTempDirectory("medsupply-selftest-domain");
        Path local2 = Files.createTempDirectory("medsupply-selftest-domain-local");
        EventStore store2 = new EventStore(shared2, local2);
        SupplyEvents.Identity id = new SupplyEvents.Identity("SELFTEST", "selftest", "sess");
        store2.append(SupplyEvents.productRegistered(id, "2026-08-01T00:00:00Z",
                parsed.gtin, "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL"));
        store2.append(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z",
                parsed.gtin, parsed.lot, parsed.expirationIso, parsed.raw, 2));

        EventStore.LoadResult loaded2 = store2.loadAll();
        if (!loaded2.errors.isEmpty())
            throw new AssertionError("Domain load errors: " + loaded2.errors);
        Projection projection = Projection.replay(loaded2.events);
        if (projection.stock().size() != 1) throw new AssertionError("Expected 1 stock line");
        if (projection.stock().get(0).quantity != 2) throw new AssertionError("Expected qty 2");

        java.time.Instant now = java.time.Instant.parse("2026-08-03T00:00:00Z");
        DashboardMetrics metrics = InventoryAnalytics.compute(
                projection.stock(), loaded2.events, now, 30);
        if (metrics.distinctSkus != 1) throw new AssertionError("Expected 1 SKU");

        java.util.List<ReorderSuggestion> suggestions = ReorderAdvisor.advise(
                projection.catalog(), projection.stock(), loaded2.events, now,
                new ReorderAdvisor.Params(90, 7, 7, 28));
        if (suggestions.isEmpty() || !suggestions.get(0).needsReorder)
            throw new AssertionError("Expected a reorder suggestion (onHand 2 < par 4)");
    }
}
