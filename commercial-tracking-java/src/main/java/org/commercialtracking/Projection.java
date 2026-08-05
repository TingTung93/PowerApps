package org.commercialtracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Projection {
    private final Map<String, PackageState> packages = new LinkedHashMap<String, PackageState>();
    private final List<String> conflicts = new ArrayList<String>();

    public void replay(List<TrackingEvent> events) {
        packages.clear();
        conflicts.clear();
        for (TrackingEvent event : events) apply(event);
    }

    private void apply(TrackingEvent event) {
        if (event.trackingNumber.length() == 0) return;
        String key = event.trackingNumber.toUpperCase();
        PackageState state = packages.get(key);
        if (state == null) {
            state = new PackageState();
            state.trackingNumber = event.trackingNumber;
            packages.put(key, state);
        }
        if ("CONFLICT".equals(state.status) && !"CONFLICT_RESOLVED".equals(event.eventType)) {
            state.lastEventUtc = event.occurredUtc;
            state.lastDevice = event.deviceId;
            state.lastEventId = event.eventId;
            state.revision++;
            return;
        }
        boolean staleTransition = event.observedRevision > 0 && event.observedRevision < state.revision
                && ("PACKAGE_RELEASED".equals(event.eventType) || "PACKAGE_VOIDED".equals(event.eventType)
                || "PACKAGE_CORRECTED".equals(event.eventType) || "PACKAGE_LOCATION_CHANGED".equals(event.eventType));
        if (staleTransition) {
            conflicts.add("Stale " + event.eventType.toLowerCase().replace('_', ' ') + ": "
                    + event.trackingNumber + " (" + event.deviceId + ")");
            state.status = "CONFLICT";
            state.lastEventUtc = event.occurredUtc;
            state.lastDevice = event.deviceId;
            state.lastEventId = event.eventId;
            state.revision++;
            return;
        }
        if ("PACKAGE_RECEIVED".equals(event.eventType)) {
            if ("READY_FOR_PICKUP".equals(state.status)) {
                conflicts.add("Duplicate active receive: " + event.trackingNumber + " (" + state.lastDevice
                        + " and " + event.deviceId + ")");
            }
            if (state.receivedUtc.length() == 0) state.receivedUtc = event.occurredUtc;
            state.status = "READY_FOR_PICKUP";
        } else if ("PACKAGE_LOCATION_CHANGED".equals(event.eventType)) {
            if (state.status.length() == 0) state.status = "READY_FOR_PICKUP";
        } else if ("PACKAGE_RELEASED".equals(event.eventType)) {
            if ("PICKED_UP".equals(state.status)) {
                conflicts.add("Competing release: " + event.trackingNumber + " (" + state.lastDevice
                        + " and " + event.deviceId + ")");
                state.status = "CONFLICT";
            } else if (!"READY_FOR_PICKUP".equals(state.status)) {
                conflicts.add("Release without active receive: " + event.trackingNumber);
                state.status = "CONFLICT";
            } else {
                state.status = "PICKED_UP";
            }
        } else if ("PACKAGE_VOIDED".equals(event.eventType)) {
            state.status = "VOIDED";
        } else if ("RECIPIENT_ASSIGNED".equals(event.eventType)
                || "PACKAGE_RECIPIENT_ASSIGNED".equals(event.eventType)) {
            state.recipient = event.recipient;
        } else if ("PACKAGE_CORRECTED".equals(event.eventType)) {
            if (event.location.length() > 0) state.location = event.location;
            if (event.recipient.length() > 0) state.recipient = event.recipient;
        } else if ("CONFLICT_RESOLVED".equals(event.eventType)) {
            state.status = event.status.length() == 0 ? "READY_FOR_PICKUP" : event.status;
            final String tracking = event.trackingNumber.toUpperCase();
            conflicts.removeIf(value -> value.toUpperCase().contains(tracking));
        } else if ("MANIFEST_PREPARED".equals(event.eventType)) {
            state.manifestId = event.manifestId;
        }
        if (event.carrier.length() > 0) state.carrier = event.carrier;
        if (event.location.length() > 0) state.location = event.location;
        if (event.recipient.length() > 0) state.recipient = event.recipient;
        state.lastEventUtc = event.occurredUtc;
        state.lastDevice = event.deviceId;
        state.lastEventId = event.eventId;
        if (event.manifestId.length() > 0) state.manifestId = event.manifestId;
        state.revision++;
    }

    public PackageState find(String tracking) {
        PackageState state = packages.get(tracking.toUpperCase());
        return state == null ? null : state.copy();
    }

    public List<PackageState> all() {
        List<PackageState> result = new ArrayList<PackageState>();
        for (PackageState state : packages.values()) result.add(state.copy());
        Collections.sort(result, (a, b) -> b.lastEventUtc.compareTo(a.lastEventUtc));
        return result;
    }

    public List<String> conflicts() { return new ArrayList<String>(conflicts); }
}
