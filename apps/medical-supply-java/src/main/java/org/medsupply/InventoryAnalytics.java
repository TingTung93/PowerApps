package org.medsupply;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class InventoryAnalytics {
    private InventoryAnalytics() {}

    public static DashboardMetrics compute(List<StockLine> stock, List<SupplyEvent> events,
            Instant now, int staleDays) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Instant staleBefore = now.minusSeconds(staleDays * 86400L);
        Instant sevenAgo = now.minusSeconds(7 * 86400L);
        DashboardMetrics m = new DashboardMetrics();

        for (StockLine line : stock) {
            if (!line.active) continue;
            LocalDate exp = parseDate(line.expirationIso);
            if (exp != null) {
                if (exp.isBefore(today)) {
                    m.expired++;
                } else if (!exp.isAfter(today.plusDays(7))) {
                    m.expiring7++;
                    m.expiring30++;
                } else if (!exp.isAfter(today.plusDays(30))) {
                    m.expiring30++;
                }
            }
            if (line.quantity < 1) m.outOfStock++;
            if (isStale(line.lastEventUtc, staleBefore)) m.stale++;
            if (line.quantity > 0) {
                m.distinctSkus++;
                m.totalUnits += line.quantity;
                m.onHandValue += line.quantity * line.unitPrice;
            }
        }

        for (SupplyEvent e : events) {
            Instant occurred = parseInstant(e.occurredUtc);
            if (occurred != null && !occurred.isBefore(sevenAgo)) m.activeEventsLast7++;
        }
        return m;
    }

    private static boolean isStale(String lastEventUtc, Instant staleBefore) {
        Instant last = parseInstant(lastEventUtc);
        return last != null && last.isBefore(staleBefore);
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.length() == 0) return null;
        try { return LocalDate.parse(iso); } catch (RuntimeException ex) { return null; }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.length() == 0) return null;
        try { return Instant.parse(iso); } catch (RuntimeException ex) { return null; }
    }
}
