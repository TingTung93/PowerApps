package org.medsupply;

public final class Gs1Scan {
    public String raw = "";
    public String gtin = "";
    public String lot = "";
    public String serial = "";
    public String count = "";
    public String expirationRaw = "";
    public String expirationIso = "";
    public boolean success;
    public boolean requiresConfirmation;
    public String note = "";

    public String itemKey() {
        return ItemKey.of(gtin, lot, expirationIso);
    }
}
