package org.commercialtracking;

public final class PackageState {
    public String trackingNumber = "";
    public String carrier = "";
    public String location = "";
    public String recipient = "";
    public String status = "";
    public String lastEventUtc = "";
    public String lastDevice = "";
    public int revision;

    public PackageState copy() {
        PackageState p = new PackageState();
        p.trackingNumber = trackingNumber;
        p.carrier = carrier;
        p.location = location;
        p.recipient = recipient;
        p.status = status;
        p.lastEventUtc = lastEventUtc;
        p.lastDevice = lastDevice;
        p.revision = revision;
        return p;
    }
}
