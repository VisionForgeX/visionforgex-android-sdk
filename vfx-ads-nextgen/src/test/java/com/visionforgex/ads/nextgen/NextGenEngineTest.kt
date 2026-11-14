package com.visionforgex.ads.nextgen

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.visionforgex.ads.engine.BannerListener
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.engine.EngineSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

/** What the Next-Gen engine hands Google: the same key-values, collapsible banners and request agent as the 25.x engine. */
@RunWith(RobolectricTestRunner::class)
class NextGenEngineTest {
    @Test fun everyKeyValueGoesToAdManager() {
        val r = AdRequest.Builder("/21775744923/example/interstitial").vfx(EngineRequest(mapOf("vfx_pl" to "level_complete", "vfx_fl" to "300", "vfx_app" to "com.example"))).build()
        assertEquals(mapOf("vfx_pl" to "level_complete", "vfx_fl" to "300", "vfx_app" to "com.example"), r.customTargeting)
        assertEquals("VisionForgeX", r.requestAgent)
        assertEquals("/21775744923/example/interstitial", r.adUnitId)
        assertNull("no collapsible unless asked", r.googleExtrasBundle?.getString("collapsible"))
    }

    @Test fun aCollapsibleBannerAsksGoogleForIt() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val b = NgBanner(ctx, "/21775744923/example/adaptive-banner", 360, L(), android.os.Handler(Looper.getMainLooper()))
        val r = b.requestFor(0, EngineRequest(mapOf("vfx_pl" to "home"), collapsible = "bottom"))
        assertEquals("bottom", r.googleExtrasBundle?.getString("collapsible"))
        assertEquals("home", r.customTargeting["vfx_pl"])
        assertEquals("large, standard and 320x50 in one request", 3, r.adSizes.size)
        assertEquals(AdSize.BANNER, b.requestFor(2, EngineRequest(emptyMap())).adSizes.single())
    }

    @Test fun theEngineIsFoundByNameAndSaysWhichGoogleSdk() {
        // vfx-core finds it by this name (VisionForgeX.ENGINES), first; renaming it would leave apps with no engine
        assertEquals(NextGenEngine::class.java, Class.forName("com.visionforgex.ads.nextgen.NextGenEngine"))
        assertEquals("ngsdk-1.6.0", NextGenEngine().name)
    }

    @Test fun childAndUnderAgeAppsGetGooglesAgeTreatment() {
        val e = NextGenEngine()
        assertEquals(AgeRestrictedTreatment.CHILD, e.configuration(EngineSettings(true, true, "G", emptyList())).ageRestrictedTreatment)
        assertEquals(AgeRestrictedTreatment.TEEN, e.configuration(EngineSettings(false, true, "", emptyList())).ageRestrictedTreatment)
        assertEquals(AgeRestrictedTreatment.UNSPECIFIED, e.configuration(EngineSettings(false, false, "", emptyList())).ageRestrictedTreatment)
        val c = e.configuration(EngineSettings(false, false, "PG", listOf("ABC123")))
        assertEquals(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG, c.maxAdContentRating)
        assertTrue("ours are passed (Google adds the emulator itself)", c.testDeviceIds.contains("ABC123"))
    }

    @Test fun googlesErrorsKeepThe25xNumbers() {
        val e = { c: LoadAdError.ErrorCode -> codeOf(LoadAdError(c, "x", null)) }
        assertEquals(3, e(LoadAdError.ErrorCode.NO_FILL))
        assertEquals(1, e(LoadAdError.ErrorCode.INVALID_REQUEST))
        assertEquals(2, e(LoadAdError.ErrorCode.NETWORK_ERROR))
        assertEquals(0, e(LoadAdError.ErrorCode.INTERNAL_ERROR))
        assertEquals(8, e(LoadAdError.ErrorCode.APP_ID_MISSING))
    }

    @Test fun theAppIdComesFromTheManifest() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals("no entry: empty, and the engine says so instead of crashing", "", NextGenEngine.appId(ctx))
        val ai = ctx.packageManager.getApplicationInfo(ctx.packageName, android.content.pm.PackageManager.GET_META_DATA)
        ai.metaData = (ai.metaData ?: android.os.Bundle()).apply { putString("com.google.android.gms.ads.APPLICATION_ID", "ca-app-pub-3940256099942544~3347511713") }
        shadowOf(ctx.packageManager).installPackage(android.content.pm.PackageInfo().apply { packageName = ctx.packageName; applicationInfo = ai })
        assertEquals("ca-app-pub-3940256099942544~3347511713", NextGenEngine.appId(ctx))
    }

    private class L : BannerListener {
        val failed = mutableListOf<Int>(); var loaded = 0
        override fun onLoaded(source: String?) { loaded++ }
        override fun onFailed(code: Int, message: String) { failed += code }
        override fun onImpression() {}
        override fun onClicked() {}
        override fun onPaid(valueMicros: Long, currency: String, precision: String) {}
    }

    /** The ladder on Next-Gen: a no-fill asks again with a smaller size before anything is reported; answers arrive off the main thread. */
    @Test fun noFillStepsDownThenReportsOnce() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val l = L()
        val b = NgBanner(ctx, "/21775744923/example/adaptive-banner", 360, l, android.os.Handler(Looper.getMainLooper()))
        val asked = mutableListOf<Pair<com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest, com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback<com.google.android.libraries.ads.mobile.sdk.banner.BannerAd>>>()
        b.send = { r, cb -> asked += r to cb }
        val noFill = LoadAdError(LoadAdError.ErrorCode.NO_FILL, "No fill.", null)
        b.load(EngineRequest(mapOf("vfx_pl" to "home")))
        assertEquals(3, asked.last().first.adSizes.size)
        Thread { asked.last().second.onAdFailedToLoad(noFill) }.apply { start(); join() }   // Google answers on its own thread
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("nothing reported yet: the standard size alone is asked", 0, l.failed.size)
        assertEquals(1, asked.last().first.adSizes.size)
        asked.last().second.onAdFailedToLoad(noFill); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AdSize.BANNER, asked.last().first.adSizes.single())
        asked.last().second.onAdFailedToLoad(noFill); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("every size tried: the no-fill is reported, once", listOf(3), l.failed)
        assertEquals(3, asked.size)
        assertTrue(b.detail, Regex("""^\d+x\d+\+\d+x\d+\+320x50=3 → \d+x\d+=3 → 320x50=3$""").matches(b.detail))
        // a stale answer (from before a new load) is ignored
        val old = asked.last().second
        b.load(EngineRequest(mapOf("vfx_pl" to "home")))
        old.onAdFailedToLoad(LoadAdError(LoadAdError.ErrorCode.NETWORK_ERROR, "late", null)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("an answer to an older load changes nothing", listOf(3), l.failed)
        asked.last().second.onAdFailedToLoad(LoadAdError(LoadAdError.ErrorCode.NETWORK_ERROR, "Network", null)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a network error is reported at once, not retried smaller", listOf(3, 2), l.failed)
        b.destroy()
        asked.last().second.onAdFailedToLoad(noFill); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("nothing after destroy", listOf(3, 2), l.failed)
    }
}

class NgBannerSizesTest {
    @Test fun largestFirstThenStandardThenSmall() {
        assertEquals(listOf("L", "S", "B"), BannerSizes.tier(0, "L", "S", "B"))
        assertEquals(listOf("B"), BannerSizes.tier(2, "L", "S", "B"))
        assertEquals(1, BannerSizes.next(0, 3)); assertNull(BannerSizes.next(2, 3)); assertNull(BannerSizes.next(0, 2))
    }
}
