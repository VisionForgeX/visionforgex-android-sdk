package com.visionforgex.ads.internal

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.visionforgex.ads.VfxFormat
import com.visionforgex.ads.VfxShowListener
import com.visionforgex.ads.engine.AdEngine
import com.visionforgex.ads.engine.BannerListener
import com.visionforgex.ads.engine.ConsentEngine
import com.visionforgex.ads.engine.EngineBanner
import com.visionforgex.ads.engine.EngineFullScreenAd
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.engine.EngineSettings
import com.visionforgex.ads.engine.FullScreenListener
import com.visionforgex.ads.engine.FullScreenLoadCallback
import com.visionforgex.ads.engine.NativeLoadCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** A stand-in engine: every request is kept, and the test answers it. */
class FakeEngine : AdEngine {
    class Req(val unit: String, val kv: Map<String, String>, val cb: FullScreenLoadCallback)
    val requests = ArrayList<Req>()
    var lastListener: FullScreenListener? = null
    var destroyed = 0
    override val name = "fake"
    override val consent: ConsentEngine? = null
    override fun initialize(context: Context, settings: EngineSettings, done: () -> Unit) = done()
    override fun apply(settings: EngineSettings) {}
    override fun loadFullScreen(context: Context, format: VfxFormat, unit: String, request: EngineRequest, callback: FullScreenLoadCallback) { requests.add(Req(unit, request.keyValues, callback)) }
    override fun newBanner(context: Context, unit: String, widthDp: Int, listener: BannerListener): EngineBanner = throw UnsupportedOperationException()
    override fun loadNative(context: Context, unit: String, request: EngineRequest, callback: NativeLoadCallback) {}
    fun ad(src: String = "Google AdMob") = object : EngineFullScreenAd {
        override val source = src
        override fun show(activity: Activity, listener: FullScreenListener) { lastListener = listener }
        override fun destroy() { destroyed++ }
    }
}

/** A stand-in SDK: time moves only when the test says, and delayed work runs then. */
internal class FakeEnv(override val engine: FakeEngine, var cfg: Config?) : SlotEnv {
    var t = 1_800_000_000_000L
    private val queue = ArrayList<Pair<Long, Runnable>>()
    val events = ArrayList<Pair<String, Map<String, Any?>>>()
    override val pacer = Pacer(MapStore(), { t })
    var can = true
    override var fullScreenShowing = false
    override fun now() = t
    override fun post(delayMs: Long, r: Runnable): Any { queue.add(t + delayMs to r); if (delayMs == 0L) runDue(); return r }
    override fun cancel(token: Any) { queue.removeAll { it.second === token } }
    override fun context(): Context = ApplicationProvider.getApplicationContext()
    override fun config() = cfg
    override fun canRequest() = can
    override fun keyValues(placement: Placement) = mapOf("vfx_pl" to placement.name)
    override fun event(type: String, p: Placement, fields: Map<String, Any?>) { events.add(type to fields) }
    override fun onFullScreenClosed() {}
    fun advance(ms: Long) { t += ms; runDue() }
    private var running = false
    private fun runDue() { if (running) return; running = true; while (true) { val due = queue.filter { it.first <= t }.minByOrNull { it.first } ?: break; queue.remove(due); due.second.run() }; running = false }
    fun types() = events.map { it.first }
}

@RunWith(RobolectricTestRunner::class)
class FullScreenSlotTest {
    private lateinit var engine: FakeEngine
    private lateinit var env: FakeEnv
    private lateinit var activity: Activity

    private fun config(p: Placement, fullGap: Int = 0) = Config(3600, true, false, "", fullGap, 1800, "warn", false, false, false, "", true, "https://x/e", 1.0, 20, 30, emptyMap(), "", mapOf(p.name to p))
    private fun pl(floors: List<String> = emptyList(), cap: Cap = Cap(), retry: Retry = Retry(3, 2, 64), expiry: Int = 55) =
        Placement("lv", VfxFormat.INTERSTITIAL, true, "/1/int", true, expiry, 0, cap, retry, Floors("vfx_fl", floors, 2500), "", "", false)

