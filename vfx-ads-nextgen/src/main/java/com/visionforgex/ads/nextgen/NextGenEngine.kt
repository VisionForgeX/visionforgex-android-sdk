package com.visionforgex.ads.nextgen

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.BaseRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.visionforgex.ads.VfxFormat
import com.visionforgex.ads.engine.AdEngine
import com.visionforgex.ads.engine.BannerListener
import com.visionforgex.ads.engine.ConsentEngine
import com.visionforgex.ads.engine.EngineBanner
import com.visionforgex.ads.engine.EngineFullScreenAd
import com.visionforgex.ads.engine.EngineNativeAd
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.engine.EngineSettings
import com.visionforgex.ads.engine.FullScreenListener
import com.visionforgex.ads.engine.FullScreenLoadCallback
import com.visionforgex.ads.engine.NativeListener
import com.visionforgex.ads.engine.NativeLoadCallback
import java.util.concurrent.Executors

/**
 * The engine on Google's GMA Next-Gen SDK (Ad Manager requests), with Google's consent SDK. Google made Next-Gen its
 * preferred Android SDK in July 2026; the 25.x line is deprecated on 30 Jun 2027 and sunset on 30 Jun 2028. An app
 * chooses it by depending on vfx-ads-nextgen instead of vfx-ads; nothing else in the app changes.
 *
 * The differences from the 25.x engine that matter here: the app ID is passed at start (read from the same
 * manifest entry Google's consent SDK needs), the ad unit travels in the request, and Google calls back on a
 * background thread (the SDK moves its own work to the main thread; this engine does the same for its views).
 */
public class NextGenEngine : AdEngine {
    override val name: String = "ngsdk-" + NEXTGEN_VERSION
    override val consent: ConsentEngine = NgConsent()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var config: RequestConfiguration? = null
    @Volatile private var started = false

    override fun initialize(context: Context, settings: EngineSettings, done: () -> Unit) {
        val app = context.applicationContext
        config = configuration(settings)
        val id = appId(app)
        // Google: initialize off the main thread (it reads its storage and starts its web engine)
        Executors.newSingleThreadExecutor().execute {
            try {
                if (id.isEmpty()) throw IllegalStateException("no com.google.android.gms.ads.APPLICATION_ID in the app's manifest")
                MobileAds.initialize(app, InitializationConfig.Builder(id).setRequestConfiguration(config!!).build()) { started = true; done() }
            } catch (t: Throwable) {
                android.util.Log.w("VisionForgeX", "Google's Next-Gen SDK did not start: ${t.message}")
                done()
            }
        }
    }

    override fun apply(settings: EngineSettings) {
        val c = configuration(settings)
        config = c
        if (started) try { MobileAds.setRequestConfiguration(c) } catch (_: Throwable) {}
    }

