package com.visionforgex.ads.engine

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.visionforgex.ads.VfxFormat

/**
 * What the SDK needs from an ad engine: the one that talks to Google Mobile Ads lives in vfx-ads.
 * The SDK decides what to request and when; the engine only requests, shows and reports.
 * Apps never use these types: they are public only so an engine in another module can implement them.
 * Every callback may arrive on any thread; the SDK moves to the main thread itself.
 */
public interface AdEngine {
    /** A short name for the log and events, e.g. "gma-25". */
    public val name: String

    /** Starts the engine once, with the tags it must apply to every request. */
    public fun initialize(context: Context, settings: EngineSettings, done: () -> Unit)

    /** New tags (consent changes, new settings). */
    public fun apply(settings: EngineSettings)

    public fun loadFullScreen(context: Context, format: VfxFormat, unit: String, request: EngineRequest, callback: FullScreenLoadCallback)

    public fun newBanner(context: Context, unit: String, widthDp: Int, listener: BannerListener): EngineBanner

    public fun loadNative(context: Context, unit: String, request: EngineRequest, callback: NativeLoadCallback)

    /** Google's consent flow, or null when the engine has none. */
    public val consent: ConsentEngine?

    public fun setMuted(muted: Boolean) {}

    /** Google's own ad inspector, for debugging a live build. */
    public fun openInspector(context: Context) {}
}

public class EngineSettings(
    public val childDirected: Boolean,
    public val underAge: Boolean,
    public val maxRating: String,
    public val testDeviceIds: List<String>,
)

/** One request: the key-values, and banner-only extras. */
public class EngineRequest(
    public val keyValues: Map<String, String>,
    public val collapsible: String = "",
)

public interface FullScreenLoadCallback {
    public fun onLoaded(ad: EngineFullScreenAd)
    public fun onFailed(code: Int, message: String)
}

/** A loaded full-screen ad. */
public interface EngineFullScreenAd {
    /** Which network filled it (Google's ad source name), when known. */
    public val source: String?
    public fun show(activity: Activity, listener: FullScreenListener)
    public fun destroy() {}
}

public interface FullScreenListener {
    public fun onShown()
    public fun onImpression() {}
    public fun onClicked()
    public fun onDismissed()
    public fun onFailedToShow(code: Int, message: String)
    public fun onReward(type: String, amount: Int) {}
    public fun onPaid(valueMicros: Long, currency: String, precision: String)
}

public interface EngineBanner {
    public val view: View
    /** For the logs: the sizes the last load asked for and how each answered, e.g. "411x64+411x60+320x50=3 → 320x50=filled". */
    public val detail: String get() = ""
    public fun load(request: EngineRequest)
    public fun pause()
    public fun resume()
    public fun destroy()
}

public interface BannerListener {
    public fun onLoaded(source: String?)
    public fun onFailed(code: Int, message: String)
    public fun onImpression()
    public fun onClicked()
    public fun onPaid(valueMicros: Long, currency: String, precision: String)
}

public interface NativeLoadCallback {
    public fun onLoaded(ad: EngineNativeAd)
    public fun onFailed(code: Int, message: String)
}

/** A loaded native ad, drawn into one of the SDK's layouts ("small" or "medium") by the engine. */
public interface EngineNativeAd {
    public val source: String?
    public fun render(context: Context, template: String, listener: NativeListener): View
    public fun destroy()
}

public interface NativeListener {
    public fun onImpression()
    public fun onClicked()
    public fun onPaid(valueMicros: Long, currency: String, precision: String)
}

public interface ConsentEngine {
    /** Asks for consent where the law needs it (shows Google's form when required), then answers. */
    public fun gather(activity: Activity, underAge: Boolean, debug: ConsentDebug?, done: (canRequestAds: Boolean, error: String?) -> Unit)
    public fun canRequestAds(context: Context): Boolean
    public fun privacyOptionsRequired(context: Context): Boolean
    public fun showPrivacyOptions(activity: Activity, done: (error: String?) -> Unit)
    public fun reset(context: Context)
}

/** For testing the consent form outside the EEA: Google's debug geography and test device. */
public class ConsentDebug(public val geography: String, public val testDeviceHashedIds: List<String>)

/** For engines: the parent of a view the SDK hands back, cleared. */
public fun View.detachFromParent() { (parent as? ViewGroup)?.removeView(this) }
