package com.adjust.sdk;

import java.util.ArrayList;
import java.util.List;

/** A stand-in for Adjust's class of the same name, for VisionForgeX's tests only: it records each call. */
public class Adjust {
    public static final List<AdjustAdRevenue> tracked = new ArrayList<>();
    public static void trackAdRevenue(AdjustAdRevenue adRevenue) { tracked.add(adRevenue); }
}
