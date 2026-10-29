package com.visionforgex.ads.gma

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
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
 * The engine on Google Mobile Ads 25.x (Ad Manager requests), with Google's consent SDK. Found by
 * name when the SDK starts; apps never touch it. When Google's Next-Gen SDK becomes the one to use,
 * a second engine replaces this one and apps change nothing but the dependency.
 */
public class GmaEngine : AdEngine {
    override val name: String = "gma-25"
    override val consent: ConsentEngine = UmpConsent()

    override fun initialize(context: Context, settings: EngineSettings, done: () -> Unit) {
        apply(settings)
        // Google: initialize off the main thread
        Executors.newSingleThreadExecutor().execute { MobileAds.initialize(context) { done() } }
    }

    override fun apply(settings: EngineSettings) {
        // Google Mobile Ads 25 replaced the two child tags with one setting: CHILD for an app directed
        // at children (COPPA), TEEN for users under the age of consent (GDPR)
        val age = when { settings.childDirected -> AgeRestrictedTreatment.CHILD; settings.underAge -> AgeRestrictedTreatment.TEEN; else -> AgeRestrictedTreatment.UNSPECIFIED }
        val b = RequestConfiguration.Builder()
            .setAgeRestrictedTreatment(age)
            .setTestDeviceIds(settings.testDeviceIds)
        when (settings.maxRating) {
            "G" -> b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            "PG" -> b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
            "T" -> b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
            "MA" -> b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA)
        }
        MobileAds.setRequestConfiguration(b.build())
    }

    override fun setMuted(muted: Boolean) { MobileAds.setAppMuted(muted) }
    override fun openInspector(context: Context) { MobileAds.openAdInspector(context) { } }

    internal fun request(r: EngineRequest): AdManagerAdRequest {
        val b = AdManagerAdRequest.Builder()
        for ((k, v) in r.keyValues) b.addCustomTargeting(k, v)
        if (r.collapsible.isNotEmpty()) b.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("collapsible", r.collapsible) })
        b.setRequestAgent("VisionForgeX")
        return b.build()
    }

    override fun loadFullScreen(context: Context, format: VfxFormat, unit: String, request: EngineRequest, callback: FullScreenLoadCallback) {
        val req = request(request)
        try {
            when (format) {
                VfxFormat.INTERSTITIAL -> AdManagerInterstitialAd.load(context, unit, req, object : AdManagerInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: AdManagerInterstitialAd) = callback.onLoaded(Full(ad.responseInfo, { a, l -> ad.fullScreenContentCallback = l.content(); ad.onPaidEventListener = l.paid(); ad.show(a) }))
                    override fun onAdFailedToLoad(e: LoadAdError) = callback.onFailed(e.code, e.message)
                })
                VfxFormat.REWARDED -> RewardedAd.load(context, unit, req, object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) = callback.onLoaded(Full(ad.responseInfo, { a, l -> ad.fullScreenContentCallback = l.content(); ad.onPaidEventListener = l.paid(); ad.show(a) { item -> l.listener.onReward(item.type, item.amount) } }))
                    override fun onAdFailedToLoad(e: LoadAdError) = callback.onFailed(e.code, e.message)
                })
                VfxFormat.REWARDED_INTERSTITIAL -> RewardedInterstitialAd.load(context, unit, req, object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) = callback.onLoaded(Full(ad.responseInfo, { a, l -> ad.fullScreenContentCallback = l.content(); ad.onPaidEventListener = l.paid(); ad.show(a) { item -> l.listener.onReward(item.type, item.amount) } }))
                    override fun onAdFailedToLoad(e: LoadAdError) = callback.onFailed(e.code, e.message)
                })
                VfxFormat.APP_OPEN -> AppOpenAd.load(context, unit, req, object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) = callback.onLoaded(Full(ad.responseInfo, { a, l -> ad.fullScreenContentCallback = l.content(); ad.onPaidEventListener = l.paid(); ad.show(a) }))
                    override fun onAdFailedToLoad(e: LoadAdError) = callback.onFailed(e.code, e.message)
                })
                else -> callback.onFailed(-1, "not a full-screen format: ${format.wire}")
            }
        } catch (t: Throwable) {
            callback.onFailed(-2, "Google Mobile Ads refused the request: ${t.message}")
        }
    }

    override fun newBanner(context: Context, unit: String, widthDp: Int, listener: BannerListener): EngineBanner = Banner(context, unit, widthDp, listener, this)

    override fun loadNative(context: Context, unit: String, request: EngineRequest, callback: NativeLoadCallback) {
        var loaded: Native? = null
        try {
            val loader = AdLoader.Builder(context, unit)
                .forNativeAd { ad -> loaded = Native(ad).also(callback::onLoaded) }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(e: LoadAdError) { if (loaded == null) callback.onFailed(e.code, e.message) }
                    // a native ad's impressions and clicks come here, not through the view
                    override fun onAdImpression() { loaded?.listener?.onImpression() }
                    override fun onAdClicked() { loaded?.listener?.onClicked() }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE).build())
                .build()
            loader.loadAd(request(request))
        } catch (t: Throwable) { callback.onFailed(-2, "Google Mobile Ads refused the request: ${t.message}") }
    }
}

