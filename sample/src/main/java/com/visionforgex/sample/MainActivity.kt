package com.visionforgex.sample

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.visionforgex.ads.VfxBannerView
import com.visionforgex.ads.VfxFullScreen
import com.visionforgex.ads.VfxNativeView
import com.visionforgex.ads.VfxShowListener
import com.visionforgex.ads.VisionForgeX
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * The demo's one screen: what the SDK is running on, each full-screen format with its state and a
 * Show button, native ads in both layouts, the banner at the bottom, every ad event as it happens,
 * and the switch between Google's demo ads and an app on VisionForgeX.
 */
class MainActivity : Activity() {
    private val purple = Color.parseColor("#7C3AED")
    private val ink = Color.parseColor("#111827")
    private val muted = Color.parseColor("#6B7280")
    private val line = Color.parseColor("#E5E7EB")
    private val green = Color.parseColor("#15803D")
    private val amber = Color.parseColor("#B45309")
    private val main = Handler(Looper.getMainLooper())
    private val states = ArrayList<Pair<VfxFullScreen, TextView>>()
    private lateinit var status: TextView
    private lateinit var log: LinearLayout
    private var earned = 0L
    private lateinit var earnedView: TextView
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun round(color: Int, radius: Int = 14, stroke: Int? = null) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun text(t: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) = TextView(this).apply { text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(Color.WHITE, 16, line)
        setPadding(dp(16), dp(14), dp(16), dp(16))
        addView(text(title.uppercase(Locale.US), 11.5f, muted, true).apply { letterSpacing = 0.06f }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    private fun button(label: String, primary: Boolean = true, f: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; stateListAnimator = null
        setTextColor(if (primary) Color.WHITE else purple)
        background = round(if (primary) purple else Color.parseColor("#F5F3FF"), 10)
        setPadding(dp(14), 0, dp(14), 0)
        minHeight = dp(42); minimumHeight = dp(42)
        setOnClickListener { f() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 21) window.statusBarColor = Color.parseColor("#5B21B6")
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        val gap = { v: View -> col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }) }

        // the header
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#4F46E5"))).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(text("VisionForgeX", 22f, Color.WHITE, true))
            addView(text("SDK demo · version ${VisionForgeX.VERSION}", 13f, Color.parseColor("#E9D5FF")))
        }
        status = text("Starting…", 13.5f, Color.WHITE).apply { setPadding(0, dp(10), 0, 0) }
        head.addView(status)
        gap(head)

