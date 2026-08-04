package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AppServiceStockTest {
    private static final char GS = (char) 29;

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-stock");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        svc.configure(base.resolve("shared"));

        String raw = "0100380740000010" + "10L1" + GS + "17261130";
        Map<String, Object> unknown = svc.receive(raw, 5, false);
        check(Boolean.TRUE.equals(unknown.get("needsRegistration")), "unknown needs registration");
        check(svc.stock().isEmpty(), "nothing written yet");

        svc.registerProduct("00380740000010", "Stent", "Abbott", "Coronary stent", 10.0, 4, "", "MANUAL");
        Map<String, Object> received = svc.receive(raw, 5, false);
        check(Boolean.TRUE.equals(received.get("ok")), "received ok");
        check(svc.stock().get(0).quantity == 5, "qty 5");

        svc.pick("00380740000010", "L1", "2026-11-30", 2);
        check(svc.stock().get(0).quantity == 3, "qty 3 after pick");

        svc.adjust("00380740000010", "L1", "2026-11-30", 9);
        check(svc.stock().get(0).quantity == 9, "qty 9 after adjust");

        svc.archive("00380740000010", "L1", "2026-11-30", "expired");
        check(!svc.stock().get(0).active, "archived");

        boolean threw = false;
        try { svc.receive("nonsense", 1, false); } catch (AppService.BadRequest ex) { threw = true; }
        check(threw, "bad barcode rejected");
        System.out.println("AppServiceStockTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
