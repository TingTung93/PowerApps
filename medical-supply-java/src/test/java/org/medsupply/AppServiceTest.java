package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public final class AppServiceTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-svc");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        check(!svc.configured(), "not configured initially");

        svc.configure(base.resolve("shared"));
        check(svc.configured(), "configured after");

        // Seed via the store directly to prove reload + snapshot.
        SupplyEvents.Identity id = svc.identity();
        svc.store().append(SupplyEvents.productRegistered(id, "2026-08-01T00:00:00Z",
                "00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL"));
        svc.store().append(SupplyEvents.stockReceived(id, "2026-08-02T00:00:00Z",
                "00380740000010", "L1", "2026-11-30", "bc", 2));
        svc.reload();

        check(svc.stock().size() == 1, "one stock line");
        check(svc.stock().get(0).quantity == 2, "qty 2");

        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        Map<String, Object> snap = svc.snapshot(now);
        check(Boolean.TRUE.equals(snap.get("configured")), "snap configured");
        Map<String, Object> metrics = Json.asMap(snap.get("dashboard"));
        check("1".equals(Json.str(metrics, "distinctSkus")), "snap skus");
        check(Json.asList(snap.get("stock")).size() == 1, "snap stock");
        check(Json.asList(snap.get("reorder")).size() == 1, "snap reorder");
        svc.store().append(SupplyEvents.stockReceived(id, "2026-08-02T01:00:00Z",
                "00380740000010", "L1", "2026-11-30", "remote", 1));
        Map<String, Object> refreshed = svc.snapshot(now);
        Map<String, Object> refreshedLine = Json.asMap(Json.asList(refreshed.get("stock")).get(0));
        check("3".equals(Json.str(refreshedLine, "quantity")), "state poll reloads remote events");
        Map<String, Object> history = svc.itemHistory(
                "00380740000010", "L1", "2026-11-30");
        check(Json.asList(history.get("events")).size() == 2, "two lot events");
        check("2".equals(Json.str(Json.asMap(Json.asList(history.get("events")).get(0)),
                "balance")), "lot event balance");
        svc.store().append(SupplyEvents.stockPicked(id, "2026-08-02T02:00:00Z",
                "00380740000010", "L1", "2026-11-30", 10));
        Map<String, Object> unsafe = svc.snapshot(now);
        check(Boolean.FALSE.equals(unsafe.get("trailComplete")),
                "negative replay cannot be certified complete");
        System.out.println("AppServiceTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
