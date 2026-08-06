package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ManagementReportTest {
    public static void main(String[] args) throws Exception {
        DashboardMetrics m = new DashboardMetrics();
        m.distinctSkus = 2;
        m.expired = 1;
        m.onHandValue = 123.5;

        List<ReorderSuggestion> reorder = new ArrayList<ReorderSuggestion>();
        ReorderSuggestion s = new ReorderSuggestion();
        s.gtin = "00380740000010";
        s.name = "Gauze <sterile>";
        s.onHand = 4;
        s.par = 10;
        s.parProvided = true;
        s.needsReorder = true;
        s.suggestedOrderQty = 6;
        s.estimatedCost = 12.0;
        reorder.add(s);

        List<StockLine> stock = new ArrayList<StockLine>();
        StockLine line = new StockLine();
        line.name = "Gauze";
        line.expirationIso = "2026-08-01";
        line.quantity = 4;
        line.active = true;
        stock.add(line);

        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        String html = ManagementReport.renderHtml(m, reorder, stock, now);
        check(html.contains("Gauze &lt;sterile&gt;"), "escaped name");
        check(html.contains("123.5") || html.contains("123.50"), "value shown");
        check(html.contains("Reorder"), "reorder section");

        String csv = ManagementReport.renderReorderCsv(reorder);
        check(csv.startsWith("gtin,name,onHand,par,suggestedOrderQty,estimatedCost"), "csv header");
        check(csv.contains("\"Gauze <sterile>\""), "csv quoted name");

        Path dir = Files.createTempDirectory("medsupply-report");
        ManagementReport.Result r = ManagementReport.write(dir, m, reorder, stock, now);
        check(Files.isRegularFile(r.html) && Files.isRegularFile(r.csv), "files written");
        check(Files.isRegularFile(r.pdf), "pdf written");
        check(new String(Files.readAllBytes(r.pdf), "ISO-8859-1").startsWith("%PDF"),
                "pdf header");
        System.out.println("ManagementReportTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
