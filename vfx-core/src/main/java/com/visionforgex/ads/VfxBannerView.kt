package com.visionforgex.ads

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.visionforgex.ads.engine.BannerListener
import com.visionforgex.ads.engine.EngineBanner
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.internal.Placement
import com.visionforgex.ads.internal.Sdk
import com.visionforgex.ads.internal.VLog
import com.visionforgex.ads.internal.backoffMs

/**
 * A banner for one placement: adaptive to the width it is given (Google's large anchored adaptive
 * size), refreshed as the platform says only while at least half of it is on screen and the app is
 * in front, collapsible when the platform says so (first load only), retried after a growing wait.
 *
 * XML: `<com.visionforgex.ads.VfxBannerView app:vfxPlacement="home_banner" android:layout_width="match_parent" android:layout_height="wrap_content"/>`
 * Code: `VfxBannerView(context, "home_banner")`. It loads by itself once attached.
 */
public class VfxBannerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {
    public constructor(context: Context, placement: String) : this(context) { this.placement = placement }

    /** The placement's name, as on the platform. */
    public var placement: String = ""
        set(v) { field = v; if (isAttachedToWindow) start() }

    private var banner: EngineBanner? = null
    private var loadedOnce = false
    private var attempt = 0
    private var started = false
    private var lastWidthDp = 0
    private val refresh = Runnable { tick() }
    private val retry = Runnable { load() }
    private var pending = false

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.VfxBannerView)
            placement = a.getString(R.styleable.VfxBannerView_vfxPlacement) ?: ""
            a.recycle()
        }
        if (isInEditMode) minimumHeight = (50 * resources.displayMetrics.density).toInt()
    }

    private fun sdk(): Sdk? = VisionForgeX.sdk
    private fun p(): Placement? = sdk()?.configSnapshot()?.placements?.get(placement)?.takeIf { it.format == VfxFormat.BANNER }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stop() }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) { banner?.resume(); if (pending) tick() } else banner?.pause()
    }

    private fun start() {
        if (isInEditMode || started || placement.isBlank()) return
        started = true
        VisionForgeX.whenReady { if (isAttachedToWindow) post { load() } }
    }

    private fun stop() {
        removeCallbacks(refresh); removeCallbacks(retry)
        banner?.destroy(); banner = null
        removeAllViews()
        started = false; loadedOnce = false; attempt = 0
    }

    /** Loads (or reloads) now. Usually not needed: it loads by itself. */
    public fun load() {
        val s = sdk() ?: return
        val p = p()
        if (p == null) { VLog.w("Banner \"$placement\": no such banner placement in the settings."); return }
        if (!p.on || p.unit.isBlank() || !s.canRequest()) return
        val widthPx = (if (width > 0) width else resources.displayMetrics.widthPixels) - paddingLeft - paddingRight
        val widthDp = (widthPx / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        if (banner == null || widthDp != lastWidthDp) {
            banner?.destroy(); removeAllViews()
            lastWidthDp = widthDp
            banner = s.engine.newBanner(context, p.unit, widthDp, listener(p, s))
            addView(banner!!.view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val kv = s.keyValues(p)
        started0 = s.now()
        s.event("req", p, mapOf("tier" to 0))
        banner!!.load(EngineRequest(kv, if (!loadedOnce) p.collapsible else ""))
    }
    private var started0 = 0L

    private fun listener(p: Placement, s: Sdk) = object : BannerListener {
        override fun onLoaded(source: String?) {
            post {
                loadedOnce = true; attempt = 0
                s.event("fill", p, mapOf("tier" to 0, "ms" to s.now() - started0, "src" to source))
                schedule(p)
            }
        }
        override fun onFailed(code: Int, message: String) {
            post {
                s.event("nofill", p, mapOf("tier" to 0, "ms" to s.now() - started0, "err" to code.toString()))
                attempt++
                if (attempt <= p.retry.maxAttempts) postDelayed(retry, backoffMs(attempt, p.retry)) else if (loadedOnce) schedule(p)
            }
        }
        override fun onImpression() { post { s.event("show", p) } }
        override fun onClicked() { post { s.event("click", p) } }
        override fun onPaid(valueMicros: Long, currency: String, precision: String) { post { s.event("paid", p, mapOf("v" to valueMicros, "cur" to currency, "prec" to precision)) } }
    }

    private fun schedule(p: Placement) {
        removeCallbacks(refresh)
        if (p.refreshSec > 0) postDelayed(refresh, p.refreshSec * 1000L)
    }

    /** A refresh is due: only if enough of the banner is seen, else wait until it is. */
    private fun tick() {
        if (p() == null) return
        if (!visibleEnough()) { pending = true; postDelayed(refresh, 2000); return }
        pending = false
        load()
    }

    private fun visibleEnough(): Boolean {
        if (!isShown || windowVisibility != View.VISIBLE || !hasWindowFocus()) return false
        val r = Rect()
        if (!getGlobalVisibleRect(r)) return false
        return r.width().toLong() * r.height() * 2 >= width.toLong() * height.coerceAtLeast(1)
    }
}
