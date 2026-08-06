package org.medsupply;

public final class SupplyEventJsonTest {
    public static void main(String[] args) {
        roundTrip();
        readToleratesMissingPayload();
        System.out.println("SupplyEventJsonTest: PASS");
    }

    private static void roundTrip() {
        SupplyEvent e = new SupplyEvent();
        e.eventType = "STOCK_RECEIVED";
        e.deviceId = "WS-1";
        e.actor = "DOM\\alice";
        e.occurredUtc = "2026-08-03T10:00:00Z";
        e.recordedUtc = "2026-08-03T10:00:01Z";
        e.payload.put("gtin", "00380740000010");
        e.payload.put("lot", "AB\"12");
        e.payload.put("quantityDelta", "5");

        SupplyEvent back = SupplyEventJson.read(SupplyEventJson.write(e));
        check("STOCK_RECEIVED".equals(back.eventType), "type");
        check(e.eventId.equals(back.eventId), "id");
        check("WS-1".equals(back.deviceId), "device");
        check("2026-08-03T10:00:00Z".equals(back.occurredUtc), "occurred");
        check("00380740000010".equals(back.payload("gtin")), "gtin");
        check("AB\"12".equals(back.payload("lot")), "lot escaped");
        check("5".equals(back.payload("quantityDelta")), "delta");
    }

    private static void readToleratesMissingPayload() {
        String json = "{\"schemaVersion\":1,\"eventId\":\"x\",\"eventType\":\"T\","
                + "\"occurredUtc\":\"2026-01-01T00:00:00Z\",\"recordedUtc\":\"2026-01-01T00:00:00Z\","
                + "\"deviceId\":\"D\",\"sessionId\":\"S\",\"actor\":\"A\"}";
        SupplyEvent e = SupplyEventJson.read(json);
        check("T".equals(e.eventType), "type without payload");
        check(e.payload.isEmpty(), "empty payload");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
