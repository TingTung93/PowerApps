package org.commercialtracking;

import java.util.ArrayList;
import java.util.List;

public final class ProjectionTest {
    public static void main(String[] args) {
        List<TrackingEvent> events = new ArrayList<TrackingEvent>();
        events.add(received("1Z999AA10123456784", "2026-08-04T14:00:00Z"));
        events.add(locationChanged("1Z999AA10123456784", "2026-08-04T15:30:00Z", "Loading Dock"));

        Projection projection = new Projection();
        projection.replay(events);
        PackageState state = projection.find("1Z999AA10123456784");
        check(state != null, "package present after receive");
        check("2026-08-04T14:00:00Z".equals(state.receivedUtc),
                "receivedUtc from first receive = " + state.receivedUtc);

        // A later receive for the same package must NOT overwrite the original received timestamp.
        events.add(received("1Z999AA10123456784", "2026-08-05T09:00:00Z"));
        projection.replay(events);
        state = projection.find("1Z999AA10123456784");
        check("2026-08-04T14:00:00Z".equals(state.receivedUtc),
                "receivedUtc unchanged by later receive = " + state.receivedUtc);

        // A package that was never received has an empty receivedUtc.
        List<TrackingEvent> locationOnly = new ArrayList<TrackingEvent>();
        locationOnly.add(locationChanged("1Z000AA10000000000", "2026-08-04T10:00:00Z", "Mailroom"));
        projection.replay(locationOnly);
        PackageState orphan = projection.find("1Z000AA10000000000");
        check(orphan != null && "".equals(orphan.receivedUtc),
                "receivedUtc empty without a receive event");

        System.out.println("ProjectionTest: PASS");
    }

    private static TrackingEvent received(String tracking, String occurredUtc) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_RECEIVED";
        event.trackingNumber = tracking;
        event.occurredUtc = occurredUtc;
        event.deviceId = "TEST-01";
        event.eventId = "evt-" + occurredUtc;
        return event;
    }

    private static TrackingEvent locationChanged(String tracking, String occurredUtc, String location) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_LOCATION_CHANGED";
        event.trackingNumber = tracking;
        event.occurredUtc = occurredUtc;
        event.location = location;
        event.deviceId = "TEST-01";
        event.eventId = "evt-loc-" + occurredUtc;
        return event;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
