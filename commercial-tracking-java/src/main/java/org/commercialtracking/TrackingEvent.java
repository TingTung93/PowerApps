package org.commercialtracking;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TrackingEvent {
    public String schemaVersion = "1";
    public String eventId = UUID.randomUUID().toString();
    public String eventType = "";
    public String occurredUtc = Instant.now().toString();
    public String recordedUtc = Instant.now().toString();
    public String deviceId = "";
    public String sessionId = "";
    public String streamId = "";
    public String actor = "";
    public String actorDisplayName = "";
    public String trackingNumber = "";
    public String carrier = "";
    public String location = "";
    public String recipient = "";
    public String status = "";
    public String parserSource = "";
    public String parserConfidence = "";
    public String weight = "";
    public String packageCount = "";
    public String addressee = "";
    public String address = "";
    public String notes = "";
    public String referenceEventId = "";
    public String manifestId = "";
    public int observedRevision;
    public String rawBarcodeHash = "";
    public String rawBarcode = "";

    public Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("schemaVersion", schemaVersion);
        fields.put("eventId", eventId);
        fields.put("eventType", eventType);
        fields.put("occurredUtc", occurredUtc);
        fields.put("recordedUtc", recordedUtc);
        fields.put("deviceId", deviceId);
        fields.put("sessionId", sessionId);
        fields.put("streamId", streamId);
        fields.put("actor", actor);
        fields.put("actorDisplayName", actorDisplayName);
        fields.put("trackingNumber", trackingNumber);
        fields.put("carrier", carrier);
        fields.put("location", location);
        fields.put("recipient", recipient);
        fields.put("status", status);
        fields.put("parserSource", parserSource);
        fields.put("parserConfidence", parserConfidence);
        fields.put("weight", weight);
        fields.put("packageCount", packageCount);
        fields.put("addressee", addressee);
        fields.put("address", address);
        fields.put("notes", notes);
        fields.put("referenceEventId", referenceEventId);
        fields.put("manifestId", manifestId);
        fields.put("observedRevision", String.valueOf(observedRevision));
        fields.put("rawBarcodeHash", rawBarcodeHash);
        fields.put("rawBarcode", rawBarcode);
        return fields;
    }

    public static TrackingEvent from(Map<String, String> fields) {
        TrackingEvent e = new TrackingEvent();
        e.schemaVersion = value(fields, "schemaVersion");
        e.eventId = value(fields, "eventId");
        e.eventType = value(fields, "eventType");
        e.occurredUtc = value(fields, "occurredUtc");
        e.recordedUtc = value(fields, "recordedUtc");
        if (e.recordedUtc.length() == 0) e.recordedUtc = e.occurredUtc;
        e.deviceId = value(fields, "deviceId");
        e.sessionId = value(fields, "sessionId");
        e.streamId = value(fields, "streamId");
        e.actor = value(fields, "actor");
        e.actorDisplayName = value(fields, "actorDisplayName");
        e.trackingNumber = value(fields, "trackingNumber");
        e.carrier = value(fields, "carrier");
        e.location = value(fields, "location");
        e.recipient = value(fields, "recipient");
        e.status = value(fields, "status");
        e.parserSource = value(fields, "parserSource");
        e.parserConfidence = value(fields, "parserConfidence");
        e.weight = value(fields, "weight");
        e.packageCount = value(fields, "packageCount");
        e.addressee = value(fields, "addressee");
        e.address = value(fields, "address");
        e.notes = value(fields, "notes");
        e.referenceEventId = value(fields, "referenceEventId");
        e.manifestId = value(fields, "manifestId");
        try { e.observedRevision = Integer.parseInt(value(fields, "observedRevision")); }
        catch (NumberFormatException ignored) { e.observedRevision = 0; }
        e.rawBarcodeHash = value(fields, "rawBarcodeHash");
        e.rawBarcode = value(fields, "rawBarcode");
        return e;
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }
}
