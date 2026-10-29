package com.visionforgex.ads.internal

import com.visionforgex.ads.VfxFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class PacerTest {
    private var now = 1_800_000_000_000L
    private val store = MapStore()
    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private fun pacer() = Pacer(store, { now }, { tz })
    private fun pl(cap: Cap, f: VfxFormat = VfxFormat.INTERSTITIAL, name: String = "lv") =
        Placement(name, f, true, "/1/u", true, 55, 0, cap, Retry(), Floors(), "", "", false)

    @Test fun graceAtTheStartOfASessionAndTheFirstSessions() {
        val p = pacer(); p.startSession(1, now)
        assertEquals("grace", p.check(pl(Cap(graceSessions = 1)), 0))
        p.startSession(2, now)
        assertNull(p.check(pl(Cap(graceSessions = 1)), 0))
        assertEquals("grace", p.check(pl(Cap(graceSec = 30)), 0))
        now += 31_000
        assertNull(p.check(pl(Cap(graceSec = 30)), 0))
    }

    @Test fun anIntervalPerPlacementAndAGapBetweenAnyTwo() {
        val p = pacer(); p.startSession(3, now - 100_000)
        val a = pl(Cap(minIntervalSec = 60))
        p.recordShow(a)
        now += 10_000
        assertEquals("gap", p.check(pl(Cap(), name = "other"), 30))
        assertEquals("a rewarded ad is asked for by the user: no gap", null, p.check(pl(Cap(), VfxFormat.REWARDED, "rw"), 30))
        now += 25_000
        assertEquals("interval", p.check(a, 30))
        now += 30_000
        assertNull(p.check(a, 30))
    }

    @Test fun perSessionPerHourPerDay() {
        val p = pacer(); p.startSession(5, now - 1_000_000)
        val s = pl(Cap(maxPerSession = 2))
        p.recordShow(s); p.recordShow(s)
        assertEquals("session", p.check(s, 0))
        p.startSession(6, now)
        assertNull(p.check(s, 0))
        val h = pl(Cap(maxPerHour = 2), name = "h")
        p.recordShow(h); now += 60_000; p.recordShow(h)
        assertEquals("hour", p.check(h, 0))
        now += 3_600_000
        assertNull(p.check(h, 0))
        val d = pl(Cap(maxPerDay = 1), name = "d")
        p.recordShow(d)
        assertEquals("day", p.check(d, 0))
        now += 86_400_000
        assertNull(p.check(d, 0))
    }

    @Test fun everyNthEligibleCall() {
        val p = pacer(); p.startSession(9, now - 1_000_000)
        val n = pl(Cap(everyNth = 3))
        assertEquals(listOf("nth", "nth", null, "nth", "nth", null), (1..6).map { p.check(n, 0) })
    }

    @Test fun theLastShowSurvivesARestart() {
        val a = pl(Cap(minIntervalSec = 600))
        pacer().apply { startSession(1, now) }.recordShow(a)
        now += 60_000
        val again = pacer(); again.startSession(2, now)
        assertEquals("interval", again.check(a, 0))
        again.reset(listOf("lv"))
        assertNull(again.check(a, 0))
    }
}
