package org.commercialtracking;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EventStoreTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("commercial-tracking-test-");
        EventStore store = new EventStore(base.resolve("shared"), base.resolve("local"));
        TrackingEvent receive = event("PACKAGE_RECEIVED", "1Z999AA10123456784", "WS-A");
        store.append(receive);
        String stored = new String(Files.readAllBytes(findJson(base.resolve("shared"))), "UTF-8");
        check(stored.contains("\"payload\""), "versioned payload envelope expected");
        check(stored.contains("\"windowsAccount\""), "actor envelope expected");
        TrackingEvent enveloped = EventJson.read(stored.substring(0, stored.length() - 1)
                .replaceFirst("\\}$", ",\"futureField\":\"ignored\"}"));
        check(receive.eventId.equals(enveloped.eventId), "unknown envelope fields ignored");
        check(Files.isRegularFile(base.resolve("local").resolve("pending").resolve(receive.eventId + ".tmp")),
                "Pending event must remain until rediscovery");
        TrackingEvent release = event("PACKAGE_RELEASED", "1Z999AA10123456784", "WS-B");
        store.append(release);
        EventStore.LoadResult loaded = store.loadAll();
        check(loaded.errors.isEmpty(), "No load errors expected");
        check(loaded.events.size() == 2, "Two events expected");
        check(!Files.exists(base.resolve("local").resolve("pending").resolve(receive.eventId + ".tmp")),
                "Observed pending event should be cleaned up");
        TrackingEvent recoverable = event("PACKAGE_RECEIVED", "RECOVERY-TRACKING", "WS-R");
        Path finalized = store.append(recoverable);
        Files.delete(finalized);
        check(store.retryPending().recovered == 1, "pending event should be safely retried");
        check(store.loadAll().events.size() == 3, "retried event should be rediscovered");
        Files.deleteIfExists(base.resolve("local").resolve("cache").resolve("event-index.tsv"));
        EventStore rebuiltStore = new EventStore(base.resolve("shared"), base.resolve("local"));
        EventStore.LoadResult rebuilt = rebuiltStore.loadAll();
        check(rebuilt.events.size() == 3, "cache deletion must permit complete replay");
        Projection rebuiltProjection = new Projection();
        rebuiltProjection.replay(rebuilt.events);
        check("PICKED_UP".equals(rebuiltProjection.find(receive.trackingNumber).status),
                "cache rebuild must reproduce package projection");
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
        TrackingEvent firstRelease = event("PACKAGE_RELEASED", receive.trackingNumber, "WS-A");
        firstRelease.occurredUtc = java.time.Instant.parse(receive.occurredUtc).plusSeconds(1).toString();
        TrackingEvent secondRelease = event("PACKAGE_RELEASED", receive.trackingNumber, "WS-B");
        secondRelease.occurredUtc = java.time.Instant.parse(receive.occurredUtc).plusSeconds(2).toString();
        TrackingEvent resolution = event("CONFLICT_RESOLVED", receive.trackingNumber, "WS-SUPERVISOR");
        resolution.occurredUtc = java.time.Instant.parse(receive.occurredUtc).plusSeconds(3).toString();
        resolution.status = "PICKED_UP";
        java.util.List<TrackingEvent> resolved = new java.util.ArrayList<TrackingEvent>();
        resolved.add(receive); resolved.add(firstRelease); resolved.add(secondRelease); resolved.add(resolution);
        conflictProjection.replay(resolved);
        check("PICKED_UP".equals(conflictProjection.find(receive.trackingNumber).status), "Resolution outcome expected");
        check(conflictProjection.conflicts().isEmpty(), "Resolved conflict should leave attention queue");
        TrackingEvent correction = event("PACKAGE_CORRECTED", receive.trackingNumber, "WS-SUPERVISOR");
        correction.recipient = "J. Smith";
        correction.location = "Mailroom";
        resolved.add(correction);
        conflictProjection.replay(resolved);
        check("J. Smith".equals(conflictProjection.find(receive.trackingNumber).recipient), "Correction recipient expected");
        check("Mailroom".equals(conflictProjection.find(receive.trackingNumber).location), "Correction location expected");
        Path partial = base.resolve("shared").resolve("events").resolve("ignored.partial");
        Files.write(partial, "incomplete".getBytes("UTF-8"));
        check(store.loadAll().events.size() == 3, "Partial files must be ignored");
        TrackingEvent duplicate = event("PACKAGE_RECEIVED", "DIFFERENT-TRACKING", "WS-X");
        duplicate.eventId = receive.eventId;
        duplicate.occurredUtc = receive.occurredUtc;
        String duplicateName = "duplicate_" + duplicate.eventId + ".json";
        Path duplicatePath = base.resolve("shared").resolve("events").resolve(duplicateName);
        Files.write(duplicatePath, JsonFlat.write(duplicate.fields()).getBytes("UTF-8"));
        check(!store.loadAll().errors.isEmpty(), "Different content for duplicate event ID must be reported");
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

    private static Path findJson(Path root) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json")).findFirst().get();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
