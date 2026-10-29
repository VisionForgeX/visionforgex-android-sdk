package com.visionforgex.ads.internal

import com.visionforgex.ads.VfxFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigParserTest {
    private val sample = """
    {"v":1,"ttl":3600,"app":{"enabled":true,"test":false,"reason":""},"global":{"fullGapSec":30,"sessionTimeoutSec":1800,"log":"warn"},
     "consent":{"ump":true,"child":false,"underAge":true,"rating":"T"},
     "events":{"on":true,"url":"https://apps.visionforgex.com/sdk/v1/events.php","sample":0.5,"batch":20,"flushSec":30},
     "kv":{"vfx_app":"com.example.game","Bad Key":"x"},"ab":"e1:b",
     "placements":{
       "level_complete":{"format":"interstitial","on":true,"unit":"/1,2/u-int","preload":true,"expiryMin":55,"refreshSec":0,
          "cap":{"minIntervalSec":60,"maxPerSession":0,"maxPerHour":4,"maxPerDay":0,"graceSec":30,"graceSessions":0,"everyNth":2},
          "retry":{"maxAttempts":6,"baseSec":2,"maxSec":64},"floors":{"key":"vfx_fl","tiers":["300","150"],"timeoutMs":2500},"collapsible":"","template":"","autoShow":false},
       "home":{"format":"banner","on":true,"unit":"/1/u-ban","refreshSec":10,"collapsible":"bottom"},
       "open":{"format":"app_open","on":true,"unit":"/1/u-ao","expiryMin":600,"autoShow":true},
       "weird":{"format":"video","on":true,"unit":"/1/x"},
       "Bad Name":{"format":"banner","on":true,"unit":"/1/x"}
     }}"""

    @Test fun readsTheProtocolSample() {
        val c = ConfigParser.parse(sample)!!
        assertTrue(c.enabled); assertTrue(c.ump); assertTrue(c.underAge); assertEquals("T", c.rating)
        assertEquals(0.5, c.sample, 0.0); assertEquals("e1:b", c.ab)
        assertEquals(mapOf("vfx_app" to "com.example.game"), c.kv)
        val p = c.placements["level_complete"]!!
        assertEquals(VfxFormat.INTERSTITIAL, p.format)
        assertEquals(listOf("300", "150"), p.floors.tiers)
        assertEquals(60, p.cap.minIntervalSec); assertEquals(4, p.cap.maxPerHour); assertEquals(2, p.cap.everyNth); assertEquals(30, p.cap.graceSec)
        assertEquals(setOf("level_complete", "home", "open"), c.placements.keys)   // an unknown format and a bad name are left out
    }

    @Test fun keepsGooglesLimitsWhateverTheServerSays() {
        val c = ConfigParser.parse(sample)!!
        assertEquals("a banner never refreshes faster than 30 s", 30, c.placements["home"]!!.refreshSec)
        assertEquals("bottom", c.placements["home"]!!.collapsible)
        assertEquals("an app open ad is thrown away before Google's 4 hours", 239, c.placements["open"]!!.expiryMin)
        assertTrue(c.placements["open"]!!.autoShow)
        val c2 = ConfigParser.parse(sample.replace("\"expiryMin\":55", "\"expiryMin\":500"))!!
        assertEquals("other ads before Google's hour", 59, c2.placements["level_complete"]!!.expiryMin)
    }

    @Test fun refusesWhatItDoesNotUnderstand() {
        assertNull(ConfigParser.parse("not json"))
        assertNull(ConfigParser.parse("{\"v\":2}"))
        assertFalse(ConfigParser.parse("{\"v\":1,\"events\":{\"on\":true,\"url\":\"ftp://x\"}}")!!.eventsOn)
        assertEquals("", ConfigParser.parse("{\"v\":1,\"ab\":\"drop table\"}")!!.ab)
        assertFalse("off unless the platform says on", ConfigParser.parse("{\"v\":1}")!!.enabled)
    }

    @Test fun backoffDoublesUpToItsCeiling() {
        val r = Retry(6, 2, 64)
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 32000L, 64000L, 64000L), (1..7).map { backoffMs(it, r) })
    }

    @Test fun theHashIsThePlatformsOwn() {
        // the same test vectors as the platform's va_sdk_hash (tests/slice6.php)
        assertEquals(0x811c9dc5L, fnv1a(""))
        assertEquals(0xe40c292cL, fnv1a("a"))
        assertEquals(0xbf9cf968L, fnv1a("foobar"))
    }
}
