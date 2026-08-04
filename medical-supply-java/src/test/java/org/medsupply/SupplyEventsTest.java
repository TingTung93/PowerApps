package org.medsupply;

public final class SupplyEventsTest {
    public static void main(String[] args) {
        itemKeyStripsDashes();
        stockReceivedCarriesKeyAndQuantity();
        productRegisteredCarriesCatalogFields();
        System.out.println("SupplyEventsTest: PASS");
    }

    private static void itemKeyStripsDashes() {
        check("00380740000010|LOT9|20261130".equals(
                ItemKey.of("00380740000010", "LOT9", "2026-11-30")), "full key");
        check("00380740000010||".equals(
                ItemKey.of("00380740000010", "", "")), "empty lot/exp");
    }

    private static void stockReceivedCarriesKeyAndQuantity() {
        SupplyEvents.Identity id = new SupplyEvents.Identity("WS-1", "DOM\\alice", "sess-1");
        SupplyEvent e = SupplyEvents.stockReceived(id, "2026-08-03T10:00:00Z",
                "00380740000010", "LOT9", "2026-11-30", "0100380740000010", 5);
        check(SupplyEvents.STOCK_RECEIVED.equals(e.eventType), "type");
        check("WS-1".equals(e.deviceId), "device");
        check("2026-08-03T10:00:00Z".equals(e.occurredUtc), "occurred");
        check("00380740000010|LOT9|20261130".equals(e.payload(SupplyEvents.K_ITEM_KEY)), "itemKey");
        check("5".equals(e.payload(SupplyEvents.K_QUANTITY)), "qty");
        check("LOT9".equals(e.payload(SupplyEvents.K_LOT)), "lot");
    }

    private static void productRegisteredCarriesCatalogFields() {
        SupplyEvents.Identity id = new SupplyEvents.Identity("WS-1", "a", "s");
        SupplyEvent e = SupplyEvents.productRegistered(id, "2026-08-03T10:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 12.50, 4, "note", "GUDID");
        check(SupplyEvents.PRODUCT_REGISTERED.equals(e.eventType), "type");
        check("Stent".equals(e.payload(SupplyEvents.K_NAME)), "name");
        check("Abbott".equals(e.payload(SupplyEvents.K_MANUFACTURER)), "man");
        check("12.5".equals(e.payload(SupplyEvents.K_UNIT_PRICE)), "price");
        check("4".equals(e.payload(SupplyEvents.K_PAR)), "par");
        check("GUDID".equals(e.payload(SupplyEvents.K_SOURCE)), "source");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
