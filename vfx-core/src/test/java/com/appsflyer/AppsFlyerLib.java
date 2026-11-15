package com.appsflyer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A stand-in for AppsFlyer's class of the same name (SDK 6.15+), for VisionForgeX's tests only: it records each call. */
public class AppsFlyerLib {
    private static final AppsFlyerLib INSTANCE = new AppsFlyerLib();
    public static final List<AFAdRevenueData> logged = new ArrayList<>();
    public static final List<Map<String, Object>> extras = new ArrayList<>();

    public static AppsFlyerLib getInstance() { return INSTANCE; }

    public void logAdRevenue(AFAdRevenueData data, Map<String, Object> additionalParameters) {
        logged.add(data);
        extras.add(additionalParameters);
    }
}
