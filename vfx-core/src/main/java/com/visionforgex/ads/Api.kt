package com.visionforgex.ads

/** The ad formats, as the platform names them. */
public enum class VfxFormat(public val wire: String) {
    BANNER("banner"),
    INTERSTITIAL("interstitial"),
    REWARDED("rewarded"),
    REWARDED_INTERSTITIAL("rewarded_interstitial"),
    APP_OPEN("app_open"),
    NATIVE("native");

    public val isFullScreen: Boolean get() = this != BANNER && this != NATIVE
    public val isRewarded: Boolean get() = this == REWARDED || this == REWARDED_INTERSTITIAL

    public companion object {
        @JvmStatic public fun fromWire(s: String): VfxFormat? = entries.firstOrNull { it.wire == s }
    }
}

/** How much the SDK writes to Logcat (tag "VisionForgeX"). */
public enum class VfxLogLevel { NONE, ERROR, WARN, INFO, DEBUG }

/**
 * Options for [VisionForgeX.initialize]. Everything that decides revenue comes from the platform;
 * these are only for building and testing the app.
 */
public class VfxOptions private constructor(
    /** Google test devices: their ads are test ads (Logcat prints the ID on the first request). */
    public val testDeviceIds: List<String>,
    /** Logcat output; null: as the platform says (warnings, or everything in test mode). */
    public val logLevel: VfxLogLevel?,
    /** Show the consent form as if the device were in the EEA (testing only). */
    public val consentDebugEea: Boolean,
    /** Hashed device IDs for the consent debug settings (Logcat prints yours). */
    public val consentTestDeviceHashedIds: List<String>,
    /** The platform's address; only for testing against another server. */
    public val baseUrl: String,
    /** Start loading as soon as the settings arrive (true), or only when asked. */
    public val autoPreload: Boolean,
    /**
     * Settings given by the app instead of fetched (PROTOCOL.md section 1), for testing only: a demo
     * build, or a test with no platform. Null in a real app: the platform decides.
     */
    public val settingsForTesting: String? = null,
) {
    public class Builder {
        private var testDeviceIds: List<String> = emptyList()
        private var logLevel: VfxLogLevel? = null
        private var consentDebugEea = false
        private var consentHashed: List<String> = emptyList()
        private var baseUrl = DEFAULT_BASE_URL
        private var autoPreload = true
        private var testing: String? = null
        public fun testDeviceIds(ids: List<String>): Builder = apply { testDeviceIds = ids }
        public fun logLevel(level: VfxLogLevel): Builder = apply { logLevel = level }
        public fun consentDebugEea(on: Boolean, hashedDeviceIds: List<String> = emptyList()): Builder = apply { consentDebugEea = on; consentHashed = hashedDeviceIds }
        public fun baseUrl(url: String): Builder = apply { baseUrl = url.trimEnd('/') }
        public fun autoPreload(on: Boolean): Builder = apply { autoPreload = on }
        /** Testing only: these settings (the protocol's JSON) instead of the platform's. Nothing is fetched. */
        public fun settingsForTesting(json: String): Builder = apply { testing = json }
        /**
         * Testing only: every format on Google's own demo ad units, which always answer with a test ad.
         * Proves the SDK and Google Mobile Ads work in the app, with no account and no platform. Nothing
         * is sent anywhere. Never in a release: a real app's settings come from VisionForgeX.
         */
        public fun googleDemoAds(): Builder = apply { testing = GOOGLE_DEMO_SETTINGS }
        public fun build(): VfxOptions = VfxOptions(testDeviceIds, logLevel, consentDebugEea, consentHashed, baseUrl, autoPreload, testing)
    }

    public companion object {
        /** Google's demo ad units for Ad Manager (they always fill with test ads), as settings. */
        public val GOOGLE_DEMO_SETTINGS: String = """{"v":1,"ttl":86400,"app":{"enabled":true,"test":true,"reason":""},
 "global":{"fullGapSec":10,"sessionTimeoutSec":1800,"log":"debug"},
 "consent":{"ump":false,"child":false,"underAge":false,"rating":""},
 "events":{"on":false,"url":"","sample":1.0,"batch":20,"flushSec":30},
 "kv":{},"ab":"",
 "placements":{
  "banner":{"format":"banner","on":true,"unit":"/21775744923/example/adaptive-banner","preload":false,"expiryMin":55,"refreshSec":30,"collapsible":""},
  "collapsible_banner":{"format":"banner","on":true,"unit":"/21775744923/example/adaptive-banner","preload":false,"expiryMin":55,"refreshSec":0,"collapsible":"bottom"},
  "interstitial":{"format":"interstitial","on":true,"unit":"/21775744923/example/interstitial","preload":true,"expiryMin":55,"cap":{"minIntervalSec":15}},
  "rewarded":{"format":"rewarded","on":true,"unit":"/21775744923/example/rewarded","preload":true,"expiryMin":55},
  "rewarded_interstitial":{"format":"rewarded_interstitial","on":true,"unit":"/21775744923/example/rewarded-interstitial","preload":true,"expiryMin":55,"cap":{"minIntervalSec":15}},
  "app_open":{"format":"app_open","on":true,"unit":"/21775744923/example/app-open","preload":true,"expiryMin":235,"autoShow":true,"cap":{"minIntervalSec":60}},
  "native":{"format":"native","on":true,"unit":"/21775744923/example/native","preload":true,"expiryMin":55,"template":"medium"},
  "native_small":{"format":"native","on":true,"unit":"/21775744923/example/native","preload":false,"expiryMin":55,"template":"small"}
 }}"""
        public const val DEFAULT_BASE_URL: String = "https://apps.visionforgex.com"
        @JvmStatic public fun defaults(): VfxOptions = Builder().build()
    }
}

