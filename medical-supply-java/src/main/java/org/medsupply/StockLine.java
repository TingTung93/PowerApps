package org.medsupply;

public final class StockLine {
    public String itemKey = "";
    public String gtin = "";
    public String lot = "";
    public String expirationIso = "";
    public String barcode = "";
    public int quantity;
    public boolean active = true;
    public String lastEventUtc = "";
    public String lastDevice = "";

    // Enriched from catalog by GTIN.
    public String name = "";
    public String manufacturer = "";
    public String category = "";
    public double unitPrice;
    public int par = -1;
}
