package com.visionforgex.ads

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.visionforgex.ads.engine.EngineNativeAd
import com.visionforgex.ads.engine.NativeListener
import com.visionforgex.ads.internal.Placement
import com.visionforgex.ads.internal.VLog

/**
 * A native ad for one placement, drawn in the SDK's own layout ("small": icon, headline, button;
 * "medium": with the media and the text), the one the platform names. Takes the ad waiting in the
 * cache when there is one, so it shows at once.
 *
 * XML: `<com.visionforgex.ads.VfxNativeView app:vfxPlacement="feed_native" android:layout_width="match_parent" android:layout_height="wrap_content"/>`
 */
public class VfxNativeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {
    public constructor(context: Context, placement: String) : this(context) { this.placement = placement }

    public var placement: String = ""
        set(v) { field = v; if (isAttachedToWindow) start() }

    private var ad: EngineNativeAd? = null
    private var started = false

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.VfxNativeView)
            placement = a.getString(R.styleable.VfxNativeView_vfxPlacement) ?: ""
            a.recycle()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); ad?.destroy(); ad = null; removeAllViews(); started = false }

    private fun p(): Placement? = VisionForgeX.sdk?.configSnapshot()?.placements?.get(placement)?.takeIf { it.format == VfxFormat.NATIVE }

    private fun start() {
        if (isInEditMode || started || placement.isBlank()) return
        started = true
        VisionForgeX.whenReady { post { load() } }
    }

    /** Loads a new ad into the view. */
    public fun load() {
        val s = VisionForgeX.sdk ?: return
        val p = p() ?: run { VLog.w("Native \"$placement\": no such native placement in the settings."); return }
        if (!p.on || !s.canRequest()) return
        s.nativeCache.take(p)?.let { show(it, p); return }
        s.nativeCache.load(p) { a -> if (a != null && isAttachedToWindow) show(a, p) else a?.destroy() }
    }

    private fun show(a: EngineNativeAd, p: Placement) {
        val s = VisionForgeX.sdk ?: return
        ad?.destroy()
        ad = a
        removeAllViews()
        addView(a.render(context, p.template, object : NativeListener {
            override fun onImpression() { post { s.event("show", p) } }
            override fun onClicked() { post { s.event("click", p) } }
            override fun onPaid(valueMicros: Long, currency: String, precision: String) { post { s.event("paid", p, mapOf("v" to valueMicros, "cur" to currency, "prec" to precision)) } }
        }), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
