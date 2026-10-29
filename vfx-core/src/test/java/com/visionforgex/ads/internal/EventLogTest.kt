package com.visionforgex.ads.internal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val header = EventLog.Header("vfx_k", "install-1", "ses-1", "1.0.0", "2.0", "14", "Pixel", "PK", "e1:a", 1.0)
    private var answer = 200
    private val sent = ArrayList<JSONObject>()
    private fun log() = EventLog(tmp.root, { 1_800_000_000_000L }, { it.run() }) { _, json -> sent.add(JSONObject(json)); HttpResult(answer, "{\"ok\":true}", null) }

    @Test fun sendsABatchWithTheHeader() {
        val l = log()
        l.add("req", mapOf("pl" to "lv", "f" to "interstitial", "tier" to 0))
        l.add("paid", mapOf("pl" to "lv", "f" to "interstitial", "v" to 4210L, "cur" to "USD"))
        var n = -1
        l.flush("https://x/e", header) { n = it }
        assertEquals(2, n)
        val b = sent.single()
        assertEquals("vfx_k", b.getString("k")); assertEquals("install-1", b.getString("i")); assertEquals("e1:a", b.getString("ab")); assertEquals("android", b.getString("p"))
        assertEquals(2, b.getJSONArray("e").length())
        assertEquals(4210L, b.getJSONArray("e").getJSONObject(1).getLong("v"))
        assertEquals(0, l.size())
    }

    @Test fun outOfTheSampleOnlySessionsAndRevenueGo() {
        val l = log(); l.inSample = false
        l.add("req", mapOf()); l.add("show", mapOf()); l.add("ses", mapOf("n" to 1)); l.add("paid", mapOf("v" to 5L))
        assertEquals(2, l.size())
    }

    @Test fun nothingIsLostWhenTheAppStopsOrThePlatformIsDown() {
        val a = log()
        a.add("req", mapOf("pl" to "lv")); a.add("fill", mapOf("pl" to "lv"))
        assertEquals("kept on disk: a new start finds them", 2, log().size())
        answer = 503
        val b = log(); b.flush("https://x/e", header)
        assertEquals("a 5xx keeps them", 2, b.size())
        answer = 0
        b.flush("https://x/e", header)
        assertEquals("no answer keeps them", 2, b.size())
        answer = 400
        b.flush("https://x/e", header)
        assertEquals("a 4xx drops them (they would fail again)", 0, b.size())
        assertEquals(0, log().size())
    }

    @Test fun atMost500AtATime() {
        val l = log()
        repeat(1200) { l.add("req", mapOf("i" to it)) }
        l.flush("https://x/e", header); assertEquals(700, l.size())
        assertTrue(sent.last().getJSONArray("e").length() == 500)
    }
}