    @Before fun setUp() {
        engine = FakeEngine()
        env = FakeEnv(engine, config(pl(listOf("300", "150"))))
        env.pacer.startSession(5, env.t - 3_600_000)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @Test fun asksTheFloorTiersHighestFirstThenWithoutAFloor() {
        val s = FullScreenSlot("lv", env)
        s.load()
        assertEquals(mapOf("vfx_pl" to "lv", "vfx_fl" to "300"), engine.requests[0].kv)
        engine.requests[0].cb.onFailed(3, "no fill")
        assertEquals("300 had no fill: 150 next", "150", engine.requests[1].kv["vfx_fl"])
        env.advance(2600)   // 150 too slow: counted as no fill, the request without a floor goes out
        assertEquals(3, engine.requests.size)
        assertFalse(engine.requests[2].kv.containsKey("vfx_fl"))
        engine.requests[1].cb.onLoaded(engine.ad())   // the slow one answers at last: thrown away
        assertEquals(1, engine.destroyed)
        assertFalse(s.isReady())
        engine.requests[2].cb.onLoaded(engine.ad())
        assertTrue(s.isReady())
        assertEquals(listOf("req", "nofill", "req", "nofill", "req", "fill"), env.types())
        assertEquals(listOf(1, 1, 2, 2, 0, 0), env.events.map { it.second["tier"] })
    }

    @Test fun showsOnlyWhenThePacingAllowsAndLoadsTheNextAtOnce() {
        env.cfg = config(pl(cap = Cap(minIntervalSec = 60)))
        val s = FullScreenSlot("lv", env)
        s.load(); engine.requests[0].cb.onLoaded(engine.ad())
        val said = ArrayList<String>()
        val l = object : VfxShowListener { override fun onShown() { said.add("shown") }; override fun onDismissed() { said.add("dismissed") }; override fun onNotShown(reason: String) { said.add(reason) } }
        s.show(activity, l)
        engine.lastListener!!.onShown()
        engine.lastListener!!.onPaid(4210, "USD", "PRECISE")
        engine.lastListener!!.onDismissed()
        assertEquals(listOf("shown", "dismissed"), said)
        assertEquals("the next one is asked for as soon as it closes", 2, engine.requests.size)
        engine.requests[1].cb.onLoaded(engine.ad())
        env.advance(10_000)
        s.show(activity, l)
        assertEquals("interval", said.last())
        assertTrue("…and still waiting", s.isReady())
        val paid = env.events.first { it.first == "paid" }.second
        assertEquals(4210L, paid["v"]); assertEquals("USD", paid["cur"]); assertEquals("PRECISE", paid["prec"])
        assertTrue(env.types().contains("capped"))
    }

    @Test fun notReadyLoadsAndSaysSo() {
        val s = FullScreenSlot("lv", env)
        var why = ""
        s.show(activity, object : VfxShowListener { override fun onNotShown(reason: String) { why = reason } })
        assertEquals("not_ready", why)
        assertEquals(1, engine.requests.size)
    }

    @Test fun anOldAdIsThrownAwayBeforeGoogleExpiresIt() {
        env.cfg = config(pl(expiry = 55))
        val s = FullScreenSlot("lv", env)
        s.load(); engine.requests[0].cb.onLoaded(engine.ad())
        env.advance(54 * 60_000L); assertTrue(s.isReady())
        env.advance(2 * 60_000L); assertFalse(s.isReady())
        assertEquals(1, engine.destroyed)
    }

    @Test fun retriesAfterAGrowingWaitThenGivesUpUntilAskedAgain() {
        env.cfg = config(pl(retry = Retry(3, 2, 64)))
        val s = FullScreenSlot("lv", env)
        s.load()
        engine.requests.last().cb.onFailed(3, "no fill")
        env.advance(1999); assertEquals(1, engine.requests.size)
        env.advance(1); assertEquals("after 2 s", 2, engine.requests.size)
        engine.requests.last().cb.onFailed(3, "no fill")
        env.advance(4000); assertEquals("after 4 s more", 3, engine.requests.size)
        engine.requests.last().cb.onFailed(3, "no fill")
        env.advance(8000); assertEquals("after 8 s more", 4, engine.requests.size)
        engine.requests.last().cb.onFailed(3, "no fill")
        env.advance(600_000); assertEquals("given up", 4, engine.requests.size)
        assertEquals("gave up", s.state)
        s.load(fresh = true); assertEquals("a show() or a return starts again", 5, engine.requests.size)
    }

    @Test fun nothingWithoutConsentOrWhilePaused() {
        env.can = false
        val s = FullScreenSlot("lv", env)
        s.load(); assertEquals(0, engine.requests.size)
        var why = ""
        s.show(activity, object : VfxShowListener { override fun onNotShown(reason: String) { why = reason } })
        assertEquals("consent", why)
        env.can = true
        env.cfg = env.cfg!!.copy(enabled = false)
        s.show(activity, object : VfxShowListener { override fun onNotShown(reason: String) { why = reason } })
        assertEquals("paused", why)
    }

    @Test fun neverTwoAtOnce() {
        val s = FullScreenSlot("lv", env)
        env.cfg = config(pl())
        s.load(); engine.requests[0].cb.onLoaded(engine.ad())
        env.fullScreenShowing = true
        var why = ""
        s.show(activity, object : VfxShowListener { override fun onNotShown(reason: String) { why = reason } })
        assertEquals("showing", why)
    }

}
