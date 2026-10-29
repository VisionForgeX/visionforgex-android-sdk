package com.visionforgex.sample

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.BaseAdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView

/**
 * The banner lab: the same banner asked for several ways side by side, straight through Google's SDK
 * (not through VisionForgeX), from Google's own sample code to the request VisionForgeX makes. Which
 * ones fill and which say "No fill" shows what a banner request needs. For testing only.
 */
@android.annotation.SuppressLint("SetTextI18n")
class BannerLab(private val a: Activity, private val log: (String, Boolean) -> Unit) {
    private class Try(val id: String, val what: String, val make: (Int) -> BaseAdView)

    private val adaptive = "/21775744923/example/adaptive-banner"
    private val fixed = "/21775744923/example/fixed-size-banner"
    private fun plain() = AdManagerAdRequest.Builder().build()
    private fun gam(unit: String, size: AdSize, req: () -> AdManagerAdRequest = ::plain): (Int) -> BaseAdView = { _ ->
        AdManagerAdView(a).apply { adUnitId = unit; setAdSizes(size); tag = req }
    }
    private fun anchored(w: Int) = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, w)

    private val tries = listOf(
        Try("A", "Google's sample: adaptive unit, anchored adaptive size, plain request") { w -> gam(adaptive, anchored(w))(w) },
        Try("B", "Adaptive unit, large anchored adaptive size, plain request") { w -> gam(adaptive, AdSize.getLargeAnchoredAdaptiveBannerAdSize(a, w))(w) },
        Try("C", "Adaptive unit, 320x50, plain request") { w -> gam(adaptive, AdSize.BANNER)(w) },
        Try("D", "Fixed-size unit, 320x50, plain request") { w -> gam(fixed, AdSize.BANNER)(w) },
        Try("E", "A + VisionForgeX's key-value (vfx_pl=banner)") { w -> gam(adaptive, anchored(w)) { AdManagerAdRequest.Builder().addCustomTargeting("vfx_pl", "banner").build() }(w) },
        Try("F", "A + VisionForgeX's request agent") { w -> gam(adaptive, anchored(w)) { AdManagerAdRequest.Builder().setRequestAgent("VisionForgeX").build() }(w) },
        Try("G", "A asked with all three sizes, as VisionForgeX does") { w -> AdManagerAdView(a).apply { adUnitId = adaptive; setAdSizes(AdSize.getLargeAnchoredAdaptiveBannerAdSize(a, w), anchored(w), AdSize.BANNER); tag = { plain() } } },
        Try("H", "AdMob's own test banner (not Ad Manager)") { w -> AdView(a).apply { adUnitId = "ca-app-pub-3940256099942544/9214589741"; setAdSize(anchored(w)); tag = { AdRequest.Builder().build() } } },
    )

    private val views = ArrayList<BaseAdView>()

    fun card(card: LinearLayout, text: (String, Float, Int) -> TextView, dp: (Int) -> Int, muted: Int, green: Int, amber: Int, run: (() -> Unit) -> View): LinearLayout {
        card.addView(text("Each line asks Google for a banner a different way, straight through Google's SDK. A line that fills shows its ad under it.", 12.5f, muted))
        val rows = ArrayList<Pair<TextView, FrameLayout>>()
        for (t in tries) {
            val label = text("${t.id}  ${t.what}", 13f, Color.parseColor("#111827")).apply { setPadding(0, dp(10), 0, dp(2)) }
            val state = text("not asked yet", 12.5f, muted)
            val box = FrameLayout(a)
            card.addView(label); card.addView(state); card.addView(box, LinearLayout.LayoutParams(-1, -2))
            rows += state to box
        }
        fun go() {
            views.forEach { it.destroy() }; views.clear()
            val wDp = ((a.resources.displayMetrics.widthPixels / a.resources.displayMetrics.density).toInt() - 64).coerceAtLeast(320)
            tries.forEachIndexed { i, t ->
                val (state, box) = rows[i]
                box.removeAllViews()
                val started = System.currentTimeMillis()
                val v = try { t.make(wDp) } catch (e: Throwable) { state.text = "could not be made: ${e.message}"; state.setTextColor(amber); return@forEachIndexed }
                views += v
                state.text = "asking…"; state.setTextColor(muted)
                v.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        val s = v.adSize?.let { "${it.width}x${it.height}" } ?: ""
                        state.text = "FILLED $s in ${System.currentTimeMillis() - started} ms"; state.setTextColor(green)
                        log("lab ${t.id} · filled $s", true)
                    }
                    override fun onAdFailedToLoad(e: LoadAdError) {
                        state.text = "no ad: code ${e.code} · ${e.message} · ${e.domain}"; state.setTextColor(amber)
                        log("lab ${t.id} · code ${e.code} ${e.message}", false)
                    }
                }
                box.addView(v, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is AdManagerAdView -> v.loadAd((v.tag as () -> AdManagerAdRequest)())
                    is AdView -> v.loadAd((v.tag as () -> AdRequest)())
                }
            }
        }
        card.addView(run { go() }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(12) })
        return card
    }

    fun pause() = views.forEach { it.pause() }
    fun resume() = views.forEach { it.resume() }
    fun destroy() { views.forEach { it.destroy() }; views.clear() }
}
