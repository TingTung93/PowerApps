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

        boolean overpick = false;
        try { svc.pick("00380740000010", "L1", "2026-11-30", 4); }
        catch (AppService.BadRequest ex) { overpick = true; }
        check(overpick, "negative inventory prevented");

        svc.adjust("00380740000010", "L1", "2026-11-30", 9);
        check(svc.stock().get(0).quantity == 9, "qty 9 after adjust");

        svc.archive("00380740000010", "L1", "2026-11-30", "expired");
        check(!svc.stock().get(0).active, "archived");

        String raw2 = "0100380740000010" + "10L2" + GS + "17261130";
        svc.receive(raw2, 2, false);
        svc.pick("00380740000010", "L2", "2026-11-30", 2);
        StockLine autoArchived = null;
        for (StockLine line : svc.stock()) if ("L2".equals(line.lot)) autoArchived = line;
        check(autoArchived != null && !autoArchived.active, "zero quantity auto-archived");
        int eventCountAfterPick = svc.events().size();
        check(SupplyEvents.STOCK_PICKED.equals(svc.events().get(eventCountAfterPick - 1).eventType),
                "zero archive is atomic in pick event");

        boolean phantomAdjust = false;
        try { svc.adjust("00380740000010", "MISSING", "", 4); }
        catch (AppService.BadRequest ex) { phantomAdjust = true; }
        check(phantomAdjust, "adjust cannot create phantom lot");
        boolean phantomArchive = false;
        try { svc.archive("00380740000010", "MISSING", "", "test"); }
        catch (AppService.BadRequest ex) { phantomArchive = true; }
        check(phantomArchive, "archive cannot create phantom lot");

        svc.retireProduct("00380740000010", "obsolete");
        boolean retiredReceive = false;
        try { svc.receive(raw, 1, false); } catch (AppService.BadRequest ex) { retiredReceive = true; }
        check(retiredReceive, "retired product cannot receive");
        boolean retiredPreview = false;
        try { svc.previewReceive(raw); } catch (AppService.BadRequest ex) { retiredPreview = true; }
        check(retiredPreview, "retired product is rejected during preview");
        boolean retiredEdit = false;
        try { svc.registerProduct("00380740000010", "Changed", "Abbott", "Category", 1, 1, "", "MANUAL"); }
        catch (AppService.BadRequest ex) { retiredEdit = true; }
        check(retiredEdit, "retired product cannot silently reactivate");
        boolean duplicateRetire = false;
        try { svc.retireProduct("00380740000010", "again"); }
        catch (AppService.BadRequest ex) { duplicateRetire = true; }
        check(duplicateRetire, "already-retired product cannot be retired twice");

        boolean threw = false;
        try { svc.receive("nonsense", 1, false); } catch (AppService.BadRequest ex) { threw = true; }
        check(threw, "bad barcode rejected");
        System.out.println("AppServiceStockTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