/** Called once the SDK has its settings (or could not get any). */
public fun interface VfxInitListener {
    public fun onReady(status: VfxStatus)
}

public class VfxStatus internal constructor(
    /** Ads may be shown: settings received, the app enabled, consent allows requests. */
    public val ready: Boolean,
    /** Why not, when [ready] is false. */
    public val reason: String,
    /** Settings came from the cache (the platform was not reached). */
    public val fromCache: Boolean,
)

/** What happened to one show() call. Every method is optional. */
public interface VfxShowListener {
    /** The ad is on screen. */
    public fun onShown() {}
    public fun onClicked() {}
    /** The ad was closed: carry on with the app. */
    public fun onDismissed() {}
    /**
     * No ad was shown: carry on with the app. [reason]: "not_ready", "paused", "consent",
     * "interval", "session", "hour", "day", "grace", "nth", "gap", "showing", "failed", "no_activity", "unknown_placement".
     */
    public fun onNotShown(reason: String) {}
    /** Rewarded formats: the user earned the reward. */
    public fun onReward(type: String, amount: Int) {}
}

/** Kotlin: `show(activity) { type, amount -> }` for rewarded formats. */
public fun interface VfxRewardListener {
    public fun onReward(type: String, amount: Int)
}

/**
 * Every ad event as it happens (for the app's own analytics or a debug screen): [type] is one of the
 * protocol's ("req", "fill", "nofill", "show", "click", "close", "fail", "reward", "paid", "capped").
 */
public fun interface VfxEventListener {
    public fun onEvent(event: VfxEvent)
}

public class VfxEvent internal constructor(
    public val type: String,
    public val placement: String,
    public val format: VfxFormat,
    /** The rest: tier, ms, err, why, src, and for "paid" v (micros), cur, prec. */
    public val details: Map<String, Any?>,
    public val time: Long,
) {
    override fun toString(): String = "$type · $placement" + if (details.isEmpty()) "" else " · " + details.entries.joinToString(" ") { "${it.key}=${it.value}" }
}

/** What one impression earned, as Google reported it (its paid event): for the app's own analytics (Firebase, AppsFlyer, Adjust…). */
public fun interface VfxRevenueListener {
    public fun onRevenue(placement: String, format: VfxFormat, valueMicros: Long, currency: String, precision: String, source: String?)
}

/** The answer of the privacy options form. */
public fun interface VfxConsentListener {
    public fun onDone(error: String?)
}

internal const val TAG: String = "VisionForgeX"
