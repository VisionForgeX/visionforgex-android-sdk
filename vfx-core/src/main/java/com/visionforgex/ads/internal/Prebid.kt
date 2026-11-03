package com.visionforgex.ads.internal

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Header bidding through a Prebid Server (PROTOCOL.md, "prebid"). The SDK decides nothing here: where to ask, with which stored
 * request and imp, how long to wait and when to stop asking after failures all come from the settings. What it does by itself is
 * what the law and plain safety need: it never asks without the consent signals the app already holds, and a failing server
 * costs an app at most a few waits (the breaker).
 *
 * What goes out: the stored ids, the device's kind, system, language and screen, the consent strings Google's consent form
 * left in the app's preferences (IAB TCF, US Privacy, GPP), and a random ID for the auction. No advertising ID, no location.
 * What comes back that is used: the winning bid's targeting keys (hb_pb, hb_bidder, hb_cache_id …), added to the Google request.
 */
internal object Prebid {
    /** The consent signals the app holds in its preferences (the IAB's standard keys, written by Google's consent form). */
    class Signals(val gdprApplies: Boolean?, val tcString: String, val usPrivacy: String, val gpp: String, val gppSid: String)

    /** What the device says about itself. */
    class Device(val os: String, val osv: String, val make: String, val model: String, val tablet: Boolean, val language: String, val w: Int, val h: Int, val pxratio: Double, val ua: String)

    /** The outcome of one auction. [result]: bid, nobid, timeout, error, skip. */
    class Result(val result: String, val keys: Map<String, String>, val valueMicros: Long, val ms: Long) {
        companion object { fun skip() = Result("skip", emptyMap(), 0, 0) }
    }

    /** Why not to ask at all, or null. GDPR applies and no consent string is held: the signal Prebid Server needs is missing. */
    fun mustSkip(s: Signals): String? = if (s.gdprApplies == true && s.tcString.isBlank()) "consent" else null

    fun request(settings: PrebidSettings, imp: String, appVersion: String, sdkVersion: String, d: Device, s: Signals, auctionId: String = UUID.randomUUID().toString()): String {
        val device = JSONObject().put("os", d.os).put("osv", d.osv).put("make", d.make).put("model", d.model).put("devicetype", if (d.tablet) 5 else 4)
            .put("language", d.language).put("w", d.w).put("h", d.h).put("pxratio", d.pxratio).put("ua", d.ua)
        val regsExt = JSONObject()
        if (s.gdprApplies != null) regsExt.put("gdpr", if (s.gdprApplies) 1 else 0)
        if (s.usPrivacy.isNotBlank()) regsExt.put("us_privacy", s.usPrivacy)
        val regs = JSONObject().put("ext", regsExt)
        if (s.gpp.isNotBlank()) { regs.put("gpp", s.gpp); gppSids(s.gppSid)?.let { regs.put("gpp_sid", it) } }
        val body = JSONObject().put("id", auctionId)
            .put("imp", JSONArray().put(JSONObject().put("id", "1").put("displaymanager", "VisionForgeX").put("displaymanagerver", sdkVersion)
                .put("ext", JSONObject().put("prebid", JSONObject().put("storedrequest", JSONObject().put("id", imp))))))
            .put("app", JSONObject().put("ver", appVersion))
            .put("device", device).put("regs", regs)
            .put("ext", JSONObject().put("prebid", JSONObject().put("storedrequest", JSONObject().put("id", settings.request))))
        if (s.tcString.isNotBlank()) body.put("user", JSONObject().put("ext", JSONObject().put("consent", s.tcString)))
        return body.toString()
    }

    private fun gppSids(text: String): JSONArray? {
        val ids = text.split(',', '_').mapNotNull { it.trim().toIntOrNull() }
        return if (ids.isEmpty()) null else JSONArray(ids)
    }

    private val KEY = Regex("^hb_[a-z0-9_]{1,30}$")
    private val VALUE = Regex("^[A-Za-z0-9._:/=+-]{1,100}$")