    /** Google's request settings from ours: the age treatment (CHILD for COPPA, TEEN for the GDPR's age of consent), test devices, rating. */
    internal fun configuration(s: EngineSettings): RequestConfiguration {
        val age = when { s.childDirected -> AgeRestrictedTreatment.CHILD; s.underAge -> AgeRestrictedTreatment.TEEN; else -> AgeRestrictedTreatment.UNSPECIFIED }
        val b = RequestConfiguration.Builder().setAgeRestrictedTreatment(age).setTestDeviceIds(s.testDeviceIds)
        when (s.maxRating) {
            "G" -> b.setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G)
            "PG" -> b.setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG)
            "T" -> b.setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T)
            "MA" -> b.setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA)
        }
        return b.build()
    }

    override fun setMuted(muted: Boolean) { try { MobileAds.setUserMutedApp(muted) } catch (_: Throwable) {} }
    override fun openInspector(context: Context) { try { MobileAds.openAdInspector { } } catch (_: Throwable) {} }

    override fun loadFullScreen(context: Context, format: VfxFormat, unit: String, request: EngineRequest, callback: FullScreenLoadCallback) {
        val req = AdRequest.Builder(unit).vfx(request).build()
        try {
            when (format) {
                VfxFormat.INTERSTITIAL -> InterstitialAd.load(req, object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) = callback.onLoaded(Full(ad.getResponseInfo(), ad::destroy) { a, l ->
                        ad.adEventCallback = l
                        ad.show(a)
                    })
                    override fun onAdFailedToLoad(adError: LoadAdError) = callback.onFailed(codeOf(adError), adError.message)
                })
                VfxFormat.REWARDED -> RewardedAd.load(req, object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) = callback.onLoaded(Full(ad.getResponseInfo(), ad::destroy) { a, l ->
                        ad.adEventCallback = l
                        ad.show(a) { item -> l.listener.onReward(item.type, item.amount) }
                    })
                    override fun onAdFailedToLoad(adError: LoadAdError) = callback.onFailed(codeOf(adError), adError.message)
                })
                VfxFormat.REWARDED_INTERSTITIAL -> RewardedInterstitialAd.load(req, object : AdLoadCallback<RewardedInterstitialAd> {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) = callback.onLoaded(Full(ad.getResponseInfo(), ad::destroy) { a, l ->
                        ad.adEventCallback = l
                        ad.show(a) { item -> l.listener.onReward(item.type, item.amount) }
                    })
                    override fun onAdFailedToLoad(adError: LoadAdError) = callback.onFailed(codeOf(adError), adError.message)
                })
                VfxFormat.APP_OPEN -> AppOpenAd.load(req, object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) = callback.onLoaded(Full(ad.getResponseInfo(), ad::destroy) { a, l ->
                        ad.adEventCallback = l
                        ad.show(a)
                    })
                    override fun onAdFailedToLoad(adError: LoadAdError) = callback.onFailed(codeOf(adError), adError.message)
                })
                else -> callback.onFailed(-1, "not a full-screen format: ${format.wire}")
            }
        } catch (t: Throwable) {
            callback.onFailed(-2, "Google's Next-Gen SDK refused the request: ${t.message}")
        }
    }

    override fun newBanner(context: Context, unit: String, widthDp: Int, listener: BannerListener): EngineBanner = NgBanner(context, unit, widthDp, listener, main)

    override fun loadNative(context: Context, unit: String, request: EngineRequest, callback: NativeLoadCallback) {
        val req = NativeAdRequest.Builder(unit, listOf(NativeAd.NativeAdType.NATIVE))
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
            .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
            .vfx(request).build()
        try {
            NativeAdLoader.load(req, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) = callback.onLoaded(NgNative(nativeAd))
                override fun onAdFailedToLoad(adError: LoadAdError) = callback.onFailed(codeOf(adError), adError.message)
            })
        } catch (t: Throwable) { callback.onFailed(-2, "Google's Next-Gen SDK refused the request: ${t.message}") }
    }

    internal companion object {
        /** The Google SDK this engine was built and tested with (its name in events: "ngsdk-1.6.0"). */
        const val NEXTGEN_VERSION: String = "1.6.0"

        /** The Ad Manager app ID from the manifest (the entry Google's consent SDK needs too), or "". */
        fun appId(c: Context): String = try {
            val ai = if (android.os.Build.VERSION.SDK_INT >= 33) c.packageManager.getApplicationInfo(c.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            else @Suppress("DEPRECATION") c.packageManager.getApplicationInfo(c.packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID").orEmpty()
        } catch (_: Throwable) { "" }
    }
}

/** Our tags on any of Google's request builders: the platform's key-values, a collapsible banner, our request agent. */
internal fun <T : BaseRequestBuilder<T>> T.vfx(r: EngineRequest): T {
    for ((k, v) in r.keyValues) putCustomTargeting(k, v)
    if (r.collapsible.isNotEmpty()) setGoogleExtrasBundle(Bundle().apply { putString("collapsible", r.collapsible) })
    setRequestAgent("VisionForgeX")
    return this
}

/**
 * Google's load error as the number the SDK's events and the banner ladder use, the same numbers as Google's 25.x
 * SDK (0 internal, 1 invalid request, 2 network, 3 no fill, 8 app ID missing, 9 mediation no fill, 10 request ID
 * mismatch, 11 invalid ad string), so a report means the same whichever engine sent it.
 */
internal fun codeOf(e: LoadAdError): Int = when (e.code) {
    LoadAdError.ErrorCode.INTERNAL_ERROR -> 0
    LoadAdError.ErrorCode.INVALID_REQUEST -> 1
    LoadAdError.ErrorCode.NETWORK_ERROR -> 2
    LoadAdError.ErrorCode.NO_FILL -> 3
    LoadAdError.ErrorCode.TIMEOUT -> 2
    LoadAdError.ErrorCode.APP_ID_MISSING -> 8
    LoadAdError.ErrorCode.REQUEST_ID_MISMATCH -> 10
    LoadAdError.ErrorCode.INVALID_AD_RESPONSE, LoadAdError.ErrorCode.AD_RESPONSE_ALREADY_USED -> 11
    else -> 0
}

internal fun sourceOf(r: ResponseInfo?): String? = r?.loadedAdSourceResponseInfo?.name?.takeIf { it.isNotBlank() }

internal fun precisionOf(v: AdValue): String = when (v.precisionType) {
    PrecisionType.PRECISE -> "PRECISE"
    PrecisionType.ESTIMATED -> "ESTIMATED"
    PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER"
    else -> "UNKNOWN"
}

/** Google's full-screen events (the same calls for every format in Next-Gen) as the SDK's listener calls. */
internal class ShowCallbacks(val listener: FullScreenListener) : InterstitialAdEventCallback, RewardedAdEventCallback, RewardedInterstitialAdEventCallback, AppOpenAdEventCallback {
    override fun onAdShowedFullScreenContent() = listener.onShown()
    override fun onAdImpression() = listener.onImpression()
    override fun onAdClicked() = listener.onClicked()
    override fun onAdDismissedFullScreenContent() = listener.onDismissed()
    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = listener.onFailedToShow(fullScreenContentError.code.value, fullScreenContentError.message)
    override fun onAdPaid(value: AdValue) = listener.onPaid(value.valueMicros, value.currencyCode, precisionOf(value))
}

internal class Full(private val info: ResponseInfo?, private val free: () -> Unit, private val doShow: (Activity, ShowCallbacks) -> Unit) : EngineFullScreenAd {
    override val source: String? get() = sourceOf(info)
    override fun show(activity: Activity, listener: FullScreenListener) {
        // Google: show on the main thread
        val run = { try { doShow(activity, ShowCallbacks(listener)) } catch (t: Throwable) { listener.onFailedToShow(-2, t.message ?: "could not show") } }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else Handler(Looper.getMainLooper()).post { run() }
    }
    override fun destroy() { try { free() } catch (_: Throwable) {} }
}

/**
 * A banner, asked for in sizes from the largest down as the 25.x engine does: Google's large anchored adaptive
 * size, the standard anchored adaptive one and 320x50 in one request; on "no fill" (or a unit that takes none of
 * them) the standard size alone, then 320x50 alone. The size that filled is kept for the next refreshes. Next-Gen
 * takes the sizes in the request, so one AdView serves every try. Google answers on a background thread: each
 * answer is handled on the main thread, in order.
 */
internal class NgBanner(private val context: Context, private val unit: String, private val widthDp: Int, private val l: BannerListener, private val main: Handler) : EngineBanner {
    private val ad = AdView(context)
    private var tier = 0
    private var good = -1
    private var request: EngineRequest? = null
    private var gen = 0                 // a newer load or destroy makes an older answer stale
    private var gone = false
    private val tried = StringBuilder()
    override val detail: String get() = tried.toString()
    override val view: View get() = ad
    /** How a request reaches Google (tests put a stand-in here). */
    internal var send: (BannerAdRequest, AdLoadCallback<BannerAd>) -> Unit = { r, cb -> ad.loadAd(r, cb) }

    internal fun sizesFor(t: Int): List<AdSize> = BannerSizes.tier(t, AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp), AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp), AdSize.BANNER)
    internal fun requestFor(t: Int, r: EngineRequest): BannerAdRequest = BannerAdRequest.Builder(unit, sizesFor(t)).vfx(r).build()
    private fun note(t: Int, result: String) { if (tried.isNotEmpty()) tried.append(" → "); tried.append(sizesFor(t).joinToString("+") { "${it.width}x${it.height}" }).append('=').append(result) }

    override fun load(request: EngineRequest) {
        this.request = request
        tried.setLength(0)
        tier = if (good >= 0) good else 0
        ask(++gen)
    }

    private fun ask(g: Int) {
        val r = request ?: return
        val t = tier
        try {
            send(requestFor(t, r), object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) { main.post { if (g == gen && !gone) loaded(t, ad) } }
                override fun onAdFailedToLoad(adError: LoadAdError) { val c = codeOf(adError); main.post { if (g == gen && !gone) failed(g, t, c, adError.message) } }
            })
        } catch (e: Throwable) { failed(g, t, -2, "Google's Next-Gen SDK refused the request: ${e.message}") }
    }

    internal fun loaded(t: Int, b: BannerAd) {
        good = t
        note(t, "filled " + b.getAdSize().let { "${it.width}x${it.height}" })
        b.adEventCallback = object : BannerAdEventCallback {
            override fun onAdImpression() = l.onImpression()
            override fun onAdClicked() = l.onClicked()
            override fun onAdPaid(value: AdValue) = l.onPaid(value.valueMicros, value.currencyCode, precisionOf(value))
        }
        l.onLoaded(sourceOf(b.getResponseInfo()))
    }

    internal fun failed(g: Int, t: Int, code: Int, message: String) {
        note(t, code.toString())
        val next = BannerSizes.next(t, code)
        if (next != null) { tier = next; ask(g); return }   // a smaller size, at once, in the same load
        good = -1
        l.onFailed(code, message)
    }

    // Next-Gen has no pause or resume on a banner: its own visibility tracking stops a hidden banner's refresh
    override fun pause() {}
    override fun resume() {}
    override fun destroy() { gone = true; gen++; try { ad.destroy() } catch (_: Throwable) {} }
}

