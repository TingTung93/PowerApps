package org.commercialtracking;

import java.nio.file.Files;
import java.nio.file.Path;

final class SelfTest {
    private SelfTest() {}

    static void run() throws Exception {
        BarcodeParserChain parser = new BarcodeParserChain();
        require("UPS".equals(parser.parse("1Z999AA10123456784").getCarrier()), "UPS parser");
        require("USPS".equals(parser.parse("9400111899223856928499").getCarrier()), "USPS parser");
        require("FedEx".equals(parser.parse("123456789012").getCarrier()), "FedEx parser");
        require("ABC123456".equals(parser.parse("(401)ABC123456(420)98431").getTrackingNumber()),
                "GS1 parser");

        Path root = Files.createTempDirectory("commercial-tracking-self-test-");
        EventStore store = new EventStore(root.resolve("shared"), root.resolve("local"));
        TrackingEvent event = new TrackingEvent();
        event.eventType = "PACKAGE_RECEIVED";
        event.deviceId = "SELF-TEST";
        event.sessionId = "self-test";
        event.trackingNumber = "1Z999AA10123456784";
        event.carrier = "UPS";
        event.location = "Main Receiving";
        event.status = "READY_FOR_PICKUP";
        store.append(event);
        EventStore.LoadResult loaded = store.loadAll();
        require(loaded.errors.isEmpty(), "event load");
        require(loaded.events.size() == 1, "event count");
        Projection projection = new Projection();
        projection.replay(loaded.events);
        require("READY_FOR_PICKUP".equals(projection.find(event.trackingNumber).status), "event replay");
    }

    private static void require(boolean condition, String operation) {
        if (!condition) throw new IllegalStateException(operation + " failed");
    }
}