internal fun sourceOf(r: ResponseInfo?): String? = r?.loadedAdapterResponseInfo?.adSourceName?.takeIf { it.isNotBlank() }

internal fun precisionOf(v: AdValue): String = when (v.precisionType) {
    AdValue.PrecisionType.PRECISE -> "PRECISE"
    AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
    AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER"
    else -> "UNKNOWN"
}

/** The callbacks one show needs, from the SDK's listener. */
internal class Callbacks(val listener: FullScreenListener) {
    fun content() = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = listener.onShown()
        override fun onAdImpression() = listener.onImpression()
        override fun onAdClicked() = listener.onClicked()
        override fun onAdDismissedFullScreenContent() = listener.onDismissed()
        override fun onAdFailedToShowFullScreenContent(e: AdError) = listener.onFailedToShow(e.code, e.message)
    }
    fun paid() = com.google.android.gms.ads.OnPaidEventListener { v -> listener.onPaid(v.valueMicros, v.currencyCode, precisionOf(v)) }
}

internal class Full(private val info: ResponseInfo?, private val doShow: (Activity, Callbacks) -> Unit) : EngineFullScreenAd {
    override val source: String? get() = sourceOf(info)
    override fun show(activity: Activity, listener: FullScreenListener) {
        try { doShow(activity, Callbacks(listener)) } catch (t: Throwable) { listener.onFailedToShow(-2, t.message ?: "could not show") }
    }
}

/**
 * A banner, asked for in sizes from the largest down, inside one request where it can: Google's large
 * anchored adaptive size, the standard anchored adaptive one and 320x50 together; when that finds no
 * buyer (or the unit takes none of them), the standard adaptive size alone, then 320x50 alone. The size
 * that filled is kept for the banner's next refreshes. Each try is a new AdManagerAdView in one
 * container, since a view's sizes are best set once.
 */
internal class Banner(private val context: Context, private val unit: String, private val widthDp: Int, private val l: BannerListener, private val engine: GmaEngine) : EngineBanner {
    private val box = android.widget.FrameLayout(context)
    private var v: AdManagerAdView? = null
    private var tier = 0          // where this load is in the ladder
    private var good = -1         // the tier that filled last, for the next refreshes
    private var request: EngineRequest? = null
    private val tried = StringBuilder()
    override val detail: String get() = tried.toString()
    private fun note(result: String) { if (tried.isNotEmpty()) tried.append(" → "); tried.append(sizesFor(tier).joinToString("+") { "${it.width}x${it.height}" }).append('=').append(result) }

    internal fun sizesFor(t: Int): Array<AdSize> = BannerSizes.tier(t, AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp), AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp), AdSize.BANNER).toTypedArray()

    private fun newView(t: Int): AdManagerAdView {
        v?.destroy(); box.removeAllViews()
        return AdManagerAdView(context).apply {
            adUnitId = unit
            setAdSizes(*sizesFor(t))
            adListener = object : AdListener() {
                override fun onAdLoaded() { good = tier; note("filled " + (adSize?.let { "${it.width}x${it.height}" } ?: "")); l.onLoaded(sourceOf(responseInfo)) }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    note(e.code.toString())
                    val next = BannerSizes.next(tier, e.code)
                    if (next != null) { tier = next; v = newView(tier); request?.let { v!!.loadAd(engine.request(it)) }; return }   // a smaller size, at once, in the same load
                    good = -1
                    l.onFailed(e.code, e.message)
                }
                override fun onAdImpression() = l.onImpression()
                override fun onAdClicked() = l.onClicked()
            }
            onPaidEventListener = com.google.android.gms.ads.OnPaidEventListener { val x = it; l.onPaid(x.valueMicros, x.currencyCode, precisionOf(x)) }
            box.addView(this, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
        }
    }

    override val view: View get() = box
    override fun load(request: EngineRequest) {
        this.request = request
        tried.setLength(0)
        val start = if (good >= 0) good else 0
        if (v == null || tier != start) { tier = start; v = newView(tier) }
        v!!.loadAd(engine.request(request))
    }
    override fun pause() { v?.pause() }
    override fun resume() { v?.resume() }
    override fun destroy() { v?.destroy(); v = null; box.removeAllViews() }
}

/** The banner size ladder, apart so it can be tested without Google. */
internal object BannerSizes {
    const val LAST = 2
    /** 0: large, standard and 320x50 together; 1: standard alone; 2: 320x50 alone. */
    fun <T> tier(t: Int, large: T, standard: T, small: T): List<T> = when (t) {
        0 -> listOf(large, standard, small)
        1 -> listOf(standard)
        else -> listOf(small)
    }
    /** The next tier to try after a failure, or null to report it: only "no fill" (3) and "invalid request" (1) step down. */
    fun next(t: Int, code: Int): Int? = if ((code == 3 || code == 1) && t < LAST) t + 1 else null
}

internal class Native(private val ad: NativeAd) : EngineNativeAd {
    var listener: NativeListener? = null
    override val source: String? get() = sourceOf(ad.responseInfo)
    override fun render(context: Context, template: String, listener: NativeListener): View {
        this.listener = listener
        ad.setOnPaidEventListener { v -> listener.onPaid(v.valueMicros, v.currencyCode, precisionOf(v)) }
        return NativeTemplates.build(context, ad, template, listener)
    }
    override fun destroy() = ad.destroy()
}
