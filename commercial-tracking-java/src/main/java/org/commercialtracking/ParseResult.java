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

    public ParseResult(String trackingNumber, String carrier, Confidence confidence,
                       String source, boolean confirmationRequired, Map<String, String> metadata) {
        this.trackingNumber = clean(trackingNumber);
        this.carrier = clean(carrier);
        this.confidence = confidence == null ? Confidence.NONE : confidence;
        this.source = clean(source);
        this.confirmationRequired = confirmationRequired;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier() { return carrier; }
    public Confidence getConfidence() { return confidence; }
    public String getSource() { return source; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public Map<String, String> getMetadata() { return metadata; }
    public boolean isSuccess() { return trackingNumber.length() > 0; }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
