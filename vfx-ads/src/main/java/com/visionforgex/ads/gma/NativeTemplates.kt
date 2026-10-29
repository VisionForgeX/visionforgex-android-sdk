package com.visionforgex.ads.gma

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.visionforgex.ads.engine.NativeListener

/**
 * The two native layouts, built in code (no resources for the app to merge): "small" — the "Ad"
 * badge, icon, headline, advertiser or rating, and the button; "medium" — the same over Google's
 * media view and the body text. Every asset Google gives is registered with the NativeAdView, as
 * its policy requires; the badge is always shown.
 */
@android.annotation.SuppressLint("SetTextI18n")   // "Ad": the badge Google's policy asks for, the same word everywhere
internal object NativeTemplates {
    fun build(ctx: Context, ad: NativeAd, template: String, @Suppress("UNUSED_PARAMETER") l: NativeListener): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt() }
        val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fg = if (night) Color.parseColor("#F3F4F6") else Color.parseColor("#111827")
        val fg2 = if (night) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")
        val accent = Color.parseColor("#7C3AED")
        val nav = NativeAdView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(if (night) Color.parseColor("#1F2937") else Color.WHITE); setStroke(dp(1), if (night) Color.parseColor("#374151") else Color.parseColor("#E5E7EB")) }
        }
        val badge = TextView(ctx).apply {
            text = "Ad"; textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
            setPadding(dp(6), dp(1), dp(6), dp(1))
            background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(Color.parseColor("#F59E0B")) }
        }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        ad.icon?.drawable?.let { icon.setImageDrawable(it); top.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) }) }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val headRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headRow.addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        val headline = TextView(ctx).apply { text = ad.headline; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(fg); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        headRow.addView(headline, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        texts.addView(headRow)
        var advertiser: TextView? = null
        var stars: RatingBar? = null
        if (!ad.advertiser.isNullOrBlank()) {
            advertiser = TextView(ctx).apply { text = ad.advertiser; textSize = 12f; setTextColor(fg2); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            texts.addView(advertiser)
        } else ad.starRating?.let { r ->
            stars = RatingBar(ctx, null, android.R.attr.ratingBarStyleSmall).apply { numStars = 5; stepSize = 0.5f; rating = r.toFloat(); setIsIndicator(true) }
            texts.addView(stars)
        }
        top.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)
        var media: MediaView? = null
        var body: TextView? = null
        if (template != "small") {
            media = MediaView(ctx).apply { setImageScaleType(ImageView.ScaleType.CENTER_CROP) }
            root.addView(media, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)).apply { topMargin = dp(10) })
            if (!ad.body.isNullOrBlank()) {
                body = TextView(ctx).apply { text = ad.body; textSize = 13f; setTextColor(fg2); maxLines = 3; ellipsize = TextUtils.TruncateAt.END }
                root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            }
        }
        var cta: Button? = null
        if (!ad.callToAction.isNullOrBlank()) {
            cta = Button(ctx).apply {
                text = ad.callToAction; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(accent) }
            }
            root.addView(cta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(10) })
        }
        nav.addView(root)
        nav.headlineView = headline
        if (ad.icon?.drawable != null) nav.iconView = icon
        advertiser?.let { nav.advertiserView = it }
        stars?.let { nav.starRatingView = it }
        body?.let { nav.bodyView = it }
        cta?.let { nav.callToActionView = it }
        media?.let { nav.mediaView = it }
        nav.setNativeAd(ad)
        return nav
    }
}
