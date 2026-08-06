package org.commercialtracking;

import java.util.ArrayList;
import java.util.List;

public final class PerformanceSmokeTest {
    private static final int EVENT_COUNT = 100000;

    public static void main(String[] args) {
        List<TrackingEvent> events = new ArrayList<TrackingEvent>(EVENT_COUNT);
        for (int i = 0; i < EVENT_COUNT; i++) {
            TrackingEvent event = new TrackingEvent();
            event.eventId = String.format("00000000-0000-0000-0000-%012d", i);
            event.eventType = "PACKAGE_RECEIVED";
            event.occurredUtc = "2026-07-30T00:00:00.000Z";
            event.recordedUtc = "2026-07-30T00:00:00.000Z";
            event.deviceId = i % 2 == 0 ? "WS-A" : "WS-B";
            event.trackingNumber = String.format("SYNTH%014d", i);
            event.carrier = "Synthetic";
            event.location = i % 2 == 0 ? "Main Receiving" : "Mailroom";
            event.status = "READY_FOR_PICKUP";
            events.add(event);
        }

        Projection projection = new Projection();
        long replayStart = System.nanoTime();
        projection.replay(events);
        long replayMillis = elapsed(replayStart);
        check(projection.all().size() == EVENT_COUNT, "100k package projection");
        check(replayMillis <= 10000, "100k replay exceeded 10 seconds: " + replayMillis + " ms");

        long lookupStart = System.nanoTime();
        for (int i = 0; i < 1000; i++)
            check(projection.find(String.format("SYNTH%014d", EVENT_COUNT - 1 - i)) != null,
                    "exact lookup");
        long lookupMillis = elapsed(lookupStart);
        check(lookupMillis <= 1000, "1000 exact lookups exceeded one second: " + lookupMillis + " ms");
        System.out.println("PerformanceSmokeTest: PASS (100k replay " + replayMillis
                + " ms; 1000 lookups " + lookupMillis + " ms)");
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1000000L;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
