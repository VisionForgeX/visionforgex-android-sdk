package com.visionforgex.sample

import android.app.Application
import android.util.Log
import com.visionforgex.ads.VfxLogLevel
import com.visionforgex.ads.VfxOptions
import com.visionforgex.ads.VisionForgeX

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One line in a real app: VisionForgeX.initialize(this, "vfx_…")
        val options = VfxOptions.Builder()
            .baseUrl(BuildConfig.VFX_BASE_URL)
            .logLevel(VfxLogLevel.DEBUG)
            .build()
        VisionForgeX.initialize(this, BuildConfig.VFX_APP_KEY, options) { status ->
            Log.i("Sample", "VisionForgeX ready=${status.ready} ${status.reason}")
        }
    }
}
