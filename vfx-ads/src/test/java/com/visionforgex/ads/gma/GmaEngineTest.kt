package com.visionforgex.ads.gma

import com.google.ads.mediation.admob.AdMobAdapter
import com.visionforgex.ads.engine.EngineRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the engine hands Google: the platform's key-values, collapsible banners, our request agent. */
@RunWith(RobolectricTestRunner::class)
class GmaEngineTest {
    @Test fun everyKeyValueGoesToAdManager() {
        val r = GmaEngine().request(EngineRequest(mapOf("vfx_pl" to "level_complete", "vfx_fl" to "300", "vfx_app" to "com.example")))
        val t = r.customTargeting
        assertEquals("level_complete", t.getString("vfx_pl"))
        assertEquals("300", t.getString("vfx_fl"))
        assertEquals("com.example", t.getString("vfx_app"))
        assertEquals("VisionForgeX", r.requestAgent)
        assertNull("no collapsible unless asked", r.getNetworkExtrasBundle(AdMobAdapter::class.java)?.getString("collapsible"))
    }

    @Test fun aCollapsibleBannerAsksGoogleForIt() {
        val r = GmaEngine().request(EngineRequest(mapOf("vfx_pl" to "home"), collapsible = "bottom"))
        assertEquals("bottom", r.getNetworkExtrasBundle(AdMobAdapter::class.java)?.getString("collapsible"))
    }

    @Test fun theEngineIsFoundByName() {
        // vfx-core finds it by this name (VisionForgeX.ENGINES); renaming it would leave apps with no engine
        assertEquals(GmaEngine::class.java, Class.forName("com.visionforgex.ads.gma.GmaEngine"))
        assertEquals("gma-25", GmaEngine().name)
    }
}

/** The banner size ladder: from the largest down, only on "no fill" or "invalid request". */
class BannerSizesTest {
    @Test fun largestFirstThenStandardThenSmall() {
        assertEquals(listOf("L", "S", "B"), BannerSizes.tier(0, "L", "S", "B"))
        assertEquals(listOf("S"), BannerSizes.tier(1, "L", "S", "B"))
        assertEquals(listOf("B"), BannerSizes.tier(2, "L", "S", "B"))
    }
    @Test fun stepsDownOnlyWhenASmallerSizeCouldHelp() {
        assertEquals(1, BannerSizes.next(0, 3)); assertEquals(2, BannerSizes.next(1, 3)); assertNull(BannerSizes.next(2, 3))
        assertEquals(1, BannerSizes.next(0, 1))
        assertNull("a network error is not about the size", BannerSizes.next(0, 2))
    }
}

/** The real banner, with Google's view: a no-fill steps down to a smaller size before anything is reported. */
@RunWith(RobolectricTestRunner::class)
class BannerLadderTest {
    private class L : com.visionforgex.ads.engine.BannerListener {
        val failed = mutableListOf<Int>()
        override fun onLoaded(source: String?) {}
        override fun onFailed(code: Int, message: String) { failed += code }
        override fun onImpression() {}
        override fun onClicked() {}
        override fun onPaid(valueMicros: Long, currency: String, precision: String) {}
    }
    private fun adView(b: Banner) = (b.view as android.view.ViewGroup).getChildAt(0) as com.google.android.gms.ads.admanager.AdManagerAdView
    private fun noFill() = com.google.android.gms.ads.LoadAdError(3, "No fill.", "com.google.android.gms.ads", null, null)

    @Test fun noFillTriesTheStandardSizeThen320x50ThenReports() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val l = L()
        val b = Banner(ctx, "/21775744923/example/adaptive-banner", 360, l, GmaEngine())
        b.load(EngineRequest(mapOf("vfx_pl" to "home")))
        assertEquals(3, adView(b).adSizes!!.size)
        adView(b).adListener.onAdFailedToLoad(noFill())
        assertEquals("nothing reported yet", 0, l.failed.size)
        assertEquals(1, adView(b).adSizes!!.size)
        assertEquals("one view in the banner at a time", 1, (b.view as android.view.ViewGroup).childCount)
        adView(b).adListener.onAdFailedToLoad(noFill())
        assertEquals(com.google.android.gms.ads.AdSize.BANNER, adView(b).adSize)
        adView(b).adListener.onAdFailedToLoad(noFill())
        assertEquals("every size tried: the no-fill is reported, once", listOf(3), l.failed)
        b.destroy()
    }

    @Test fun theSizeThatFilledIsKeptForTheRefresh() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val b = Banner(ctx, "/21775744923/example/adaptive-banner", 360, L(), GmaEngine())
        b.load(EngineRequest(mapOf("vfx_pl" to "home")))
        adView(b).adListener.onAdFailedToLoad(noFill())
        adView(b).adListener.onAdLoaded()
        val filled = adView(b)
        b.load(EngineRequest(mapOf("vfx_pl" to "home")))
        assertEquals("the refresh reuses the view and size that filled", filled, adView(b))
        assertEquals(1, adView(b).adSizes!!.size)
    }
}