        // full-screen formats
        val fs = card("Full-screen ads")
        fun row(label: String, ad: VfxFullScreen) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(text(label, 15f, ink, true))
            val st = text("…", 12.5f, muted)
            names.addView(st)
            r.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(button("Show") {
                ad.show(this, object : VfxShowListener {
                    override fun onReward(type: String, amount: Int) { toast("Reward earned: $amount $type") }
                    override fun onNotShown(reason: String) { toast("Not shown: " + why(reason)) }
                })
            }, LinearLayout.LayoutParams(-2, dp(42)))
            fs.addView(r)
            states.add(ad to st)
        }
        row("Interstitial", VisionForgeX.interstitial("interstitial"))
        row("Rewarded", VisionForgeX.rewarded("rewarded"))
        row("Rewarded interstitial", VisionForgeX.rewardedInterstitial("rewarded_interstitial"))
        row("App open", VisionForgeX.appOpen("app_open"))
        fs.addView(text("App open also shows by itself when you leave the app and come back (at most once a minute here).", 12.5f, muted).apply { setPadding(0, dp(8), 0, 0) })
        gap(fs)

        // native
        val nat = card("Native: medium layout")
        nat.addView(VfxNativeView(this, "native"), LinearLayout.LayoutParams(-1, -2))
        nat.addView(text("Shows here as soon as it loads.", 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
        gap(nat)
        val nat2 = card("Native: small layout")
        nat2.addView(VfxNativeView(this, "native_small"), LinearLayout.LayoutParams(-1, -2))
        gap(nat2)

        // revenue and events
        val ev = card("What happens, as it happens")
        earnedView = text("Earned in this run: 0.000000 (Google's value of each impression; test ads are worth nothing)", 12.5f, muted)
        ev.addView(earnedView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ev.addView(log)
        gap(ev)
        VisionForgeX.setEventListener { e ->
            val color = when (e.type) { "fill", "show", "reward" -> green; "nofill", "fail", "capped" -> amber; "paid" -> purple; else -> muted }
            log.addView(text("${time.format(Date(e.time))}  $e", 11.5f, color).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(2), 0, dp(2)) }, 0)
            while (log.childCount > 40) log.removeViewAt(log.childCount - 1)
        }
        VisionForgeX.setRevenueListener { _, _, micros, cur, _, _ -> earned += micros; earnedView.text = "Earned in this run: %.6f %s (Google's value of each impression; test ads are worth nothing)".format(earned / 1e6, cur) }

        // tools and the mode
        val tools = card("Tools")
        val toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolRow.addView(button("Debug panel", false) { VisionForgeX.openDebugPanel(this) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        toolRow.addView(button("Ad inspector", false) { VisionForgeX.openAdInspector(this) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        toolRow.addView(button("Privacy", false) { if (VisionForgeX.isPrivacyOptionsRequired()) VisionForgeX.showPrivacyOptions(this) else toast("Privacy options are not required here") }, LinearLayout.LayoutParams(0, dp(42), 1f))
        tools.addView(toolRow)
        gap(tools)
        gap(modeCard())
        gap(text("Collapsible banner (opens large once, then collapses):", 12.5f, muted))
        gap(FrameLayout(this).apply { background = round(Color.WHITE, 12, line); addView(VfxBannerView(this@MainActivity, "collapsible_banner")) })

        // the page: the column scrolls, the banner stays at the bottom
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F5F3FF")) }
        page.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        page.addView(FrameLayout(this).apply { setBackgroundColor(Color.WHITE); addView(VfxBannerView(this@MainActivity, "banner"), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)) }, LinearLayout.LayoutParams(-1, -2))
        setContentView(page)
        tick()
    }

    /** Every second: the SDK's state and each placement's. */
    private fun tick() {
        val src = VisionForgeX.settingsSource()
        val mode = when (src) { "testing" -> "Google demo ads (no account needed)"; "none" -> "waiting for the settings"; else -> "your app on VisionForgeX · settings from $src" }
        status.text = (if (VisionForgeX.isReady()) "● Ready" else "○ Starting…") + "  ·  " + mode
        for ((ad, tv) in states) { val ready = ad.isReady(); tv.text = if (ready) "Ready to show" else "Loading…"; tv.setTextColor(if (ready) green else muted) }
        main.postDelayed({ tick() }, 1000)
    }

    private fun modeCard(): LinearLayout {
        val c = card("Run on")
        val p = SampleApp.prefs(this)
        val platform = p.getString(SampleApp.MODE, SampleApp.MODE_DEMO) == SampleApp.MODE_PLATFORM
        c.addView(text(if (platform) "Now: your app on VisionForgeX." else "Now: Google's demo ads. They always fill, so every format can be seen working.", 13.5f, ink))
        val key = EditText(this).apply {
            hint = "App key, vfx_… (its page on VisionForgeX)"; textSize = 14f; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(p.getString(SampleApp.KEY, "").takeUnless { it.isNullOrBlank() } ?: BuildConfig.VFX_APP_KEY.takeIf { !it.endsWith("000000000000") } ?: "")
            background = round(Color.parseColor("#F9FAFB"), 10, line); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        c.addView(key, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val base = EditText(this).apply {
            hint = "Platform address"; textSize = 13f; isSingleLine = true; inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(p.getString(SampleApp.BASE, BuildConfig.VFX_BASE_URL)); background = round(Color.parseColor("#F9FAFB"), 10, line); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        c.addView(base, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Use this app key") {
            val k = key.text.toString().trim()
            if (!Regex("^vfx_[0-9a-f]{24}$").matches(k)) { toast("An app key is vfx_ and 24 letters and digits"); return@button }
            p.edit().putString(SampleApp.MODE, SampleApp.MODE_PLATFORM).putString(SampleApp.KEY, k).putString(SampleApp.BASE, base.text.toString().trim().trimEnd('/')).apply()
            restart()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        row.addView(button("Google demo ads", false) { p.edit().putString(SampleApp.MODE, SampleApp.MODE_DEMO).apply(); restart() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        c.addView(text("Changing it restarts the app. For an app on VisionForgeX, switch \"Test ads\" on for it there to see test ads; its events then show on Live.", 12f, muted).apply { setPadding(0, dp(8), 0, 0) })
        return c
    }

    private fun why(r: String) = mapOf("not_ready" to "not loaded yet (it loads in advance; try again in a moment)", "interval" to "too soon after the last one (the pacing)", "gap" to "too soon after another full-screen ad",
        "grace" to "not at the very start of a session", "consent" to "consent does not allow ads", "paused" to "ads are off for this app").getOrElse(r) { r }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    /** Starts the app again, so the SDK starts with the new choice. */
    private fun restart() {
        // the start is asked of Android first, so it comes back in a new process once this one ends
        startActivity(Intent.makeRestartActivityTask(packageManager.getLaunchIntentForPackage(packageName)!!.component))
        exitProcess(0)
    }
}
