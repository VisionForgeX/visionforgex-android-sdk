# VisionForgeX SDK: the engine is found by name at start.
-keep class com.visionforgex.ads.gma.GmaEngine { <init>(); }
-keep class * implements com.visionforgex.ads.engine.AdEngine { <init>(); }
# The attribution tools ad revenue is forwarded to, found by name when the app has them (RevenueForwarder).
# Kept only if the app has them; nothing is added to an app without them.
-keep class com.appsflyer.AppsFlyerLib { public static ** getInstance(); public ** logAdRevenue(...); }
-keep class com.appsflyer.AFAdRevenueData { public <init>(...); }
-keep enum com.appsflyer.MediationNetwork { *; }
-keep class com.appsflyer.AdRevenueScheme { public static <fields>; }
-keep class com.adjust.sdk.Adjust { public static ** trackAdRevenue(...); }
-keep class com.adjust.sdk.AdjustAdRevenue { public <init>(...); public ** setRevenue(...); public ** setAdRevenueUnit(...); public ** setAdRevenuePlacement(...); }
-dontwarn com.appsflyer.**
-dontwarn com.adjust.sdk.**
