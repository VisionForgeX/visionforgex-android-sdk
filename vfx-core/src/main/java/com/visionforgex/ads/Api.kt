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
) {
    public class Builder {
        private var testDeviceIds: List<String> = emptyList()
        private var logLevel: VfxLogLevel? = null
        private var consentDebugEea = false
        private var consentHashed: List<String> = emptyList()
        private var baseUrl = DEFAULT_BASE_URL
        private var autoPreload = true
        public fun testDeviceIds(ids: List<String>): Builder = apply { testDeviceIds = ids }
        public fun logLevel(level: VfxLogLevel): Builder = apply { logLevel = level }
        public fun consentDebugEea(on: Boolean, hashedDeviceIds: List<String> = emptyList()): Builder = apply { consentDebugEea = on; consentHashed = hashedDeviceIds }
        public fun baseUrl(url: String): Builder = apply { baseUrl = url.trimEnd('/') }
        public fun autoPreload(on: Boolean): Builder = apply { autoPreload = on }
        public fun build(): VfxOptions = VfxOptions(testDeviceIds, logLevel, consentDebugEea, consentHashed, baseUrl, autoPreload)
    }

    public companion object {
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

/** The answer of the privacy options form. */
public fun interface VfxConsentListener {
    public fun onDone(error: String?)
}

internal const val TAG: String = "VisionForgeX"
