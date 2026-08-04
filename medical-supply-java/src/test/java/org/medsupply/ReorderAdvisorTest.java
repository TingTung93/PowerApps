package org.medsupply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReorderAdvisorTest {
    private static final SupplyEvents.Identity ID = new SupplyEvents.Identity("d", "a", "s");
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    public static void main(String[] args) {
        parBelowTriggersReorder();
        consumptionDerivedWhenNoPar();
        insufficientHistoryWhenNoPicks();
        System.out.println("ReorderAdvisorTest: PASS");
    }

    private static void parBelowTriggersReorder() {
        Map<String, CatalogProduct> catalog = catalogOf("g1", "Gauze", 2.0, 10);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g1", 4));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock,
                new ArrayList<SupplyEvent>(), NOW, params());
        ReorderSuggestion s = out.get(0);
        check(s.parProvided, "par provided");
        check(s.onHand == 4, "onHand");
        check(s.needsReorder, "needs reorder");
        check(s.suggestedOrderQty == 6, "order 10-4=6, got " + s.suggestedOrderQty);
        check(Math.abs(s.estimatedCost - 12.0) < 1e-9, "cost");
    }

    private static void consumptionDerivedWhenNoPar() {
        Map<String, CatalogProduct> catalog = catalogOf("g2", "Glove", 1.0, -1);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g2", 5));
        // 90 units picked across the 90-day window => 1/day.
        List<SupplyEvent> events = new ArrayList<SupplyEvent>();
        events.add(SupplyEvents.stockPicked(ID, "2026-07-15T00:00:00Z", "g2", "L", "", 90));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock, events, NOW, params());
        ReorderSuggestion s = out.get(0);
        check(!s.parProvided, "no par");
        check(!s.insufficientHistory, "has history");
        check(Math.abs(s.avgDailyUsage - 1.0) < 1e-9, "avg=" + s.avgDailyUsage);
        check(s.reorderPoint == 14, "rop=ceil(1*(7+7))=14, got " + s.reorderPoint);
        check(s.needsReorder, "5<=14 reorder");
        check(s.suggestedOrderQty == 23, "target ceil(1*28)=28 -5 =23, got " + s.suggestedOrderQty);
    }

    private static void insufficientHistoryWhenNoPicks() {
        Map<String, CatalogProduct> catalog = catalogOf("g3", "Tape", 1.0, -1);
        List<StockLine> stock = new ArrayList<StockLine>();
        stock.add(activeLine("g3", 2));
        List<ReorderSuggestion> out = ReorderAdvisor.advise(catalog, stock,
                new ArrayList<SupplyEvent>(), NOW, params());
        ReorderSuggestion s = out.get(0);
        check(s.insufficientHistory, "insufficient");
        check(!s.needsReorder, "cannot advise");
    }

    private static ReorderAdvisor.Params params() {
        return new ReorderAdvisor.Params(90, 7, 7, 28);
    }

    private static Map<String, CatalogProduct> catalogOf(String gtin, String name, double price, int par) {
        CatalogProduct p = new CatalogProduct();
        p.gtin = gtin;
        p.name = name;
        p.unitPrice = price;
        p.par = par;
        Map<String, CatalogProduct> m = new LinkedHashMap<String, CatalogProduct>();
        m.put(gtin, p);
        return m;
    }

    private static StockLine activeLine(String gtin, int qty) {
        StockLine l = new StockLine();
        l.gtin = gtin;
        l.itemKey = gtin + "|L|";
        l.quantity = qty;
        l.active = true;
        return l;
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
