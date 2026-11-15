package com.appsflyer;

/** A stand-in for AppsFlyer's class of the same name (SDK 6.15+), for VisionForgeX's tests only. */
public final class AFAdRevenueData {
    public final String monetizationNetwork;
    public final MediationNetwork mediationNetwork;
    public final String currency;
    public final double revenue;

    public AFAdRevenueData(String monetizationNetwork, MediationNetwork mediationNetwork, String currencyIso4217Code, double revenue) {
        this.monetizationNetwork = monetizationNetwork;
        this.mediationNetwork = mediationNetwork;
        this.currency = currencyIso4217Code;
        this.revenue = revenue;
    }
}
