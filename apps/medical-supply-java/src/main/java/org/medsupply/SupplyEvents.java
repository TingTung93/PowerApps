package org.medsupply;

public final class SupplyEvents {
    private SupplyEvents() {}

    public static final String PRODUCT_REGISTERED = "PRODUCT_REGISTERED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_RETIRED = "PRODUCT_RETIRED";
    public static final String STOCK_RECEIVED = "STOCK_RECEIVED";
    public static final String STOCK_PICKED = "STOCK_PICKED";
    public static final String STOCK_ADJUSTED = "STOCK_ADJUSTED";
    public static final String STOCK_ARCHIVED = "STOCK_ARCHIVED";
    public static final String STOCK_VOIDED = "STOCK_VOIDED";
    public static final String STOCK_RESTORED = "STOCK_RESTORED";
    public static final String DISTRO_UPDATED = "DISTRO_UPDATED";
    public static final String K_MEMBERS = "members";

    public static final String K_GTIN = "gtin";
    public static final String K_LOT = "lot";
    public static final String K_EXPIRATION = "expiration";
    public static final String K_BARCODE = "barcode";
    public static final String K_ITEM_KEY = "itemKey";
    public static final String K_QUANTITY = "quantity";
    public static final String K_NAME = "name";
    public static final String K_MANUFACTURER = "manufacturer";
    public static final String K_CATEGORY = "category";
    public static final String K_UNIT_PRICE = "unitPrice";
    public static final String K_PAR = "par";
    public static final String K_NOTES = "notes";
    public static final String K_SOURCE = "source";
    public static final String K_REASON = "reason";
    public static final String K_AUTO_ARCHIVE = "autoArchive";

    public static final class Identity {
        public final String deviceId;
        public final String actor;
        public final String sessionId;

        public Identity(String deviceId, String actor, String sessionId) {
            this.deviceId = deviceId;
            this.actor = actor;
            this.sessionId = sessionId;
        }
    }

    public static SupplyEvent productRegistered(Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        return product(PRODUCT_REGISTERED, id, nowIso, gtin, name, manufacturer, category,
                unitPrice, par, notes, source);
    }

    public static SupplyEvent productUpdated(Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        return product(PRODUCT_UPDATED, id, nowIso, gtin, name, manufacturer, category, unitPrice, par, notes, source);
    }

    private static SupplyEvent product(String type, Identity id, String nowIso, String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes, String source) {
        SupplyEvent e = base(type, id, nowIso);
        e.payload.put(K_GTIN, nz(gtin));
        e.payload.put(K_NAME, nz(name));
        e.payload.put(K_MANUFACTURER, nz(manufacturer));
        e.payload.put(K_CATEGORY, nz(category));
        e.payload.put(K_UNIT_PRICE, trimNumber(unitPrice));
        e.payload.put(K_PAR, Integer.toString(par));
        e.payload.put(K_NOTES, nz(notes));
        e.payload.put(K_SOURCE, nz(source));
        return e;
    }

    public static SupplyEvent stockReceived(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String barcode, int quantity) {
        SupplyEvent e = stock(STOCK_RECEIVED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_BARCODE, nz(barcode));
        e.payload.put(K_QUANTITY, Integer.toString(quantity));
        return e;
    }

    public static SupplyEvent stockPicked(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, int quantity) {
        return stockPicked(id, nowIso, gtin, lot, expirationIso, quantity, false);
    }

    public static SupplyEvent stockPicked(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, int quantity, boolean autoArchive) {
        SupplyEvent e = stock(STOCK_PICKED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_QUANTITY, Integer.toString(quantity));
        if (autoArchive) e.payload.put(K_AUTO_ARCHIVE, "true");
        return e;
    }

    public static SupplyEvent stockAdjusted(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, int absoluteQuantity) {
        SupplyEvent e = stock(STOCK_ADJUSTED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_QUANTITY, Integer.toString(absoluteQuantity));
        return e;
    }

    public static SupplyEvent stockArchived(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String reason) {
        SupplyEvent e = stock(STOCK_ARCHIVED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    public static SupplyEvent stockVoided(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String reason) {
        SupplyEvent e = stock(STOCK_VOIDED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    public static SupplyEvent stockRestored(Identity id, String nowIso, String gtin, String lot,
            String expirationIso, String reason) {
        SupplyEvent e = stock(STOCK_RESTORED, id, nowIso, gtin, lot, expirationIso);
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    public static SupplyEvent productRetired(Identity id, String nowIso, String gtin, String reason) {
        SupplyEvent e = base(PRODUCT_RETIRED, id, nowIso);
        e.payload.put(K_GTIN, nz(gtin));
        e.payload.put(K_REASON, nz(reason));
        return e;
    }

    public static SupplyEvent distroUpdated(Identity id, String nowIso, String members) {
        SupplyEvent e = base(DISTRO_UPDATED, id, nowIso);
        e.payload.put(K_MEMBERS, nz(members));
        return e;
    }

    private static SupplyEvent stock(String type, Identity id, String nowIso, String gtin, String lot,
            String expirationIso) {
        SupplyEvent e = base(type, id, nowIso);
        e.payload.put(K_GTIN, nz(gtin));
        e.payload.put(K_LOT, nz(lot));
        e.payload.put(K_EXPIRATION, nz(expirationIso));
        e.payload.put(K_ITEM_KEY, ItemKey.of(gtin, lot, expirationIso));
        return e;
    }

    private static SupplyEvent base(String type, Identity id, String nowIso) {
        SupplyEvent e = new SupplyEvent();
        e.eventType = type;
        e.deviceId = id.deviceId;
        e.actor = id.actor;
        e.sessionId = id.sessionId;
        e.occurredUtc = nowIso;
        e.recordedUtc = nowIso;
        return e;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    static String trimNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return Long.toString((long) value);
        return Double.toString(value);
    }
}
