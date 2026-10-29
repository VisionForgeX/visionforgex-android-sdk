package com.visionforgex.ads.internal

import android.app.Activity
import android.content.Context
import com.visionforgex.ads.VfxShowListener
import com.visionforgex.ads.engine.AdEngine
import com.visionforgex.ads.engine.EngineFullScreenAd
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.engine.FullScreenListener
import com.visionforgex.ads.engine.FullScreenLoadCallback

/** What a slot needs from the SDK. Everything is called on the main thread. */
internal interface SlotEnv {
    val engine: AdEngine
    val pacer: Pacer
    fun now(): Long
    fun post(delayMs: Long, r: Runnable): Any
    fun cancel(token: Any)
    fun context(): Context
    fun config(): Config?
    /** Settings arrived, the app enabled, consent allows requests. */
    fun canRequest(): Boolean
    fun keyValues(placement: Placement): Map<String, String>
    fun event(type: String, p: Placement, fields: Map<String, Any?> = emptyMap())
    var fullScreenShowing: Boolean
    fun onFullScreenClosed()
}

/**
 * One full-screen placement: keeps one ad loaded (when the platform says preload), asks through
 * the floor tiers highest first, retries a failed load after a growing wait, throws away an ad
 * before Google's expiry, and shows only when the pacing allows.
 */
internal class FullScreenSlot(val name: String, private val env: SlotEnv) {
    private var ad: EngineFullScreenAd? = null
    private var loadedAt = 0L
    private var source: String? = null
    private var loading = false
    private var loadId = 0
    private var attempt = 0
    private var retryToken: Any? = null
    private var timeoutToken: Any? = null
    var lastError: String = ""
        private set
    var state: String = "idle"
        private set

    fun placement(): Placement? = env.config()?.placements?.get(name)

    fun isReady(): Boolean {
        val p = placement() ?: return false
        val a = ad ?: return false
        if (env.now() - loadedAt > p.expiryMin * 60_000L) { a.destroy(); ad = null; state = "expired"; return false }
        return true
    }

    /** Loads one, unless one is loaded, loading, or waiting to retry. [fresh]: a new start after giving up. */
    fun load(fresh: Boolean = false) {
        val p = placement() ?: return
        if (!p.on || !env.canRequest() || p.unit.isBlank()) return
        if (isReady() || loading) return
        if (retryToken != null && !fresh) return
        if (fresh) { retryToken?.let(env::cancel); retryToken = null; attempt = 0 }
        if (attempt > p.retry.maxAttempts) return
        loadTier(p, 0, ++loadId)
    }

    private fun loadTier(p: Placement, tier: Int, id: Int) {
        val tiers = p.floors.tiers
        val kv = HashMap(env.keyValues(p))
        if (tier < tiers.size) kv[p.floors.key] = tiers[tier]
        loading = true
        state = "loading"
        val started = env.now()
        val tierNo = if (tier < tiers.size) tier + 1 else 0   // 0: the request without a floor
        env.event("req", p, mapOf("tier" to tierNo))
        var answered = false
        if (tier < tiers.size) {   // a floored tier that is slow counts as no fill: the next, lower one is asked
            timeoutToken = env.post(p.floors.timeoutMs, Runnable {
                if (answered || id != loadId) return@Runnable
                answered = true
                env.event("nofill", p, mapOf("tier" to tierNo, "ms" to (env.now() - started), "err" to "timeout"))
                loadTier(p, tier + 1, id)
            })
        }
        env.engine.loadFullScreen(env.context(), p.format, p.unit, EngineRequest(kv), object : FullScreenLoadCallback {
            override fun onLoaded(ad: EngineFullScreenAd) { env.post(0, Runnable { loaded(ad) }) }
            override fun onFailed(code: Int, message: String) { env.post(0, Runnable { failed(code, message) }) }
            fun loaded(ad: EngineFullScreenAd) {
                if (answered || id != loadId) { ad.destroy(); return }   // too late: a lower tier was already asked
                answered = true
                timeoutToken?.let(env::cancel); timeoutToken = null
                loading = false
                attempt = 0
                this@FullScreenSlot.ad = ad
                loadedAt = env.now()
                source = ad.source
                state = "ready"
                lastError = ""
                env.event("fill", p, mapOf("tier" to tierNo, "ms" to (env.now() - started), "src" to ad.source))
            }
            fun failed(code: Int, message: String) {
                if (answered || id != loadId) return
                answered = true
                timeoutToken?.let(env::cancel); timeoutToken = null
                lastError = "$code $message"
                env.event("nofill", p, mapOf("tier" to tierNo, "ms" to (env.now() - started), "err" to code.toString()))
                if (tier < tiers.size) { loadTier(p, tier + 1, id); return }
                loading = false
                scheduleRetry(p)
            }
        })
    }

