package com.visionforgex.ads.internal

import com.visionforgex.ads.VfxFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrebidTest {
    private val settings = PrebidSettings("https://pbs.example.com/openrtb2/auction", "vfx-a1", 800, 3, 600)
    private val device = Prebid.Device("android", "14", "Google", "Pixel 8", false, "en", 1080, 2400, 2.75, "Mozilla/5.0 (Linux; Android 14)")
    private val noSignals = Prebid.Signals(null, "", "", "", "")

    // ------------------------------------------------------------ the settings

    private val base = """{"v":1,"ttl":3600,"app":{"enabled":true},"placements":{
        "ban":{"format":"banner","on":true,"unit":"/1/b","prebid":{"imp":"vfx-a1-banner"}},
        "int":{"format":"interstitial","on":true,"unit":"/1/i","prebid":{"imp":"vfx-a1-interstitial"}},
        "rew":{"format":"rewarded","on":true,"unit":"/1/r","prebid":{"imp":"vfx-a1-rewarded"}},
        "plain":{"format":"banner","on":true,"unit":"/1/p"}}"""

    @Test fun theBlockAndTheEntriesAreRead() {
        val c = ConfigParser.parse(base + ""","prebid":{"url":"https://pbs.example.com/openrtb2/auction","request":"vfx-a1","timeoutMs":900,"breaker":{"failures":4,"pauseSec":300}}}""")!!
        assertEquals(PrebidSettings("https://pbs.example.com/openrtb2/auction", "vfx-a1", 900, 4, 300), c.prebid)
        assertEquals("vfx-a1-banner", c.placements["ban"]!!.prebidImp)
        assertEquals("vfx-a1-interstitial", c.placements["int"]!!.prebidImp)
        assertEquals("formats Prebid does not bid for are never given an imp, whatever the settings say", "", c.placements["rew"]!!.prebidImp)
        assertEquals("", c.placements["plain"]!!.prebidImp)
    }

    @Test fun settingsWithoutTheBlockAreExactlyAsBefore() {
        val c = ConfigParser.parse(base + "}")!!
        assertNull(c.prebid)
        assertEquals("vfx-a1-banner", c.placements["ban"]!!.prebidImp)   // an entry alone does nothing: Sdk asks only when the block is there
    }

    @Test fun anUnusableBlockMeansNothingIsBidFor() {
        assertNull(ConfigParser.parse(base + ""","prebid":{"url":"ftp://x","request":"vfx-a1"}}""")!!.prebid)
        assertNull(ConfigParser.parse(base + ""","prebid":{"url":"https://x/y","request":"has space"}}""")!!.prebid)
        assertNull(ConfigParser.parse(base + ""","prebid":{"url":"https://x/y"}}""")!!.prebid)
        assertNull(ConfigParser.parse(base + ""","prebid":"yes"}""")!!.prebid)
    }

    @Test fun theWaitAndTheBreakerStayWithinBounds() {
        val a = ConfigParser.parse(base + ""","prebid":{"url":"https://x/y","request":"r","timeoutMs":999999,"breaker":{"failures":0,"pauseSec":1}}}""")!!.prebid!!
        assertEquals(3000L, a.timeoutMs); assertEquals(1, a.breakerFailures); assertEquals(30, a.breakerPauseSec)
        val b = ConfigParser.parse(base + ""","prebid":{"url":"https://x/y","request":"r","timeoutMs":1}}""")!!.prebid!!
        assertEquals(100L, b.timeoutMs)
    }

    @Test fun anImpThatIsNotAStoredIdIsDropped() {
        val c = ConfigParser.parse("""{"v":1,"placements":{"ban":{"format":"banner","on":true,"unit":"/1/b","prebid":{"imp":"a b\"c"}}}}""")!!
        assertEquals("", c.placements["ban"]!!.prebidImp)
    }

    // ------------------------------------------------------------ the request

    @Test fun theRequestCarriesOnlyIdsAndWhatTheDeviceSays() {
        val j = JSONObject(Prebid.request(settings, "vfx-a1-banner", "2.3", "1.1.0", device, noSignals, "auction-1"))
        assertEquals("auction-1", j.getString("id"))
        assertEquals("vfx-a1-banner", j.getJSONArray("imp").getJSONObject(0).getJSONObject("ext").getJSONObject("prebid").getJSONObject("storedrequest").getString("id"))
        assertEquals("vfx-a1", j.getJSONObject("ext").getJSONObject("prebid").getJSONObject("storedrequest").getString("id"))
        assertEquals("VisionForgeX", j.getJSONArray("imp").getJSONObject(0).getString("displaymanager"))
        assertEquals("2.3", j.getJSONObject("app").getString("ver"))
        val d = j.getJSONObject("device")
        assertEquals("android", d.getString("os")); assertEquals(4, d.getInt("devicetype")); assertEquals(1080, d.getInt("w"))
        assertFalse("no advertising ID, no location", d.has("ifa") || d.has("geo"))
        assertFalse("nothing the platform stores is sent from the app (bundle, publisher, sizes, bidders)", j.has("site") || j.getJSONObject("app").has("bundle") || j.getJSONArray("imp").getJSONObject(0).has("banner"))
        assertFalse("no consent: no user object", j.has("user"))
        assertEquals(0, j.getJSONObject("regs").getJSONObject("ext").length())
    }

    @Test fun aTabletSaysSo() {
        val t = Prebid.Device("android", "14", "Samsung", "Tab", true, "en", 1600, 2560, 2.0, "ua")
        assertEquals(5, JSONObject(Prebid.request(settings, "i", "1", "1", t, noSignals)).getJSONObject("device").getInt("devicetype"))
    }

    @Test fun consentSignalsGoAsTheIabHasThem() {
        val s = Prebid.Signals(true, "CPtc-string", "1YNN", "DBABM~BVQqAAAAAgA.QA", "2_7")
        val j = JSONObject(Prebid.request(settings, "i", "1", "1", device, s))
        assertEquals(1, j.getJSONObject("regs").getJSONObject("ext").getInt("gdpr"))
        assertEquals("1YNN", j.getJSONObject("regs").getJSONObject("ext").getString("us_privacy"))
        assertEquals("DBABM~BVQqAAAAAgA.QA", j.getJSONObject("regs").getString("gpp"))
        assertEquals("[2,7]", j.getJSONObject("regs").getJSONArray("gpp_sid").toString())
        assertEquals("CPtc-string", j.getJSONObject("user").getJSONObject("ext").getString("consent"))
        val no = JSONObject(Prebid.request(settings, "i", "1", "1", device, Prebid.Signals(false, "", "", "", "")))
        assertEquals("GDPR not applying is said too", 0, no.getJSONObject("regs").getJSONObject("ext").getInt("gdpr"))
    }

    @Test fun gdprThatAppliesWithoutAConsentStringMeansNoAuction() {
        assertEquals("consent", Prebid.mustSkip(Prebid.Signals(true, "", "", "", "")))
        assertNull(Prebid.mustSkip(Prebid.Signals(true, "CPtc", "", "", "")))
        assertNull("GDPR not applying: fine", Prebid.mustSkip(Prebid.Signals(false, "", "", "", "")))
        assertNull("nothing known (no consent form, outside the regions): fine", Prebid.mustSkip(noSignals))
    }

    // ------------------------------------------------------------ the answer

    private fun answer(vararg bids: String) = """{"id":"x","cur":"USD","seatbid":[{"seat":"appnexus","bid":[${bids.joinToString(",")}]}]}"""
    private val winner = """{"id":"b1","impid":"1","price":1.23,"ext":{"prebid":{"targeting":{"hb_pb":"1.20","hb_bidder":"appnexus","hb_size":"320x50","hb_cache_id":"abc-123","hb_cache_host":"pbs.example.com","hb_cache_path":"/cache"}}}}"""
    private val loser = """{"id":"b2","impid":"1","price":0.5}"""

    @Test fun theWinnersKeysAreTaken() {
        val r = Prebid.response(200, answer(loser, winner), 120)
        assertEquals("bid", r.result); assertEquals(1_230_000L, r.valueMicros); assertEquals(120L, r.ms)
        assertEquals("1.20", r.keys["hb_pb"]); assertEquals("appnexus", r.keys["hb_bidder"]); assertEquals("/cache", r.keys["hb_cache_path"])
    }

    @Test fun onlyHbKeysWithSafeValuesAreKept() {
        val w = """{"price":2,"ext":{"prebid":{"targeting":{"hb_pb":"2.00","evil":"x","hb_x":"<script>","vfx_pl":"hijack","hb_ok":"fine_1"}}}}"""
        val r = Prebid.response(200, answer(w), 1)
        assertEquals(setOf("hb_pb", "hb_ok"), r.keys.keys)
    }

    @Test fun noBidsAreNotAnError() {
        assertEquals("nobid", Prebid.response(204, "", 5).result)
        assertEquals("nobid", Prebid.response(200, """{"id":"x","seatbid":[]}""", 5).result)
        assertEquals("nobid", Prebid.response(200, answer(loser), 5).result)
        assertEquals("a winner without a price bucket cannot match any line item", "nobid", Prebid.response(200, answer("""{"price":1,"ext":{"prebid":{"targeting":{"hb_bidder":"x"}}}}"""), 5).result)
    }

    @Test fun anythingElseIsAnError() {
        assertEquals("error", Prebid.response(500, "down", 5).result)
        assertEquals("error", Prebid.response(400, "Invalid request", 5).result)
        assertEquals("error", Prebid.response(200, "<html>", 5).result)
    }

    @Test fun aBidIsNeverWorthMoreThanAHundredForOneImpression() {
        assertEquals(100_000_000L, Prebid.response(200, answer("""{"price":5000000,"ext":{"prebid":{"targeting":{"hb_pb":"20.00"}}}}"""), 1).valueMicros)
    }

    // ------------------------------------------------------------ the breaker

    private fun fail(r: String = "error") = Prebid.Result(r, emptyMap(), 0, 10)

    @Test fun threeFailuresInARowPauseTheAskingThenItResumes() {
        val b = Prebid.Breaker()
        b.record(fail(), 1000, settings); b.record(fail("timeout"), 1100, settings)
        assertFalse(b.open(1200))
        b.record(fail(), 1200, settings)
        assertTrue(b.open(1300)); assertTrue(b.open(1200 + 599_000))
        assertFalse("after the pause", b.open(1200 + 600_000))
    }

    @Test fun anAnswerEndsTheRunAndSkipsDoNotCount() {
        val b = Prebid.Breaker()
        b.record(fail(), 0, settings); b.record(fail(), 0, settings)
        b.record(Prebid.Result("nobid", emptyMap(), 0, 5), 0, settings)   // the server is alive
        b.record(fail(), 0, settings); b.record(fail(), 0, settings)
        b.record(Prebid.Result.skip(), 0, settings)
        assertFalse(b.open(1))
    }

    // ------------------------------------------------------------ the runner, on a clock that moves when the test says

    private class Rig(val http: (String, String, Int) -> HttpResult, val sig: Prebid.Signals) {
        var t = 1_000_000L
        val timers = ArrayList<Pair<Long, Runnable>>()
        val io = ArrayList<Runnable>()
        val events = ArrayList<Triple<String, Prebid.Result, String>>()
        val sent = ArrayList<Triple<String, String, Int>>()
        val answers = ArrayList<Map<String, String>>()
        val runner = PrebidRunner(post = { d, r -> timers.add(t + d to r); r }, cancel = { x -> timers.removeAll { it.second === x } }, now = { t }, io = { io.add(it) },
            send = { u, b, tmo -> sent.add(Triple(u, b, tmo)); http(u, b, tmo) }, device = { Prebid.Device("android", "14", "G", "P", false, "en", 1, 1, 1.0, "") }, signals = { sig },
            appVersion = { "1.0" }, sdkVersion = "1.1.0", event = { p, r, why -> events.add(Triple(p.name, r, why)) })
        fun pump() { var again = true; while (again) { again = false; val due = timers.filter { it.first <= t }.minByOrNull { it.first }; if (due != null) { timers.remove(due); due.second.run(); again = true } } }
        fun advance(ms: Long) { t += ms; pump() }
        fun runIo() { val l = ArrayList(io); io.clear(); l.forEach { it.run() }; pump() }
    }
    private fun cfg(c: String = base + ""","prebid":{"url":"https://pbs.example.com/openrtb2/auction","request":"vfx-a1"}}""") = ConfigParser.parse(c)!!
    private fun ban(c: Config) = c.placements["ban"]!!

    @Test fun aWinningBidGivesItsKeysAndIsReported() {
        val rig = Rig({ _, _, _ -> HttpResult(200, answer(winner), null) }, noSignals)
        val c = cfg()
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        assertEquals("nothing is answered before the server has", 0, rig.answers.size)
        rig.t += 150; rig.runIo()
        assertEquals(1, rig.answers.size); assertEquals("1.20", rig.answers[0]["hb_pb"])
        assertEquals("pb", "bid", rig.events.single().second.result); assertEquals(150L, rig.events.single().second.ms); assertEquals(1_230_000L, rig.events.single().second.valueMicros)
        assertEquals("the settings' address and wait", Triple("https://pbs.example.com/openrtb2/auction", 800, "vfx-a1-banner"), Triple(rig.sent[0].first, rig.sent[0].third, JSONObject(rig.sent[0].second).getJSONArray("imp").getJSONObject(0).getJSONObject("ext").getJSONObject("prebid").getJSONObject("storedrequest").getString("id")))
        assertTrue("the wait timer is gone once answered", rig.timers.isEmpty())
    }

    @Test fun aSlowServerCostsAtMostTheWaitAndTheLateAnswerIsDropped() {
        val rig = Rig({ _, _, _ -> HttpResult(200, answer(winner), null) }, noSignals)
        val c = cfg()
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        rig.advance(899)
        assertEquals(0, rig.answers.size)
        rig.advance(1)
        assertEquals("answered with no bids at the wait (800) plus a short grace (100)", listOf(emptyMap<String, String>()), rig.answers)
        assertEquals("timeout", rig.events.single().second.result)
        rig.runIo()
        assertEquals("the late answer changes nothing and is not counted twice", 1, rig.answers.size)
        assertEquals(1, rig.events.size)
    }

    @Test fun aServerThatCannotBeReachedIsAnErrorNotATimeoutWhenItFailsAtOnce() {
        val rig = Rig({ _, _, _ -> HttpResult(0, "", null) }, noSignals)
        val c = cfg()
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        rig.t += 20; rig.runIo()
        assertEquals("error", rig.events.single().second.result)
        assertEquals(listOf(emptyMap<String, String>()), rig.answers)
    }

    @Test fun afterThreeFailuresItDoesNotAskAndSaysWhy() {
        val rig = Rig({ _, _, _ -> HttpResult(500, "down", null) }, noSignals)
        val c = cfg()
        repeat(3) { rig.runner.run(c, ban(c)) { rig.answers.add(it) }; rig.t += 30; rig.runIo() }
        assertEquals(3, rig.sent.size)
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        assertEquals("left alone: no request, answered at once", 3, rig.sent.size)
        assertEquals(4, rig.answers.size)
        assertEquals(Pair("skip", "breaker"), rig.events.last().second.result to rig.events.last().third)
        rig.t += 601_000
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        rig.runIo()
        assertEquals("after the pause it asks again", 4, rig.sent.size)
    }

    @Test fun gdprWithoutAConsentStringNeverLeavesTheApp() {
        val rig = Rig({ _, _, _ -> HttpResult(200, answer(winner), null) }, Prebid.Signals(true, "", "", "", ""))
        val c = cfg()
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        assertEquals(0, rig.sent.size)
        assertEquals(listOf(emptyMap<String, String>()), rig.answers)
        assertEquals(Pair("skip", "consent"), rig.events.single().second.result to rig.events.single().third)
    }

    @Test fun nothingIsAskedWhenTheSettingsDoNotSayToOrTheAppIsForChildrenOrTesting() {
        val rig = Rig({ _, _, _ -> HttpResult(200, answer(winner), null) }, noSignals)
        val noBlock = cfg(base + "}")
        rig.runner.run(noBlock, ban(noBlock)) { rig.answers.add(it) }
        val plain = cfg(); rig.runner.run(plain, plain.placements["plain"]!!) { rig.answers.add(it) }
        val child = cfg(base.replace("\"app\":{\"enabled\":true}", "\"app\":{\"enabled\":true},\"consent\":{\"child\":true}") + ""","prebid":{"url":"https://x/y","request":"r"}}""")
        rig.runner.run(child, ban(child)) { rig.answers.add(it) }
        val test = cfg(base.replace("\"app\":{\"enabled\":true}", "\"app\":{\"enabled\":true,\"test\":true}") + ""","prebid":{"url":"https://x/y","request":"r"}}""")
        rig.runner.run(test, ban(test)) { rig.answers.add(it) }
        assertEquals("every one answered at once, with nothing", 4, rig.answers.size)
        assertEquals(0, rig.sent.size); assertEquals("…and none is an event", 0, rig.events.size)
    }

    @Test fun twoAuctionsAtOnceDoNotMixTheirAnswers() {
        var n = 0
        val rig = Rig({ _, _, _ -> HttpResult(200, answer(winner.replace("1.20", "1.2${n++}")), null) }, noSignals)
        val c = cfg()
        rig.runner.run(c, ban(c)) { rig.answers.add(it) }
        rig.runner.run(c, c.placements["int"]!!) { rig.answers.add(it) }
        rig.t += 10; rig.runIo()
        assertEquals(listOf("1.20", "1.21"), rig.answers.map { it["hb_pb"] })
        assertEquals(2, rig.events.size)
    }
}
