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
