package com.visionforgex.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.visionforgex.ads.debug.VfxDebugActivity
import com.visionforgex.ads.engine.AdEngine
import com.visionforgex.ads.internal.Sdk
import com.visionforgex.ads.internal.VLog

/**
 * The VisionForgeX SDK. Start it once, as early as you can (Application.onCreate):
 *
 * ```
 * VisionForgeX.initialize(this, "vfx_…your app key…")
 * ```
 *
 * Then show ads by placement name, as set on apps.visionforgex.com:
 *
 * ```
 * VisionForgeX.interstitial("level_complete").show(activity)
 * VisionForgeX.rewarded("extra_life").show(activity) { type, amount -> giveReward() }
 * ```
 *
 * Everything else — what each placement requests, how often, when it may show, its floors, consent,
 * experiments — is decided by the platform and arrives as the app's settings.
 */
public object VisionForgeX {
    /** This SDK's version. */
    @JvmField public val VERSION: String = BuildConfig.VFX_VERSION

    @Volatile internal var sdk: Sdk? = null
        private set
    private val main = Handler(Looper.getMainLooper())
    private val waiting = ArrayList<() -> Unit>()
    private var ready = false

    /**
     * Starts the SDK: its settings are fetched (and the last ones used meanwhile), consent asked where
     * the law needs it (Google's form, on the first screen), and the placements that preload begin
     * loading. Call it once; calling it again does nothing. Call on the main thread.
     */
    @JvmStatic @JvmOverloads
    public fun initialize(context: Context, appKey: String, options: VfxOptions = VfxOptions.defaults(), listener: VfxInitListener? = null) {
        if (sdk != null) { listener?.let { l -> whenSettled(l) }; return }
        val engine = findEngine()
        if (engine == null) {
            VLog.e("No ad engine: add com.github.VisionForgeX.visionforgex-android-sdk:vfx-ads to the app, not vfx-core alone.")
            listener?.onReady(VfxStatus(false, "no ad engine", false))
            return
        }
        val app = context.applicationContext as Application
        val s = Sdk(app, appKey.trim(), options, engine)
        sdk = s
        s.addListener { st ->
            if (st.ready) markReady()
            listener?.onReady(st)
        }
        s.start(context as? Activity)
    }

    /** True once the SDK has settings that allow ads and consent allows requests. */
    @JvmStatic public fun isReady(): Boolean = sdk?.canRequest() == true

    /** An interstitial placement. */
    @JvmStatic public fun interstitial(placement: String): VfxFullScreen = VfxFullScreen(placement, VfxFormat.INTERSTITIAL)
    /** A rewarded placement. */
    @JvmStatic public fun rewarded(placement: String): VfxFullScreen = VfxFullScreen(placement, VfxFormat.REWARDED)
    /** A rewarded interstitial placement. */
    @JvmStatic public fun rewardedInterstitial(placement: String): VfxFullScreen = VfxFullScreen(placement, VfxFormat.REWARDED_INTERSTITIAL)
    /** An app open placement (shown by itself when the platform turns that on; or call show()). */
    @JvmStatic public fun appOpen(placement: String): VfxFullScreen = VfxFullScreen(placement, VfxFormat.APP_OPEN)

    /** Loads every placement that preloads (only needed with VfxOptions.autoPreload(false)). */
    @JvmStatic public fun preloadAll() { sdk?.preloadAll() }

    /**
     * A key-value of the app's own added to every request (e.g. "level" → "12"), for targeting and
     * reports in Ad Manager. Keys: lowercase letters, digits and _, at most 20; "vfx_" is ours. Null removes it.
     */
    @JvmStatic public fun setKeyValue(key: String, value: String?) {
        if (!Regex("^[a-z0-9_]{1,20}$").matches(key) || key.startsWith("vfx_")) { VLog.w("Key-value \"$key\" not used: lowercase letters, digits and _, at most 20, not starting with vfx_."); return }
        sdk?.setAppKeyValue(key, value?.take(40))
    }

    /** Whether the app must offer a "Privacy options" button (users in the EEA, UK, Switzerland, some US states). */
    @JvmStatic public fun isPrivacyOptionsRequired(): Boolean { val s = sdk ?: return false; return s.engine.consent?.privacyOptionsRequired(s.app) == true }

