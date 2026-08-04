package org.medsupply;

import java.util.ArrayList;
import java.util.List;

public final class ProjectionTest {
    private static final SupplyEvents.Identity ID = new SupplyEvents.Identity("WS-1", "a", "s");

    public static void main(String[] args) {
        replaysQuantitiesAndEnrichment();
        adjustThenArchive();
        System.out.println("ProjectionTest: PASS");
    }

    private static void replaysQuantitiesAndEnrichment() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.productRegistered(ID, "2026-08-01T09:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "GUDID"));
        events.add(SupplyEvents.stockReceived(ID, "2026-08-02T09:00:00Z",
                "00380740000010", "L1", "2026-11-30", "bc1", 10));
        events.add(SupplyEvents.stockPicked(ID, "2026-08-03T09:00:00Z",
                "00380740000010", "L1", "2026-11-30", 3));

        Projection p = Projection.replay(events);
        check(p.catalog().size() == 1, "one product");
        CatalogProduct product = p.catalog().get("00380740000010");
        check("Stent".equals(product.name) && product.par == 4, "catalog fields");

        check(p.stock().size() == 1, "one stock line");
        StockLine line = p.stock().get(0);
        check(line.quantity == 7, "qty 10-3=7, got " + line.quantity);
        check(line.active, "active");
        check("Stent".equals(line.name), "enriched name");
        check("Abbott".equals(line.manufacturer), "enriched manufacturer");
        check(line.unitPrice == 10.0, "enriched price");
        check("2026-08-03T09:00:00Z".equals(line.lastEventUtc), "last event");
    }

    private static void adjustThenArchive() {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.stockReceived(ID, "2026-08-02T09:00:00Z", "G", "L", "", "bc", 5));
        events.add(SupplyEvents.stockAdjusted(ID, "2026-08-03T09:00:00Z", "G", "L", "", 2));
        events.add(SupplyEvents.stockArchived(ID, "2026-08-04T09:00:00Z", "G", "L", "", "expired"));

        Projection p = Projection.replay(events);
        StockLine line = p.stock().get(0);
        check(line.quantity == 2, "adjust sets absolute, got " + line.quantity);
        check(!line.active, "archived inactive");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
