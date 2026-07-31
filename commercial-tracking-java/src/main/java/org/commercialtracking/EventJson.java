package org.commercialtracking;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EventJson {
    private EventJson() {}

    public static String write(TrackingEvent event) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("schemaVersion", Integer.valueOf(event.schemaVersion));
        root.put("eventId", event.eventId);
        root.put("eventType", event.eventType);
        root.put("occurredUtc", event.occurredUtc);
        root.put("recordedUtc", event.recordedUtc);
        root.put("deviceId", event.deviceId);
        root.put("sessionId", event.sessionId);
        root.put("streamId", event.streamId);
        Map<String, Object> actor = new LinkedHashMap<String, Object>();
        actor.put("windowsAccount", event.actor);
        actor.put("displayName", "");
        root.put("actor", actor);
        root.put("packageKey", event.trackingNumber.length() == 0 ? "" : "TRACKING:" + event.trackingNumber);
        root.put("observedRevision", Integer.valueOf(event.observedRevision));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> field : event.fields().entrySet()) {
            String key = field.getKey();
            if (isEnvelope(key)) continue;
            payload.put(key, field.getValue());
        }
        root.put("payload", payload);
        return JsonOutput.write(root) + "\n";
    }

    public static TrackingEvent read(String json) {
        if (json == null || json.length() > 1024 * 1024) throw new IllegalArgumentException("Invalid JSON size");
        if (!json.contains("\"payload\"")) return TrackingEvent.from(JsonFlat.read(json));
        Map<String, String> fields = new LinkedHashMap<String, String>();
        String[] keys = {
                "schemaVersion", "eventId", "eventType", "occurredUtc", "recordedUtc", "deviceId",
                "sessionId", "streamId", "windowsAccount", "observedRevision", "trackingNumber",
                "carrier", "location", "recipient", "status", "parserSource", "parserConfidence",
                "weight", "packageCount", "addressee", "address", "notes", "referenceEventId",
                "manifestId", "rawBarcodeHash", "rawBarcode"
        };
        for (String key : keys) {
            String value = findValue(json, key);
            if (value != null) fields.put("windowsAccount".equals(key) ? "actor" : key, value);
        }
        return TrackingEvent.from(fields);
    }

    private static boolean isEnvelope(String key) {
        return "schemaVersion".equals(key) || "eventId".equals(key) || "eventType".equals(key)
                || "occurredUtc".equals(key) || "recordedUtc".equals(key) || "deviceId".equals(key)
                || "sessionId".equals(key) || "streamId".equals(key) || "actor".equals(key)
                || "observedRevision".equals(key);
    }

    private static String findValue(String json, String key) {
        String marker = "\"" + key + "\"";
        int at = json.indexOf(marker);
        if (at < 0) return null;
        int colon = json.indexOf(':', at + marker.length());
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length()) return null;
        if (json.charAt(p) != '"') {
            int end = p;
            while (end < json.length() && ",}] \r\n\t".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(p, end);
        }
        int start = p++;
        boolean escaped = false;
        while (p < json.length()) {
            char c = json.charAt(p++);
            if (c == '"' && !escaped) {
                String token = json.substring(start, p);
                return JsonFlat.read("{\"value\":" + token + "}").get("value");
            }
            escaped = c == '\\' && !escaped;
            if (c != '\\') escaped = false;
        }
        throw new IllegalArgumentException("Unterminated JSON value for " + key);
    }
}