    /** The result of an answer: HTTP 204 or no winner is "nobid"; anything that is not a Prebid Server answer is "error". */
    fun response(code: Int, body: String, ms: Long): Result {
        if (code == 204) return Result("nobid", emptyMap(), 0, ms)
        if (code != 200) return Result("error", emptyMap(), 0, ms)
        val o = try { JSONObject(body) } catch (e: Exception) { return Result("error", emptyMap(), 0, ms) }
        val seats = o.optJSONArray("seatbid") ?: return Result("nobid", emptyMap(), 0, ms)
        for (i in 0 until seats.length()) {
            val bids = seats.optJSONObject(i)?.optJSONArray("bid") ?: continue
            for (j in 0 until bids.length()) {
                val b = bids.optJSONObject(j) ?: continue
                val t = b.optJSONObject("ext")?.optJSONObject("prebid")?.optJSONObject("targeting") ?: continue   // only the winner carries the keys
                val keys = LinkedHashMap<String, String>()
                t.keys().forEach { k -> if (KEY.matches(k)) t.optString(k).takeIf { VALUE.matches(it) }?.let { keys[k] = it } }
                if (keys["hb_pb"] == null) continue   // without a price bucket no line item can match: not a usable bid
                val micros = (b.optDouble("price", 0.0).coerceIn(0.0, 100.0) * 1_000_000).toLong()
                return Result("bid", keys, micros, ms)
            }
        }
        return Result("nobid", emptyMap(), 0, ms)
    }

    /** After [failures] failures in a row (a timeout or an error) the server is left alone for [pauseSec]; any answer ends the run. */
    class Breaker {
        private var run = 0
        private var pausedUntil = 0L
        fun open(now: Long): Boolean = now < pausedUntil
        fun record(r: Result, now: Long, settings: PrebidSettings) {
            if (r.result == "timeout" || r.result == "error") { if (++run >= settings.breakerFailures) { pausedUntil = now + settings.breakerPauseSec * 1000L; run = 0 } }
            else if (r.result != "skip") run = 0
        }
    }
}

/**
 * One auction at a time per call, on the main thread: [run] answers exactly once, with the winning bid's targeting keys or with
 * nothing, and never later than the settings' wait (plus a short grace). Everything it needs from the SDK is passed in, so it
 * is tested with a clock that moves only when the test says.
 */
internal class PrebidRunner(
    private val post: (Long, Runnable) -> Any,
    private val cancel: (Any) -> Unit,
    private val now: () -> Long,
    private val io: (Runnable) -> Unit,
    private val send: (url: String, body: String, timeoutMs: Int) -> HttpResult,
    private val device: () -> Prebid.Device,
    private val signals: () -> Prebid.Signals,
    private val appVersion: () -> String,
    private val sdkVersion: String,
    private val event: (Placement, Prebid.Result, String) -> Unit,
) {
    val breaker = Prebid.Breaker()

    fun run(cfg: Config, p: Placement, done: (Map<String, String>) -> Unit) {
        val s = cfg.prebid
        // not bid for: not an event, nothing changes. (The platform never sends the block for these apps; this is a second lock.)
        if (s == null || p.prebidImp.isEmpty() || cfg.test || cfg.child || cfg.underAge) { done(emptyMap()); return }
        val sig = signals()
        val why = Prebid.mustSkip(sig) ?: if (breaker.open(now())) "breaker" else null
        if (why != null) { event(p, Prebid.Result.skip(), why); done(emptyMap()); return }
        val started = now()
        var answered = false
        val body = Prebid.request(s, p.prebidImp, appVersion(), sdkVersion, device(), sig)
        fun finish(r: Prebid.Result) {
            breaker.record(r, now(), s)
            event(p, r, "")
            done(if (r.result == "bid") r.keys else emptyMap())
        }
        val timer = post(s.timeoutMs + 100, Runnable {
            if (answered) return@Runnable
            answered = true
            finish(Prebid.Result("timeout", emptyMap(), 0, now() - started))
        })
        io(Runnable {
            val r = send(s.url, body, s.timeoutMs.toInt())
            post(0, Runnable {
                if (answered) return@Runnable   // too late: the app was already told "no bids"
                answered = true
                cancel(timer)
                val ms = now() - started
                finish(if (r.code == 0) Prebid.Result(if (ms >= s.timeoutMs - 50) "timeout" else "error", emptyMap(), 0, ms) else Prebid.response(r.code, r.body, ms))
            })
        })
    }
}
