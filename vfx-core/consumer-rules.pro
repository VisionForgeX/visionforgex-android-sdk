# VisionForgeX SDK: the engine is found by name at start.
-keep class com.visionforgex.ads.gma.GmaEngine { <init>(); }
-keep class * implements com.visionforgex.ads.engine.AdEngine { <init>(); }
