package org.medsupply;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupplyEventJson {
    private SupplyEventJson() {}

    public static String write(SupplyEvent event) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("schemaVersion", Integer.valueOf(parseIntOr(event.schemaVersion, 1)));
        root.put("eventId", event.eventId);
        root.put("eventType", event.eventType);
        root.put("occurredUtc", event.occurredUtc);
        root.put("recordedUtc", event.recordedUtc);
        root.put("deviceId", event.deviceId);
        root.put("sessionId", event.sessionId);
        root.put("actor", event.actor);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> field : event.payload.entrySet())
            payload.put(field.getKey(), field.getValue());
        root.put("payload", payload);
        return Json.write(root) + "\n";
    }

    public static SupplyEvent read(String json) {
        if (json == null || json.length() > 1024 * 1024)
            throw new IllegalArgumentException("Invalid JSON size");
        Map<String, Object> root = Json.asMap(Json.parse(json));
        SupplyEvent e = new SupplyEvent();
        e.schemaVersion = Json.str(root, "schemaVersion");
        e.eventId = Json.str(root, "eventId");
        e.eventType = Json.str(root, "eventType");
        e.occurredUtc = Json.str(root, "occurredUtc");
        e.recordedUtc = Json.str(root, "recordedUtc");
        e.deviceId = Json.str(root, "deviceId");
        e.sessionId = Json.str(root, "sessionId");
        e.actor = Json.str(root, "actor");
        Map<String, Object> payload = Json.asMap(root.get("payload"));
        for (Map.Entry<String, Object> entry : payload.entrySet())
            e.payload.put(entry.getKey(), entry.getValue() == null ? "" : Json.str(payload, entry.getKey()));
        return e;
    }

    private static int parseIntOr(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return fallback; }
    }
}
