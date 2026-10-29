package com.visionforgex.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.visionforgex.ads.VfxBannerView
import com.visionforgex.ads.VfxNativeView
import com.visionforgex.ads.VfxShowListener
import com.visionforgex.ads.VisionForgeX

/** Every format, by placement name: the names are the app's placements on the platform. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val say = { t: String -> Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
        fun button(label: String, f: () -> Unit) = col.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { f() } })
        col.addView(TextView(this).apply { text = "VisionForgeX SDK ${VisionForgeX.VERSION}"; textSize = 18f })
        button("Interstitial (\"interstitial\")") {
            VisionForgeX.interstitial("interstitial").show(this, object : VfxShowListener {
                override fun onNotShown(reason: String) = say("Not shown: $reason")
                override fun onDismissed() = say("Closed")
            })
        }
        button("Rewarded (\"rewarded\")") { VisionForgeX.rewarded("rewarded").show(this) { type, amount -> say("Reward: $amount $type") } }
        button("Rewarded interstitial") { VisionForgeX.rewardedInterstitial("rewarded_interstitial").show(this) { _, amount -> say("Reward: $amount") } }
        button("App open") { VisionForgeX.appOpen("app_open").show(this) }
        button("Privacy options") { if (VisionForgeX.isPrivacyOptionsRequired()) VisionForgeX.showPrivacyOptions(this) else say("Not required here") }
        button("Debug panel") { VisionForgeX.openDebugPanel(this) }
        col.addView(VfxNativeView(this, "native"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(VfxBannerView(this, "banner"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(page)
    }
}
