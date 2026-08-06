package org.commercialtracking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ParseResult {
    public enum Confidence { VERIFIED, HIGH, MEDIUM, LOW, NONE }

    private final String trackingNumber;
    private final String carrier;
    private final Confidence confidence;
    private final String source;
    private final boolean confirmationRequired;
    private final Map<String, String> metadata;
    private final Map<String, FieldEvidence> evidence;

    public ParseResult(String trackingNumber, String carrier, Confidence confidence,
                       String source, boolean confirmationRequired, Map<String, String> metadata) {
        this.trackingNumber = clean(trackingNumber);
        this.carrier = clean(carrier);
        this.confidence = confidence == null ? Confidence.NONE : confidence;
        this.source = clean(source);
        this.confirmationRequired = confirmationRequired;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
        Map<String, FieldEvidence> fields = new LinkedHashMap<String, FieldEvidence>();
        if (this.trackingNumber.length() > 0)
            fields.put("trackingNumber", new FieldEvidence(this.trackingNumber, this.confidence, this.source, this.confirmationRequired));
        if (this.carrier.length() > 0)
            fields.put("carrier", new FieldEvidence(this.carrier, this.confidence, this.source, this.confirmationRequired));
        for (Map.Entry<String, String> entry : this.metadata.entrySet())
            fields.put(entry.getKey(), new FieldEvidence(entry.getValue(), this.confidence, this.source, this.confirmationRequired));
        evidence = Collections.unmodifiableMap(fields);
    }

    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier() { return carrier; }
    public Confidence getConfidence() { return confidence; }
    public String getSource() { return source; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public Map<String, String> getMetadata() { return metadata; }
    public Map<String, FieldEvidence> getEvidence() { return evidence; }
    public boolean isSuccess() { return trackingNumber.length() > 0; }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class FieldEvidence {
        public final String value;
        public final Confidence confidence;
        public final String source;
        public final boolean requiresConfirmation;
        FieldEvidence(String value, Confidence confidence, String source, boolean requiresConfirmation) {
            this.value = value;
            this.confidence = confidence;
            this.source = source;
            this.requiresConfirmation = requiresConfirmation;
        }
    }
}
