package org.medsupply;

public final class ReorderSuggestion {
    public String gtin = "";
    public String name = "";
    public int onHand;
    public boolean parProvided;
    public int par = -1;
    public double avgDailyUsage;
    public int reorderPoint;
    public int suggestedPar;
    public int suggestedOrderQty;
    public double estimatedCost;
    public boolean needsReorder;
    public boolean insufficientHistory;
}
