package com.visionforgex.ads.debug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.visionforgex.ads.VfxFormat
import com.visionforgex.ads.VfxShowListener
import com.visionforgex.ads.VisionForgeX
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The SDK's debug panel (VisionForgeX.openDebugPanel): what the platform's settings say, what each
 * placement is doing, its pacing, the consent state and the last events; load and show any
 * placement by hand, reset the pacing, ask for the settings again, open Google's ad inspector.
 */
public class VfxDebugActivity : Activity() {
    private lateinit var box: LinearLayout
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "VisionForgeX SDK"
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32)) }
        setContentView(ScrollView(this).apply { addView(box) })
        draw()
    }

    override fun onResume() { super.onResume(); draw() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun head(t: String) = box.addView(TextView(this).apply { text = t.uppercase(Locale.US); textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#7C3AED")); setPadding(0, dp(18), 0, dp(6)) })
    private fun line(t: String, mono: Boolean = false) = box.addView(TextView(this).apply { text = t; textSize = 13f; setTextIsSelectable(true); if (mono) typeface = Typeface.MONOSPACE; setPadding(0, dp(2), 0, dp(2)) })
    private fun buttons(vararg b: Pair<String, () -> Unit>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        b.forEach { (label, f) -> row.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { f(); box.postDelayed({ draw() }, 300) } }) }
        box.addView(HorizontalScrollView(this).apply { addView(row) })
    }

    private fun draw() {
        box.removeAllViews()
        val s = VisionForgeX.sdk
        if (s == null) { line("The SDK has not been started: call VisionForgeX.initialize() in Application.onCreate."); return }
        val c = s.configSnapshot()
        head("The SDK")
        line("Version ${VisionForgeX.VERSION} · engine ${s.engine.name} ${if (s.isEngineReady()) "(ready)" else "(not started)"}")
        line("App key ${s.key}", true)
        line("Install ${s.installId}", true)
        line("Session ${s.sessionNumber} · events waiting ${s.events.size()}")
        buttons("Copy install ID" to { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("install", s.installId)); Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() },
            "Ask for settings" to { s.fetchConfig("debug panel") }, "Send events" to { s.flushNow() }, "Ad inspector" to { VisionForgeX.openAdInspector(this) })
        head("Settings")
        if (c == null) line("None yet (${s.configFrom}).")
        else {
            line("From ${s.configFrom} at ${if (s.configAt > 0) time.format(Date(s.configAt)) else "—"} · refreshed every ${c.ttlSec / 60} min")
            line(if (c.enabled) "Ads on${if (c.test) " · TEST ADS (Google's demo units)" else ""}" else "Ads OFF: ${c.reason}")
            line("Consent: ${if (c.ump) "Google's form where needed" else "not asked"} · can request ads: ${s.canRequest()}${s.consentError?.let { " · $it" } ?: ""}")
            line("Child-directed ${c.child} · under age ${c.underAge} · max rating ${c.rating.ifEmpty { "any" }} · experiment ${c.ab.ifEmpty { "none" }}")
            line("Least gap between full-screen ads ${c.fullGapSec} s · events ${if (c.eventsOn) "on (sample ${(c.sample * 100).toInt()}%, ${if (s.events.inSample) "in it" else "not in it"})" else "off"}")
            buttons("Reset pacing" to { s.pacer.reset(c.placements.keys) }, "Ask consent again" to { s.resetConsent(); recreate() }, "Privacy options" to { VisionForgeX.showPrivacyOptions(this) })
            head("Placements")
            for ((n, p) in c.placements) {
                val state = when {
                    p.format.isFullScreen -> s.slot(n).describe()
                    p.format == VfxFormat.NATIVE -> s.nativeCache.describe(n)
                    else -> "a view loads it"
                }
                line("$n · ${p.format.wire}${if (!p.on) " · OFF" else ""} · $state", false)
                line("  ${p.unit}", true)
                val cap = p.cap
                line("  ${if (p.preload) "preload · " else ""}${if (p.refreshSec > 0) "refresh ${p.refreshSec} s · " else ""}${if (cap.minIntervalSec > 0) "${cap.minIntervalSec} s apart · " else ""}${if (cap.graceSec > 0) "grace ${cap.graceSec} s · " else ""}${if (cap.graceSessions > 0) "not in first ${cap.graceSessions} sessions · " else ""}${if (cap.maxPerSession > 0) "≤${cap.maxPerSession}/session · " else ""}${if (cap.maxPerHour > 0) "≤${cap.maxPerHour}/h · " else ""}${if (p.floors.tiers.isNotEmpty()) "floors ${p.floors.tiers.joinToString(", ")} · " else ""}shown ${s.pacer.shownThisSession(n)} this session")
                if (p.format.isFullScreen) {
                    val fs = when (p.format) { VfxFormat.REWARDED -> VisionForgeX.rewarded(n); VfxFormat.REWARDED_INTERSTITIAL -> VisionForgeX.rewardedInterstitial(n); VfxFormat.APP_OPEN -> VisionForgeX.appOpen(n); else -> VisionForgeX.interstitial(n) }
                    buttons("Load" to { fs.load() }, "Show" to { fs.show(this, object : VfxShowListener { override fun onNotShown(reason: String) { Toast.makeText(this@VfxDebugActivity, "Not shown: $reason", Toast.LENGTH_SHORT).show() } }) })
                }
            }
        }
        head("Last events")
        val ev = synchronized(s.events) { s.events.recent.toList() }
        if (ev.isEmpty()) line("None yet.") else ev.forEach { line(it, true) }
        box.addView(View(this).apply { minimumHeight = dp(24) })
    }
}
