package org.medsupply;

public final class CatalogProduct {
    public String gtin = "";
    public String name = "";
    public String manufacturer = "";
    public String category = "";
    public String notes = "";
    public String source = "";
    public double unitPrice;
    public int par = -1;

    public boolean hasPar() {
        return par >= 0;
    }
}
