package com.visionforgex.ads.internal

import com.visionforgex.ads.VfxFormat

/** Which attribution tools get each impression's revenue (PROTOCOL.md, "mmp"); both off unless the platform says so. */
internal data class Mmp(val appsflyer: Boolean = false, val adjust: Boolean = false) {
    val any: Boolean get() = appsflyer || adjust
}

/**
 * Sends Google's paid event (what one impression earned) to the attribution tool the app already runs: AppsFlyer
 * (6.15 or newer: `logAdRevenue`) and/or Adjust (`trackAdRevenue`, source `admob_sdk`). Whether to is the platform's
 * setting; this only carries it out.
 *
 * By reflection: VisionForgeX depends on neither, and an app without one is unchanged. The app starts the tool itself,
 * as its own guide says; VisionForgeX never starts or configures it. A tool switched on but not in the app is said
 * once in the log, never thrown.
 */
internal class RevenueForwarder(private val find: (String) -> Class<*>? = ::findClass) {
    private val said = HashSet<String>()

    /** The tools the revenue reached (empty when none was on, or none is in the app). */
    fun forward(mmp: Mmp, placement: String, format: VfxFormat, unit: String, micros: Long, currency: String, source: String?): List<String> {
        if (!mmp.any || micros <= 0 || !CURRENCY.matches(currency)) return emptyList()
        val value = micros / 1_000_000.0
        val out = ArrayList<String>(2)
        if (mmp.appsflyer) attempt("AppsFlyer", "AppsFlyer 6.15 or newer") { appsFlyer(placement, format, unit, value, currency, source) }?.let(out::add)
        if (mmp.adjust) attempt("Adjust", "Adjust") { adjust(placement, unit, value, currency) }?.let(out::add)
        return out
    }

    /** Runs one tool's call; its name when it went, null when the tool is missing or refused (said once). */
    private fun attempt(name: String, what: String, call: () -> Boolean): String? {
        val ok = try { call() } catch (t: Throwable) {
            if (said.add("$name-error")) VLog.w("Ad revenue could not be sent to $name: ${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}")
            return null
        }
        if (!ok) { if (said.add("$name-missing")) VLog.w("The platform sends ad revenue to $name, but $what is not in the app: nothing is sent to it."); return null }
        if (said.add("$name-ok")) VLog.i("Ad revenue goes to $name too")
        return name
    }

    private fun appsFlyer(placement: String, format: VfxFormat, unit: String, value: Double, currency: String, source: String?): Boolean {
        val dataClass = find("com.appsflyer.AFAdRevenueData") ?: return false
        val netClass = find("com.appsflyer.MediationNetwork") ?: return false
        val lib = find("com.appsflyer.AppsFlyerLib") ?: return false
        val admob = netClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "GOOGLE_ADMOB" } ?: return false
        val data = dataClass.getConstructor(String::class.java, netClass, String::class.java, Double::class.javaPrimitiveType)
            .newInstance(monetizationNetwork(source), admob, currency, value)
        val scheme = find("com.appsflyer.AdRevenueScheme")
        fun key(field: String, fallback: String): String = (try { scheme?.getField(field)?.get(null) as? String } catch (e: Exception) { null }) ?: fallback
        val extra: Map<String, Any> = linkedMapOf(key("AD_UNIT", "ad_unit") to unit, key("AD_TYPE", "ad_type") to format.wire, key("PLACEMENT", "placement") to placement)
        val instance = lib.getMethod("getInstance").invoke(null)
        lib.getMethod("logAdRevenue", dataClass, Map::class.java).invoke(instance, data, extra)
        return true
    }

    private fun adjust(placement: String, unit: String, value: Double, currency: String): Boolean {
        val revClass = find("com.adjust.sdk.AdjustAdRevenue") ?: return false
        val adjust = find("com.adjust.sdk.Adjust") ?: return false
        val rev = revClass.getConstructor(String::class.java).newInstance("admob_sdk")
        val set = try { revClass.getMethod("setRevenue", Double::class.javaObjectType, String::class.java) } catch (e: NoSuchMethodException) { revClass.getMethod("setRevenue", Double::class.javaPrimitiveType, String::class.java) }
        set.invoke(rev, value, currency)
        for ((m, v) in listOf("setAdRevenueUnit" to unit, "setAdRevenuePlacement" to placement)) try { revClass.getMethod(m, String::class.java).invoke(rev, v) } catch (e: NoSuchMethodException) { }
        adjust.getMethod("trackAdRevenue", revClass).invoke(null, rev)
        return true
    }

    companion object {
        private val CURRENCY = Regex("^[A-Z]{3}$")

        /** The network that served the ad, as AppsFlyer names networks: Google's own is "googleadmob"; a partner, its name in lower case. */
        fun monetizationNetwork(source: String?): String {
            val s = source?.trim().orEmpty()
            if (s.isEmpty() || Regex("google|admob|ad ?manager|ad ?exchange", RegexOption.IGNORE_CASE).containsMatchIn(s)) return "googleadmob"
            return s.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "googleadmob" }.take(40)
        }

        private fun findClass(name: String): Class<*>? = try { Class.forName(name) } catch (e: ClassNotFoundException) { null } catch (e: LinkageError) { null }
    }
}
