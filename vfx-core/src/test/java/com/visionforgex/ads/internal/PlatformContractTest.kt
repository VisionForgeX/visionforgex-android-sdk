package com.visionforgex.ads.internal

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.visionforgex.ads.VfxOptions
import com.visionforgex.ads.VfxShowListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The SDK against the real platform (its test server): the settings fetched and understood, the
 * placements loaded as they say (with their key-values), an ad shown, and the events accepted.
 * Runs only when the platform's tests start it (tests/slice6.php in the platform's repository):
 *   VFX_PLATFORM=http://127.0.0.1:8300 VFX_TEST_KEY=vfx_… ./gradlew :vfx-core:testDebugUnitTest --tests '*PlatformContractTest*'
 * The install ID is fixed, so the platform's test can find this run's rows.
 */
@RunWith(RobolectricTestRunner::class)
class PlatformContractTest {
    private fun waitFor(what: String, ms: Long = 15_000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!ok()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(40)
        }
    }

    @Test fun settingsFetchedAdsLoadedAsToldEventsAccepted() {
        val base = System.getProperty("vfx.platform")
        val key = System.getProperty("vfx.key")
        assumeTrue("only when the platform's test runs it", !base.isNullOrBlank() && !key.isNullOrBlank())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = MapStore().apply { putString(Sdk.INSTALL, INSTALL) }
        var now = System.currentTimeMillis()
        val engine = FakeEngine()
        val sdk = Sdk(app, key!!, VfxOptions.Builder().baseUrl(base!!).build(), engine, store) { now }
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        sdk.start(activity)
        waitFor("the settings") { sdk.configFrom == "platform" && sdk.isEngineReady() }
        val c = sdk.configSnapshot()!!
        assertTrue("the app is enabled: ${c.reason}", c.enabled)
        val inter = c.placements["interstitial"]!!
        assertTrue(inter.unit.startsWith("/"))
        // every placement that preloads was asked for at once, with the platform's key-values
        waitFor("the preloads") { engine.requests.isNotEmpty() }
        val mine = { x: FakeEngine.Req -> x.unit == inter.unit && x.kv["vfx_pl"] == "interstitial" }   // other placements may share its unit
        val req = engine.requests.first(mine)
        assertEquals("interstitial", req.kv["vfx_pl"])
        assertEquals(c.kv["vfx_app"], req.kv["vfx_app"])
        if (inter.floors.tiers.isNotEmpty()) assertEquals(inter.floors.tiers[0], req.kv[inter.floors.key])
        // answer every floored tier with no fill, then fill the request without a floor
        var r = req
        while (r.kv.containsKey(inter.floors.key)) { val was = r; r.cb.onFailed(3, "no fill"); waitFor("the next tier") { engine.requests.last(mine) !== was }; r = engine.requests.last(mine) }
        r.cb.onLoaded(engine.ad("Google AdMob"))
        waitFor("ready") { sdk.slot("interstitial").isReady() }
        now += 120_000   // past the session's grace
        var shown = false
        val why = ArrayList<String>()
        // the platform's pacing decides: call at every opportunity, as an app does, until it lets one through
        for (i in 0 until 6) {
            if (engine.lastListener != null) break
            sdk.slot("interstitial").show(activity, object : VfxShowListener { override fun onShown() { shown = true }; override fun onNotShown(reason: String) { why.add(reason) } })
            now += 120_000
        }
        assertTrue("never let through: $why", engine.lastListener != null)
        println("VFX-CONTRACT not shown first: $why")
        engine.lastListener!!.onShown()
        engine.lastListener!!.onPaid(4210, "USD", "PRECISE")
        engine.lastListener!!.onClicked()
        engine.lastListener!!.onDismissed()
        waitFor("shown") { shown }
        // the events reach the platform
        var accepted = -1
        sdk.events.flush(c.eventsUrl, EventLog.Header(key, INSTALL, sdk.sessionId, "1.0.0", "1.0", "14", "Robolectric", "PK", c.ab, c.sample)) { accepted = it }
        waitFor("the platform's answer") { accepted >= 0 }
        assertTrue("the platform accepted the events ($accepted)", accepted >= 6)
        assertEquals(0, sdk.events.size())
        println("VFX-CONTRACT-OK accepted=$accepted install=$INSTALL")
    }

    companion object { const val INSTALL = "c0ffee00-0000-4000-8000-00000000c0de" }
}
