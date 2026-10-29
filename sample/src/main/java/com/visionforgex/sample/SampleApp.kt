package com.visionforgex.sample

import android.app.Application
import android.content.Context
import com.visionforgex.ads.VfxLogLevel
import com.visionforgex.ads.VfxOptions
import com.visionforgex.ads.VisionForgeX

/**
 * The demo app. Two ways to run, chosen on its screen (kept until changed):
 *  - Google demo ads: every format on Google's own demo ad units, which always answer with a test ad.
 *    No account, no platform: it proves the SDK and Google Mobile Ads work on this phone.
 *  - My app on VisionForgeX: an app key from the platform; its settings, test ads (switch "Test ads" on
 *    for that app on the platform) and its events in Live.
 * A real app needs only: VisionForgeX.initialize(this, "vfx_…")
 */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val p = prefs(this)
        val key = p.getString(KEY, "") ?: ""
        val b = VfxOptions.Builder().logLevel(VfxLogLevel.DEBUG)
        if (p.getString(MODE, MODE_DEMO) == MODE_PLATFORM && key.isNotBlank()) {
            b.baseUrl(p.getString(BASE, VfxOptions.DEFAULT_BASE_URL) ?: VfxOptions.DEFAULT_BASE_URL)
            if (p.getBoolean(EEA, false)) b.consentDebugEea(true)
            VisionForgeX.initialize(this, key, b.build())
        } else {
            VisionForgeX.initialize(this, "vfx_" + "0".repeat(24), b.googleDemoAds().build())
        }
    }

    companion object {
        const val MODE = "mode"; const val KEY = "key"; const val BASE = "base"; const val EEA = "eea"
        const val MODE_DEMO = "demo"; const val MODE_PLATFORM = "platform"
        fun prefs(c: Context) = c.getSharedPreferences("demo", Context.MODE_PRIVATE)
    }
}
