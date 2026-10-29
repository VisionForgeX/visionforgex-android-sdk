package com.visionforgex.ads.internal

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.visionforgex.ads.VfxEvent
import com.visionforgex.ads.VfxOptions
import com.visionforgex.ads.VisionForgeX
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Google's demo ads: the settings given by the app, nothing fetched, every format on Google's demo units. */
@RunWith(RobolectricTestRunner::class)
class DemoSettingsTest {
    @After fun tearDown() { VisionForgeX.setEventListener(null); VisionForgeX.setRevenueListener(null) }

    @Test fun demoSettingsAreUsedAndNothingIsFetched() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = FakeEngine()
        val events = ArrayList<VfxEvent>()
        var paid = 0L
        VisionForgeX.setEventListener { events.add(it) }
        VisionForgeX.setRevenueListener { _, _, v, _, _, _ -> paid += v }
        val sdk = Sdk(app, "vfx_" + "0".repeat(24), VfxOptions.Builder().baseUrl("http://127.0.0.1:1").googleDemoAds().build(), engine, MapStore())
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        sdk.start(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("testing", sdk.configFrom)
        val c = sdk.configSnapshot()!!
        assertTrue(c.enabled)
        assertEquals("/21775744923/example/interstitial", c.placements["interstitial"]!!.unit)
        assertEquals("/21775744923/example/native", c.placements["native_small"]!!.unit)
        assertEquals("small", c.placements["native_small"]!!.template)
        assertTrue("nothing is sent anywhere", !c.eventsOn)
        // the preloads were asked of the engine at once, and the app hears each event
        assertTrue(engine.requests.any { it.unit == "/21775744923/example/rewarded" })
        assertTrue(events.any { it.type == "req" && it.placement == "interstitial" })
        // a paid event reaches the app's revenue listener even with events off
        val r = engine.requests.first { it.kv["vfx_pl"] == "interstitial" }
        r.cb.onLoaded(engine.ad())
        shadowOf(Looper.getMainLooper()).idle()
        sdk.slot("interstitial").show(activity, null)
        engine.lastListener!!.onShown(); engine.lastListener!!.onPaid(1234, "USD", "PRECISE")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1234L, paid)
        assertTrue(events.any { it.type == "paid" && it.details["v"] == 1234L })
    }

    @Test fun settingsTheSdkCannotReadStartNothing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val engine = FakeEngine()
        val sdk = Sdk(app, "vfx_" + "0".repeat(24), VfxOptions.Builder().baseUrl("http://127.0.0.1:1").settingsForTesting("{not json").build(), engine, MapStore())
        sdk.start(null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(sdk.configSnapshot() == null && engine.requests.isEmpty())
    }
}
