package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InventoryAnalyticsTest {
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(line("g1|a|20260801", 5, 2.0, "2026-08-01", true, "2026-08-02T00:00:00Z")); // expired
        stock.add(line("g2|b|20260806", 3, 4.0, "2026-08-06", true, "2026-08-02T00:00:00Z")); // expiring7
        stock.add(line("g3|c|20260828", 2, 1.0, "2026-08-28", true, "2026-08-02T00:00:00Z")); // expiring30
        stock.add(line("g4|d|20270101", 0, 9.0, "2027-01-01", true, "2026-01-01T00:00:00Z")); // out+stale
        StockLine archived = line("g5|e|", 100, 5.0, "", false, "2026-08-02T00:00:00Z");     // ignored
        stock.add(archived);

        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        SupplyEvents.Identity id = new SupplyEvents.Identity("d", "a", "s");
        events.add(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z", "g2", "b", "2026-08-06", "bc", 3)); // recent
        events.add(SupplyEvents.stockReceived(id, "2026-06-01T00:00:00Z", "g4", "d", "2027-01-01", "bc", 1)); // old

        DashboardMetrics m = InventoryAnalytics.compute(stock, events, now, 30);
        check(m.expired == 1, "expired=" + m.expired);
        check(m.expiring7 == 1, "expiring7=" + m.expiring7);
        check(m.expiring30 == 2, "expiring30=" + m.expiring30);
        check(m.outOfStock == 1, "outOfStock=" + m.outOfStock);
        check(m.stale == 1, "stale=" + m.stale);
        check(m.distinctSkus == 3, "distinctSkus=" + m.distinctSkus);
        check(m.totalUnits == 10, "totalUnits=" + m.totalUnits);
        check(Math.abs(m.onHandValue - (5 * 2.0 + 3 * 4.0 + 2 * 1.0)) < 1e-9, "value=" + m.onHandValue);
        check(m.activeEventsLast7 == 1, "events7=" + m.activeEventsLast7);
        System.out.println("InventoryAnalyticsTest: PASS");
    }

    private static StockLine line(String key, int qty, double price, String exp, boolean active, String last) {
        StockLine l = new StockLine();
        l.itemKey = key;
        l.quantity = qty;
        l.unitPrice = price;
        l.expirationIso = exp;
        l.active = active;
        l.lastEventUtc = last;
        return l;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