/** The banner size ladder (the same as the 25.x engine's), apart so it can be tested without Google. */
internal object BannerSizes {
    const val LAST = 2
    /** 0: large, standard and 320x50 together; 1: standard alone; 2: 320x50 alone. */
    fun <T> tier(t: Int, large: T, standard: T, small: T): List<T> = when (t) {
        0 -> listOf(large, standard, small)
        1 -> listOf(standard)
        else -> listOf(small)
    }
    /** The next tier after a failure, or null to report it: only "no fill" (3) and "invalid request" (1) step down. */
    fun next(t: Int, code: Int): Int? = if ((code == 3 || code == 1) && t < LAST) t + 1 else null
}

internal class NgNative(private val ad: NativeAd) : EngineNativeAd {
    override val source: String? get() = sourceOf(ad.getResponseInfo())
    override fun render(context: Context, template: String, listener: NativeListener): View {
        ad.adEventCallback = object : NativeAdEventCallback {
            override fun onAdImpression() = listener.onImpression()
            override fun onAdClicked() = listener.onClicked()
            override fun onAdPaid(value: AdValue) = listener.onPaid(value.valueMicros, value.currencyCode, precisionOf(value))
        }
        return NgNativeTemplates.build(context, ad, template)
    }
    override fun destroy() { try { ad.destroy() } catch (_: Throwable) {} }
}