    private fun scheduleRetry(p: Placement) {
        attempt++
        if (attempt > p.retry.maxAttempts) { state = "gave up"; return }   // until the next show() or the app's next return
        val wait = backoffMs(attempt, p.retry)
        state = "retry in ${wait / 1000} s"
        retryToken = env.post(wait, Runnable { retryToken = null; load() })
    }

    fun show(activity: Activity?, listener: VfxShowListener?) {
        val p = placement()
        if (p == null) { listener?.onNotShown("unknown_placement"); return }
        val cfg = env.config()
        val notShown = { why: String -> if (why != "unknown_placement") env.event("capped", p, mapOf("why" to why)); listener?.onNotShown(why) }
        when {
            cfg == null || !cfg.enabled || !p.on -> { listener?.onNotShown("paused"); return }
            !env.canRequest() -> { listener?.onNotShown("consent"); return }
            env.fullScreenShowing -> { listener?.onNotShown("showing"); return }
            activity == null || activity.isFinishing -> { listener?.onNotShown("no_activity"); return }
        }
        env.pacer.check(p, cfg!!.fullGapSec)?.let { notShown(it); return }
        val a = ad
        if (a == null || !isReady()) {
            listener?.onNotShown("not_ready")
            load(fresh = state == "gave up")
            return
        }
        ad = null
        state = "showing"
        env.fullScreenShowing = true
        val src = source
        a.show(activity!!, OnMain(env, object : FullScreenListener {
            override fun onShown() {
                env.pacer.recordShow(p)
                env.event("show", p, mapOf("src" to src))
                listener?.onShown()
            }
            override fun onClicked() { env.event("click", p); listener?.onClicked() }
            override fun onDismissed() {
                env.fullScreenShowing = false
                env.onFullScreenClosed()
                env.event("close", p)
                state = "idle"
                a.destroy()
                listener?.onDismissed()
                if (p.preload) load(fresh = true)
            }
            override fun onFailedToShow(code: Int, message: String) {
                env.fullScreenShowing = false
                state = "idle"
                lastError = "$code $message"
                env.event("fail", p, mapOf("err" to code.toString()))
                a.destroy()
                listener?.onNotShown("failed")
                load(fresh = true)
            }
            override fun onReward(type: String, amount: Int) { env.event("reward", p, mapOf("amount" to amount)); listener?.onReward(type, amount) }
            override fun onPaid(valueMicros: Long, currency: String, precision: String) {
                env.event("paid", p, mapOf("v" to valueMicros, "cur" to currency, "prec" to precision, "src" to src))
            }
        }))
    }

    /** The settings changed or the SDK stops: forget the loaded ad when its placement changed. */
    fun reset() {
        loadId++
        retryToken?.let(env::cancel); retryToken = null
        timeoutToken?.let(env::cancel); timeoutToken = null
        loading = false; attempt = 0
        ad?.destroy(); ad = null
        state = "idle"
    }

    fun describe(): String = "$state${source?.let { " · $it" } ?: ""}${if (lastError.isNotEmpty()) " · last error $lastError" else ""}"

}

/** Moves an engine's show callbacks to the main thread. */
internal class OnMain(private val env: SlotEnv, private val l: FullScreenListener) : FullScreenListener {
    private fun run(f: () -> Unit) { env.post(0, Runnable(f)) }
    override fun onShown() = run { l.onShown() }
    override fun onImpression() = run { l.onImpression() }
    override fun onClicked() = run { l.onClicked() }
    override fun onDismissed() = run { l.onDismissed() }
    override fun onFailedToShow(code: Int, message: String) = run { l.onFailedToShow(code, message) }
    override fun onReward(type: String, amount: Int) = run { l.onReward(type, amount) }
    override fun onPaid(valueMicros: Long, currency: String, precision: String) = run { l.onPaid(valueMicros, currency, precision) }
}
