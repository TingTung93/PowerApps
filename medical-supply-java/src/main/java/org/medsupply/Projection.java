package org.medsupply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Projection {
    private final Map<String, CatalogProduct> catalog = new LinkedHashMap<String, CatalogProduct>();
    private final Map<String, StockLine> stock = new LinkedHashMap<String, StockLine>();

    private Projection() {}

    public static Projection replay(List<SupplyEvent> input) {
        List<SupplyEvent> events = new ArrayList<SupplyEvent>(input);
        Collections.sort(events, Comparator.comparing((SupplyEvent e) -> e.occurredUtc)
                .thenComparing(e -> e.recordedUtc).thenComparing(e -> e.deviceId).thenComparing(e -> e.eventId));
        Projection p = new Projection();
        for (SupplyEvent e : events) p.apply(e);
        p.enrich();
        return p;
    }

    private void apply(SupplyEvent e) {
        String type = e.eventType;
        if (SupplyEvents.PRODUCT_REGISTERED.equals(type) || SupplyEvents.PRODUCT_UPDATED.equals(type)) {
            CatalogProduct product = new CatalogProduct();
            product.gtin = e.payload(SupplyEvents.K_GTIN);
            product.name = e.payload(SupplyEvents.K_NAME);
            product.manufacturer = e.payload(SupplyEvents.K_MANUFACTURER);
            product.category = e.payload(SupplyEvents.K_CATEGORY);
            product.notes = e.payload(SupplyEvents.K_NOTES);
            product.source = e.payload(SupplyEvents.K_SOURCE);
            product.unitPrice = parseDouble(e.payload(SupplyEvents.K_UNIT_PRICE));
            product.par = parseInt(e.payload(SupplyEvents.K_PAR), -1);
            if (product.gtin.length() > 0) catalog.put(product.gtin, product);
            return;
        }
        if (SupplyEvents.PRODUCT_RETIRED.equals(type)) {
            CatalogProduct product = catalog.get(e.payload(SupplyEvents.K_GTIN));
            if (product != null) product.active = false;
            return;
        }
        String key = e.payload(SupplyEvents.K_ITEM_KEY);
        if (key.length() == 0) return;
        StockLine line = stock.get(key);
        if (line == null) {
            line = new StockLine();
            line.itemKey = key;
            line.gtin = e.payload(SupplyEvents.K_GTIN);
            line.lot = e.payload(SupplyEvents.K_LOT);
            line.expirationIso = e.payload(SupplyEvents.K_EXPIRATION);
            stock.put(key, line);
        }
        if (e.payload(SupplyEvents.K_BARCODE).length() > 0) line.barcode = e.payload(SupplyEvents.K_BARCODE);
        line.lastEventUtc = e.occurredUtc;
        line.lastDevice = e.deviceId;
        int qty = parseInt(e.payload(SupplyEvents.K_QUANTITY), 0);
        if (SupplyEvents.STOCK_RECEIVED.equals(type)) {
            line.quantity += qty;
            line.active = true;
        } else if (SupplyEvents.STOCK_PICKED.equals(type)) {
            line.quantity -= qty;
            if ("true".equals(e.payload(SupplyEvents.K_AUTO_ARCHIVE))) line.active = false;
        } else if (SupplyEvents.STOCK_ADJUSTED.equals(type)) {
            line.quantity = qty;
            line.active = true;
        } else if (SupplyEvents.STOCK_ARCHIVED.equals(type) || SupplyEvents.STOCK_VOIDED.equals(type)) {
            line.active = false;
        } else if (SupplyEvents.STOCK_RESTORED.equals(type)) {
            line.active = true;
        }
    }

    private void enrich() {
        for (StockLine line : stock.values()) {
            CatalogProduct product = catalog.get(line.gtin);
            if (product == null) continue;
            line.name = product.name;
            line.manufacturer = product.manufacturer;
            line.category = product.category;
            line.unitPrice = product.unitPrice;
            line.par = product.par;
        }
    }

    public Map<String, CatalogProduct> catalog() {
        return catalog;
    }

    public List<StockLine> stock() {
        List<StockLine> lines = new ArrayList<StockLine>(stock.values());
        Collections.sort(lines, Comparator.comparing((StockLine l) -> l.itemKey));
        return lines;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ex) { return fallback; }
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value.trim()); } catch (NumberFormatException ex) { return 0.0; }
    }
}
