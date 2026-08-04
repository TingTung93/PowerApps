package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReorderAdvisor {
    private ReorderAdvisor() {}

    public static final class Params {
        public final int windowDays;
        public final int leadDays;
        public final int safetyDays;
        public final int coverageDays;

        public Params(int windowDays, int leadDays, int safetyDays, int coverageDays) {
            this.windowDays = windowDays;
            this.leadDays = leadDays;
            this.safetyDays = safetyDays;
            this.coverageDays = coverageDays;
        }
    }

    public static List<ReorderSuggestion> advise(Map<String, CatalogProduct> catalog, List<StockLine> stock,
            List<SupplyEvent> events, Instant now, Params params) {
        Map<String, Integer> onHand = new HashMap<String, Integer>();
        for (StockLine line : stock) {
            if (!line.active) continue;
            Integer current = onHand.get(line.gtin);
            onHand.put(line.gtin, (current == null ? 0 : current) + line.quantity);
        }
        Map<String, Integer> pickedInWindow = pickedInWindow(events, now, params.windowDays);

        List<ReorderSuggestion> out = new ArrayList<ReorderSuggestion>();
        for (CatalogProduct product : catalog.values()) {
            ReorderSuggestion s = new ReorderSuggestion();
            s.gtin = product.gtin;
            s.name = product.name;
            s.onHand = onHand.containsKey(product.gtin) ? onHand.get(product.gtin) : 0;
            s.par = product.par;

            Integer picked = pickedInWindow.get(product.gtin);
            s.avgDailyUsage = picked == null ? 0.0 : (double) picked / params.windowDays;

            if (product.hasPar()) {
                s.parProvided = true;
                s.needsReorder = s.onHand < product.par;
                s.suggestedOrderQty = Math.max(0, product.par - s.onHand);
            } else if (picked == null || picked == 0) {
                s.insufficientHistory = true;
            } else {
                s.reorderPoint = (int) Math.ceil(s.avgDailyUsage * (params.leadDays + params.safetyDays));
                s.suggestedPar = s.reorderPoint;
                s.needsReorder = s.onHand <= s.reorderPoint;
                int target = (int) Math.ceil(s.avgDailyUsage * params.coverageDays);
                s.suggestedOrderQty = Math.max(0, target - s.onHand);
            }
            s.estimatedCost = s.suggestedOrderQty * product.unitPrice;
            out.add(s);
        }

        Collections.sort(out, new Comparator<ReorderSuggestion>() {
            public int compare(ReorderSuggestion a, ReorderSuggestion b) {
                if (a.needsReorder != b.needsReorder) return a.needsReorder ? -1 : 1;
                return a.gtin.compareTo(b.gtin);
            }
        });
        return out;
    }

    private static Map<String, Integer> pickedInWindow(List<SupplyEvent> events, Instant now, int windowDays) {
        Instant windowStart = now.minusSeconds(windowDays * 86400L);
        Map<String, Integer> picked = new HashMap<String, Integer>();
        for (SupplyEvent e : events) {
            if (!SupplyEvents.STOCK_PICKED.equals(e.eventType)) continue;
            Instant occurred;
            try { occurred = Instant.parse(e.occurredUtc); } catch (RuntimeException ex) { continue; }
            if (occurred.isBefore(windowStart) || occurred.isAfter(now)) continue;
            String gtin = e.payload(SupplyEvents.K_GTIN);
            int qty;
            try { qty = Integer.parseInt(e.payload(SupplyEvents.K_QUANTITY).trim()); }
            catch (NumberFormatException ex) { qty = 0; }
            Integer current = picked.get(gtin);
            picked.put(gtin, (current == null ? 0 : current) + qty);
        }
        return picked;
    }
}
