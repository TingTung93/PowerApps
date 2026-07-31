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
        String key = event.trackingNumber.toUpperCase();
        PackageState state = packages.get(key);
        if (state == null) {
            state = new PackageState();
            state.trackingNumber = event.trackingNumber;
            packages.put(key, state);
        }
        if ("PACKAGE_RECEIVED".equals(event.eventType)) {
            if ("READY_FOR_PICKUP".equals(state.status)) {
                conflicts.add("Duplicate active receive: " + event.trackingNumber + " (" + state.lastDevice
                        + " and " + event.deviceId + ")");
            }
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
        } else if ("RECIPIENT_ASSIGNED".equals(event.eventType)) {
            state.recipient = event.recipient;
        }
        if (event.carrier.length() > 0) state.carrier = event.carrier;
        if (event.location.length() > 0) state.location = event.location;
        if (event.recipient.length() > 0) state.recipient = event.recipient;
        state.lastEventUtc = event.occurredUtc;
        state.lastDevice = event.deviceId;
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
