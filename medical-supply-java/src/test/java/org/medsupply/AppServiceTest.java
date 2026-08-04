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
        System.out.println("AppServiceTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
