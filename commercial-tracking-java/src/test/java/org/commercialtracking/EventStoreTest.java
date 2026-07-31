package org.commercialtracking;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EventStoreTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("commercial-tracking-test-");
        EventStore store = new EventStore(base.resolve("shared"), base.resolve("local"));
        TrackingEvent receive = event("PACKAGE_RECEIVED", "1Z999AA10123456784", "WS-A");
        store.append(receive);
        TrackingEvent release = event("PACKAGE_RELEASED", "1Z999AA10123456784", "WS-B");
        store.append(release);
        EventStore.LoadResult loaded = store.loadAll();
        check(loaded.errors.isEmpty(), "No load errors expected");
        check(loaded.events.size() == 2, "Two events expected");
        Projection projection = new Projection();
        projection.replay(loaded.events);
        check("PICKED_UP".equals(projection.find(receive.trackingNumber).status), "Expected picked up");
        String json = JsonFlat.write(receive.fields());
        TrackingEvent decoded = TrackingEvent.from(JsonFlat.read(json));
        check(receive.eventId.equals(decoded.eventId), "JSON round trip");
        TrackingEvent secondReceive = event("PACKAGE_RECEIVED", receive.trackingNumber, "WS-C");
        Projection conflictProjection = new Projection();
        java.util.List<TrackingEvent> duplicateReceives = new java.util.ArrayList<TrackingEvent>();
        duplicateReceives.add(receive);
        duplicateReceives.add(secondReceive);
        conflictProjection.replay(duplicateReceives);
        check(!conflictProjection.conflicts().isEmpty(), "Duplicate receive conflict expected");
        System.out.println("EventStoreTest: PASS");
    }

    private static TrackingEvent event(String type, String tracking, String device) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = type;
        event.trackingNumber = tracking;
        event.deviceId = device;
        event.sessionId = "test";
        event.location = "Main Receiving";
        event.carrier = "UPS";
        event.status = "PACKAGE_RECEIVED".equals(type) ? "READY_FOR_PICKUP" : "PICKED_UP";
        return event;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
