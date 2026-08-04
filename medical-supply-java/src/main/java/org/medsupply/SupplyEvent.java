package org.medsupply;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
public final class SupplyEvent {
    public String schemaVersion = SupplyMeta.SCHEMA_VERSION;
    public String eventId = UUID.randomUUID().toString();
    public String eventType = "";
    public String occurredUtc = Instant.now().toString();
    public String recordedUtc = Instant.now().toString();
    public String deviceId = "";
    public String sessionId = "";
    public String actor = "";
    public final Map<String,String> payload = new LinkedHashMap<String,String>();
    public String payload(String key) { String value = payload.get(key); return value == null ? "" : value; }
}
