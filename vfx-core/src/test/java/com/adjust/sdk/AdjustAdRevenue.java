package com.adjust.sdk;

/** A stand-in for Adjust's class of the same name, for VisionForgeX's tests only. */
public class AdjustAdRevenue {
    public final String source;
    public Double revenue;
    public String currency;
    public String unit;
    public String placement;

    public AdjustAdRevenue(String source) { this.source = source; }
    public void setRevenue(Double revenue, String currency) { this.revenue = revenue; this.currency = currency; }
    public void setAdRevenueUnit(String unit) { this.unit = unit; }
    public void setAdRevenuePlacement(String placement) { this.placement = placement; }
}
