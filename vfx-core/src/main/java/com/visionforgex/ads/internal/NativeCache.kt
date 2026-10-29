package com.visionforgex.ads.internal

import com.visionforgex.ads.engine.EngineNativeAd
import com.visionforgex.ads.engine.EngineRequest
import com.visionforgex.ads.engine.NativeLoadCallback

/** One native ad waiting per placement (when the platform says preload), handed to the first view that asks. */
internal class NativeCache(private val env: SlotEnv) {
    private class Entry(val ad: EngineNativeAd, val at: Long)
    private val ready = HashMap<String, Entry>()
    private val loading = HashSet<String>()

    fun prefetch(p: Placement) {
        if (!p.on || p.unit.isBlank() || !env.canRequest() || p.name in loading) return
        take(p, peek = true)?.let { return }
        load(p) { ad -> if (ad != null) ready[p.name] = Entry(ad, env.now()) }
    }

    /** A fresh one if waiting (and [peek] false: taken, and the next one asked for). */
    fun take(p: Placement, peek: Boolean = false): EngineNativeAd? {
        val e = ready[p.name] ?: return null
        if (env.now() - e.at > p.expiryMin * 60_000L) { e.ad.destroy(); ready.remove(p.name); return null }
        if (peek) return e.ad
        ready.remove(p.name)
        if (p.preload) env.post(0, Runnable { prefetch(p) })
        return e.ad
    }

    fun load(p: Placement, done: (EngineNativeAd?) -> Unit) {
        loading.add(p.name)
        val started = env.now()
        env.event("req", p, mapOf("tier" to 0))
        env.engine.loadNative(env.context(), p.unit, EngineRequest(env.keyValues(p)), object : NativeLoadCallback {
            override fun onLoaded(ad: EngineNativeAd) { env.post(0, Runnable { loading.remove(p.name); env.event("fill", p, mapOf("tier" to 0, "ms" to env.now() - started, "src" to ad.source)); done(ad) }) }
            override fun onFailed(code: Int, message: String) { env.post(0, Runnable { loading.remove(p.name); env.event("nofill", p, mapOf("tier" to 0, "ms" to env.now() - started, "err" to code.toString())); done(null) }) }
        })
    }

    fun describe(name: String): String = when { ready.containsKey(name) -> "ready"; name in loading -> "loading"; else -> "idle" }
}
