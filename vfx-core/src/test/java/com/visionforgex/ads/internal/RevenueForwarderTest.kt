package com.visionforgex.ads.internal

import com.adjust.sdk.Adjust
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.visionforgex.ads.VfxFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Forwarding Google's paid events to AppsFlyer and Adjust, against stand-ins with their classes' names and signatures. */
@RunWith(RobolectricTestRunner::class)
class RevenueForwarderTest {
    @Before fun clear() { AppsFlyerLib.logged.clear(); AppsFlyerLib.extras.clear(); Adjust.tracked.clear() }

    @Test fun nothingUnlessThePlatformSaysSo() {
        val f = RevenueForwarder()
        assertEquals(emptyList<String>(), f.forward(Mmp(), "home", VfxFormat.BANNER, "/1/u", 4210, "USD", null))
        assertTrue(AppsFlyerLib.logged.isEmpty() && Adjust.tracked.isEmpty())
        assertEquals("ConfigParser: absent means off", Mmp(), ConfigParser.parse("{\"v\":1}")!!.mmp)
        assertEquals(Mmp(appsflyer = true, adjust = false), ConfigParser.parse("{\"v\":1,\"mmp\":{\"appsflyer\":true}}")!!.mmp)
    }

    @Test fun appsFlyerGetsEachImpressionInUnitsWithItsNetworkUnitAndPlacement() {
        val sent = RevenueForwarder().forward(Mmp(appsflyer = true), "level_complete", VfxFormat.INTERSTITIAL, "/1,2/u-int", 4210, "USD", "AppLovin")
        assertEquals(listOf("AppsFlyer"), sent)
        val d = AppsFlyerLib.logged.single()
        assertEquals("applovin", d.monetizationNetwork)
        assertEquals(MediationNetwork.GOOGLE_ADMOB, d.mediationNetwork)
        assertEquals("USD", d.currency)
        assertEquals(0.00421, d.revenue, 1e-12)
        assertEquals(mapOf("ad_unit" to "/1,2/u-int", "ad_type" to "interstitial", "placement" to "level_complete"), AppsFlyerLib.extras.single())
    }

    @Test fun adjustGetsItAsAdMobRevenue() {
        val sent = RevenueForwarder().forward(Mmp(adjust = true), "home", VfxFormat.BANNER, "/1/u-ban", 900, "EUR", null)
        assertEquals(listOf("Adjust"), sent)
        val r = Adjust.tracked.single()
        assertEquals("admob_sdk", r.source); assertEquals(0.0009, r.revenue!!, 1e-12); assertEquals("EUR", r.currency)
        assertEquals("/1/u-ban", r.unit); assertEquals("home", r.placement)
    }

    @Test fun bothAtOnce_andNeverAWorthlessOrUnreadableOne() {
        val f = RevenueForwarder()
        assertEquals(listOf("AppsFlyer", "Adjust"), f.forward(Mmp(true, true), "home", VfxFormat.BANNER, "/1/u", 1, "USD", "Google AdMob Network"))
        assertEquals("googleadmob", AppsFlyerLib.logged.single().monetizationNetwork)
        assertEquals(emptyList<String>(), f.forward(Mmp(true, true), "home", VfxFormat.BANNER, "/1/u", 0, "USD", null))
        assertEquals(emptyList<String>(), f.forward(Mmp(true, true), "home", VfxFormat.BANNER, "/1/u", 5, "usd", null))
        assertEquals(1, AppsFlyerLib.logged.size); assertEquals(1, Adjust.tracked.size)
    }

    @Test fun aToolNotInTheAppIsLeftAlone_neverThrown() {
        val none = RevenueForwarder(find = { null })
        assertEquals(emptyList<String>(), none.forward(Mmp(true, true), "home", VfxFormat.BANNER, "/1/u", 4210, "USD", null))
        // AppsFlyer older than 6.15 (no AFAdRevenueData): skipped, Adjust still sent
        val old = RevenueForwarder(find = { n -> if (n == "com.appsflyer.AFAdRevenueData") null else Class.forName(n) })
        assertEquals(listOf("Adjust"), old.forward(Mmp(true, true), "home", VfxFormat.BANNER, "/1/u", 4210, "USD", null))
        // a class of that name with another shape: said, not thrown
        val odd = RevenueForwarder(find = { n -> if (n == "com.appsflyer.AppsFlyerLib") String::class.java else Class.forName(n) })
        assertEquals(emptyList<String>(), odd.forward(Mmp(appsflyer = true), "home", VfxFormat.BANNER, "/1/u", 4210, "USD", null))
    }

    @Test fun networkNames() {
        assertEquals("googleadmob", RevenueForwarder.monetizationNetwork(null))
        assertEquals("googleadmob", RevenueForwarder.monetizationNetwork("Google Ad Manager"))
        assertEquals("unityads", RevenueForwarder.monetizationNetwork("Unity Ads"))
        assertEquals("liftoffmonetize", RevenueForwarder.monetizationNetwork("Liftoff Monetize"))
    }
}
