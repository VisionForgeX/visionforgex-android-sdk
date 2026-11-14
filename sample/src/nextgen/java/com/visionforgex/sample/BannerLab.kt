package com.visionforgex.sample

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * The banner lab on Google's Next-Gen SDK: the same banner asked for several ways side by side, straight through
 * Google's SDK (not through VisionForgeX), from Google's own sample to the request VisionForgeX makes. Next-Gen
 * takes the ad unit and the sizes in the request, and answers on a background thread. For testing only.
 */
@android.annotation.SuppressLint("SetTextI18n")
class BannerLab(private val a: Activity, private val log: (String, Boolean) -> Unit) {
    private class Try(val id: String, val what: String, val request: (Int) -> BannerAdRequest)

    private val adaptive = "/21775744923/example/adaptive-banner"
    private val fixed = "/21775744923/example/fixed-size-banner"
    private fun anchored(w: Int) = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, w)
    private val main = Handler(Looper.getMainLooper())

    private val tries = listOf(
        Try("A", "Google's sample: adaptive unit, anchored adaptive size, plain request") { w -> BannerAdRequest.Builder(adaptive, anchored(w)).build() },
        Try("B", "Adaptive unit, large anchored adaptive size, plain request") { w -> BannerAdRequest.Builder(adaptive, AdSize.getLargeAnchoredAdaptiveBannerAdSize(a, w)).build() },
        Try("C", "Adaptive unit, 320x50, plain request") { _ -> BannerAdRequest.Builder(adaptive, AdSize.BANNER).build() },
        Try("D", "Fixed-size unit, 320x50, plain request") { _ -> BannerAdRequest.Builder(fixed, AdSize.BANNER).build() },
        Try("E", "A + VisionForgeX's key-value (vfx_pl=banner)") { w -> BannerAdRequest.Builder(adaptive, anchored(w)).putCustomTargeting("vfx_pl", "banner").build() },
        Try("F", "A + VisionForgeX's request agent") { w -> BannerAdRequest.Builder(adaptive, anchored(w)).setRequestAgent("VisionForgeX").build() },
        Try("G", "A asked with all three sizes, as VisionForgeX does") { w -> BannerAdRequest.Builder(adaptive, listOf(AdSize.getLargeAnchoredAdaptiveBannerAdSize(a, w), anchored(w), AdSize.BANNER)).build() },
        Try("H", "AdMob's own test banner (not Ad Manager)") { w -> BannerAdRequest.Builder("ca-app-pub-3940256099942544/9214589741", anchored(w)).build() },
    )

    private val views = ArrayList<AdView>()

    fun card(card: LinearLayout, text: (String, Float, Int) -> TextView, dp: (Int) -> Int, muted: Int, green: Int, amber: Int, run: (() -> Unit) -> View): LinearLayout {
        card.addView(text("Each line asks Google's Next-Gen SDK for a banner a different way. A line that fills shows its ad under it.", 12.5f, muted))
        val rows = ArrayList<Pair<TextView, FrameLayout>>()
        for (t in tries) {
            val label = text("${t.id}  ${t.what}", 13f, Color.parseColor("#111827")).apply { setPadding(0, dp(10), 0, dp(2)) }
            val state = text("not asked yet", 12.5f, muted)
            val box = FrameLayout(a)
            card.addView(label); card.addView(state); card.addView(box, LinearLayout.LayoutParams(-1, -2))
            rows += state to box
        }
        fun go() {
            destroy()
            val wDp = ((a.resources.displayMetrics.widthPixels / a.resources.displayMetrics.density).toInt() - 64).coerceAtLeast(320)
            tries.forEachIndexed { i, t ->
                val (state, box) = rows[i]
                box.removeAllViews()
                val started = System.currentTimeMillis()
                val v = AdView(a)
                views += v
                state.text = "asking…"; state.setTextColor(muted)
                box.addView(v, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                try {
                    v.loadAd(t.request(wDp), object : AdLoadCallback<BannerAd> {
                        override fun onAdLoaded(ad: BannerAd) = main.post {
                            val s = ad.getAdSize().let { "${it.width}x${it.height}" }
                            state.text = "FILLED $s in ${System.currentTimeMillis() - started} ms"; state.setTextColor(green)
                            log("lab ${t.id} · filled $s", true)
                        }.let { }
                        override fun onAdFailedToLoad(adError: LoadAdError) = main.post {
                            state.text = "no ad: ${adError.code} · ${adError.message}"; state.setTextColor(amber)
                            log("lab ${t.id} · ${adError.code} ${adError.message}", false)
                        }.let { }
                    })
                } catch (e: Throwable) { state.text = "could not be asked: ${e.message}"; state.setTextColor(amber) }
            }
        }
        card.addView(run { go() }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(12) })
        return card
    }

    // Next-Gen's banners pause and resume by themselves with the screen
    fun pause() {}
    fun resume() {}
    fun destroy() { views.forEach { it.destroy() }; views.clear() }
}
