package org.medsupply;

public final class SwingRowsTest {
    public static void main(String[] args) {
        StockLine line = new StockLine();
        line.name = "Stent";
        line.gtin = "00380740000010";
        line.lot = "L1";
        line.expirationIso = "2026-11-30";
        line.quantity = 7;
        Object[] row = SwingApp.stockRow(line);
        check(row.length == 5, "5 columns");
        check("Stent".equals(row[0]), "name");
        check("00380740000010".equals(row[1]), "gtin");
        check("L1".equals(row[2]), "lot");
        check("2026-11-30".equals(row[3]), "exp");
        check(Integer.valueOf(7).equals(row[4]), "qty");
        System.out.println("SwingRowsTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
