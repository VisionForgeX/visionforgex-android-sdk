package com.visionforgex.ads.internal

import com.visionforgex.ads.VfxFormat
import org.json.JSONObject

/** Full-screen pacing (PROTOCOL.md: cap). */
internal data class Cap(
    val minIntervalSec: Int = 0,
    val maxPerSession: Int = 0,
    val maxPerHour: Int = 0,
    val maxPerDay: Int = 0,
    val graceSec: Int = 0,
    val graceSessions: Int = 0,
    val everyNth: Int = 1,
)

internal data class Retry(val maxAttempts: Int = 6, val baseSec: Int = 2, val maxSec: Int = 64)

internal data class Floors(val key: String = "vfx_fl", val tiers: List<String> = emptyList(), val timeoutMs: Long = 2500)

internal data class Placement(
    val name: String,
    val format: VfxFormat,
    val on: Boolean,
    val unit: String,
    val preload: Boolean,
    val expiryMin: Int,
    val refreshSec: Int,
    val cap: Cap,
    val retry: Retry,
    val floors: Floors,
    val collapsible: String,
    val template: String,
    val autoShow: Boolean,
)

/** The settings from the platform (PROTOCOL.md section 1), checked and kept within safe bounds. */
internal data class Config(
    val ttlSec: Int,
    val enabled: Boolean,
    val test: Boolean,
    val reason: String,
    val fullGapSec: Int,
    val sessionTimeoutSec: Int,
    val log: String,
    val ump: Boolean,
    val child: Boolean,
    val underAge: Boolean,
    val rating: String,
    val eventsOn: Boolean,
    val eventsUrl: String,
    val sample: Double,
    val batch: Int,
    val flushSec: Int,
    val kv: Map<String, String>,
    val ab: String,
    val placements: Map<String, Placement>,
)

internal object ConfigParser {
    private val NAME = Regex("^[a-z0-9_]{1,40}$")
    private val KV = Regex("^[a-z0-9_]{1,20}$")

    /** The settings, or null when the text is not settings this SDK understands. */
    fun parse(text: String): Config? {
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        if (o.optInt("v", 0) != 1) return null
        val app = o.optJSONObject("app") ?: JSONObject()
        val global = o.optJSONObject("global") ?: JSONObject()
        val consent = o.optJSONObject("consent") ?: JSONObject()
        val events = o.optJSONObject("events") ?: JSONObject()
        val kv = LinkedHashMap<String, String>()
        o.optJSONObject("kv")?.let { k -> k.keys().forEach { key -> if (KV.matches(key)) kv[key] = k.optString(key).take(40) } }
        val pls = LinkedHashMap<String, Placement>()
        val pj = o.optJSONObject("placements")
        pj?.keys()?.forEach { name ->
            val p = pj.optJSONObject(name) ?: return@forEach
            val f = VfxFormat.fromWire(p.optString("format")) ?: return@forEach
            if (!NAME.matches(name)) return@forEach
            pls[name] = placement(name, f, p)
        }
        val url = events.optString("url")
        return Config(
            ttlSec = o.optInt("ttl", 3600).coerceIn(60, 86400),
            enabled = app.optBoolean("enabled", false),
            test = app.optBoolean("test", false),
            reason = app.optString("reason"),
            fullGapSec = global.optInt("fullGapSec", 30).coerceIn(0, 3600),
            sessionTimeoutSec = global.optInt("sessionTimeoutSec", 1800).coerceIn(30, 86400),
            log = global.optString("log", "warn"),
            ump = consent.optBoolean("ump", true),
            child = consent.optBoolean("child", false),
            underAge = consent.optBoolean("underAge", false),
            rating = consent.optString("rating").let { if (it in setOf("G", "PG", "T", "MA")) it else "" },
            eventsOn = events.optBoolean("on", false) && (url.startsWith("https://") || url.startsWith("http://")),
            eventsUrl = url,
            sample = events.optDouble("sample", 1.0).let { if (it.isNaN()) 1.0 else it.coerceIn(0.0, 1.0) },
            batch = events.optInt("batch", 20).coerceIn(1, 500),
            flushSec = events.optInt("flushSec", 30).coerceIn(5, 3600),
            kv = kv,
            ab = o.optString("ab").let { if (Regex("^e\\d{1,4}:[ab]$").matches(it)) it else "" },
            placements = pls,
        )
    }

    private fun placement(name: String, f: VfxFormat, p: JSONObject): Placement {
        val c = p.optJSONObject("cap") ?: JSONObject()
        val r = p.optJSONObject("retry") ?: JSONObject()
        val fl = p.optJSONObject("floors") ?: JSONObject()
        val tiers = ArrayList<String>()
        fl.optJSONArray("tiers")?.let { a -> for (i in 0 until minOf(a.length(), 5)) a.optString(i).takeIf { Regex("^[0-9a-z_]{1,10}$").matches(it) }?.let(tiers::add) }
        val refresh = if (f == VfxFormat.BANNER) p.optInt("refreshSec", 0).let { if (it <= 0) 0 else it.coerceIn(30, 3600) } else 0   // never faster than Google allows
        val maxExpiry = if (f == VfxFormat.APP_OPEN) 239 else 59
        return Placement(
            name = name, format = f, on = p.optBoolean("on", false), unit = p.optString("unit"),
            preload = p.optBoolean("preload", f != VfxFormat.BANNER),
            expiryMin = p.optInt("expiryMin", maxExpiry).coerceIn(1, maxExpiry),
            refreshSec = refresh,
            cap = Cap(
                minIntervalSec = c.optInt("minIntervalSec", 0).coerceIn(0, 86400),
                maxPerSession = c.optInt("maxPerSession", 0).coerceIn(0, 1000),
                maxPerHour = c.optInt("maxPerHour", 0).coerceIn(0, 1000),
                maxPerDay = c.optInt("maxPerDay", 0).coerceIn(0, 10000),
                graceSec = c.optInt("graceSec", 0).coerceIn(0, 3600),
                graceSessions = c.optInt("graceSessions", 0).coerceIn(0, 100),
                everyNth = c.optInt("everyNth", 1).coerceIn(1, 100),
            ),
            retry = Retry(r.optInt("maxAttempts", 6).coerceIn(0, 20), r.optInt("baseSec", 2).coerceIn(1, 60), r.optInt("maxSec", 64).coerceIn(1, 3600)),
            floors = Floors(fl.optString("key", "vfx_fl").ifBlank { "vfx_fl" }, tiers, fl.optLong("timeoutMs", 2500).coerceIn(500, 10000)),
            collapsible = p.optString("collapsible").let { if (it == "top" || it == "bottom") it else "" },
            template = p.optString("template").let { if (it == "small") "small" else "medium" },
            autoShow = f == VfxFormat.APP_OPEN && p.optBoolean("autoShow", false),
        )
    }
}

/** The wait before retry number [attempt] (1 = the first retry). */
internal fun backoffMs(attempt: Int, r: Retry): Long {
    val exp = (r.baseSec.toLong() shl (attempt - 1).coerceIn(0, 20))
    return minOf(r.maxSec.toLong(), exp) * 1000L
}

/** FNV-1a, 32 bits: the same as the platform's va_sdk_hash. */
internal fun fnv1a(s: String): Long {
    var h = 0x811c9dc5L
    for (b in s.toByteArray(Charsets.UTF_8)) { h = h xor (b.toLong() and 0xff); h = (h * 0x01000193L) and 0xffffffffL }
    return h
}