    /** Opens Google's privacy options form, from the app's "Privacy options" button. */
    @JvmStatic @JvmOverloads public fun showPrivacyOptions(activity: Activity, listener: VfxConsentListener? = null) {
        val c = sdk?.engine?.consent ?: run { listener?.onDone("no consent engine"); return }
        c.showPrivacyOptions(activity) { err -> main.post { listener?.onDone(err) } }
    }

    /** Mutes video ads (the app's own sound setting). */
    @JvmStatic public fun setMuted(muted: Boolean) { sdk?.engine?.setMuted(muted) }

    /** The SDK's own screen: settings, placements, pacing, recent events, with load and show buttons. For debug builds. */
    @JvmStatic public fun openDebugPanel(context: Context) {
        context.startActivity(Intent(context, VfxDebugActivity::class.java).addFlags(if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Google's ad inspector, for checking a live build. */
    @JvmStatic public fun openAdInspector(context: Context) { sdk?.engine?.openInspector(context) }

    /** Every ad event as it happens (null stops it). Called on the main thread. */
    @JvmStatic public fun setEventListener(listener: VfxEventListener?) { eventListener = listener }
    @Volatile internal var eventListener: VfxEventListener? = null
    @Volatile internal var revenueListener: VfxRevenueListener? = null

    /** What each impression earned, for the app's own analytics (null stops it). Called on the main thread. */
    @JvmStatic public fun setRevenueListener(listener: VfxRevenueListener?) { revenueListener = listener }

    /** Where the settings came from: "platform", "cache", "testing", or "none". */
    @JvmStatic public fun settingsSource(): String = sdk?.configFrom ?: "none"

    /** Sends the events waiting now (they are sent by themselves too). */
    @JvmStatic public fun flush() { sdk?.flushNow() }

    // ---------------------------------------------------------------- inside

    internal fun whenReady(f: () -> Unit) { main.post { if (ready) f() else waiting.add(f) } }
    private fun markReady() { main.post { ready = true; val w = ArrayList(waiting); waiting.clear(); w.forEach { it() } } }
    private fun whenSettled(l: VfxInitListener) { sdk?.addListener(l) }

    private fun findEngine(): AdEngine? {
        for (n in ENGINES) try { return Class.forName(n).getDeclaredConstructor().newInstance() as AdEngine } catch (_: Throwable) {}
        return null
    }

    /** Tests only: forget everything. */
    internal fun resetForTests() { sdk = null; ready = false; waiting.clear() }
    internal fun startForTests(s: Sdk, current: Activity?) { sdk = s; s.addListener { if (it.ready) markReady() }; s.start(current) }

    private val ENGINES = listOf("com.visionforgex.ads.gma.GmaEngine")
}

/** One full-screen placement: `VisionForgeX.interstitial("level_complete")`. */
public class VfxFullScreen internal constructor(public val placement: String, public val format: VfxFormat) {
    /** An ad is loaded and fresh, so show() will show it (if the pacing allows). */
    public fun isReady(): Boolean = VisionForgeX.sdk?.slot(placement)?.isReady() == true

    /** Loads one if none is waiting (placements that preload do this by themselves). */
    public fun load() { VisionForgeX.sdk?.slot(placement)?.load(fresh = true) }

    /**
     * Shows it if one is ready and the pacing allows; otherwise [listener]'s onNotShown says why, and
     * the app carries on. Safe to call at every opportunity: the platform's pacing decides.
     */
    @JvmOverloads public fun show(activity: Activity, listener: VfxShowListener? = null) {
        val s = VisionForgeX.sdk ?: run { listener?.onNotShown("not_ready"); return }
        val p = s.configSnapshot()?.placements?.get(placement)
        if (p != null && p.format != format) VLog.w("\"$placement\" is a ${p.format.wire} placement on the platform, not ${format.wire}.")
        s.slot(placement).show(activity, listener)
    }

    /** Rewarded formats, Kotlin: `show(activity) { type, amount -> }`. */
    public fun show(activity: Activity, onReward: VfxRewardListener) {
        show(activity, object : VfxShowListener { override fun onReward(type: String, amount: Int) = onReward.onReward(type, amount) })
    }
}
