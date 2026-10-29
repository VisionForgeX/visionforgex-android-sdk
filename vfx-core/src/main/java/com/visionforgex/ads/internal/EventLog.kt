package com.visionforgex.ads.internal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Events (PROTOCOL.md section 2): kept in memory and in a file, so a crash or a closed app loses
 * none, and sent in batches. "ses" and "paid" are always sent; the rest only when the install is in
 * the sample. A 4xx answer drops the batch (it would fail again); no answer or a 5xx keeps it.
 */
internal class EventLog(
    private val dir: File?,
    private val clock: () -> Long,
    private val io: (Runnable) -> Unit,
    private val send: (url: String, json: String) -> HttpResult = { u, j -> Http.postJson(u, j) },
) {
    class Header(val key: String, val install: String, val session: String, val sdk: String, val appVersion: String, val os: String, val device: String, val country: String, val ab: String, val sample: Double)

    private val lock = Any()
    private val pending = ArrayList<JSONObject>()
    private var sending = false
    @Volatile var inSample = true
    /** The last events, newest first, for the debug panel. */
    val recent = ArrayDeque<String>()

    private val file: File? get() = dir?.let { File(it, "events.jsonl") }

    init {
        // what a previous run did not send
        try { file?.takeIf { it.exists() }?.readLines()?.forEach { l -> runCatching { pending.add(JSONObject(l)) } } } catch (_: Exception) {}
    }

    fun add(type: String, fields: Map<String, Any?>) {
        if (!inSample && type != "ses" && type != "paid") return
        val e = JSONObject().put("t", type).put("ts", clock())
        for ((k, v) in fields) if (v != null) e.put(k, v)
        synchronized(lock) {
            pending.add(e)
            if (pending.size > MAX_KEPT) pending.subList(0, pending.size - MAX_KEPT).clear()
            recent.addFirst(e.toString()); while (recent.size > 40) recent.removeLast()
        }
        io(Runnable { persist() })
    }

    fun size(): Int = synchronized(lock) { pending.size }

    /** Sends what is waiting, in batches of at most 500. [done] gets how many were accepted. */
    fun flush(url: String, header: Header, done: ((Int) -> Unit)? = null) {
        val batch: List<JSONObject>
        synchronized(lock) {
            if (sending || pending.isEmpty()) { done?.invoke(0); return }
            sending = true
            batch = ArrayList(pending.take(500))
        }
        io(Runnable {
            var accepted = 0
            try {
                val body = JSONObject().put("k", header.key).put("i", header.install).put("s", header.session).put("sv", header.sdk).put("p", "android")
                    .put("av", header.appVersion).put("os", header.os).put("dev", header.device).put("c", header.country).put("ab", header.ab).put("sr", header.sample)
                    .put("e", JSONArray(batch))
                val r = send(url, body.toString())
                if (r.code in 200..499) {
                    synchronized(lock) { pending.removeAll(batch.toSet()) }
                    if (r.code < 300) accepted = batch.size else VLog.w("Events refused (${r.code}): ${r.body.take(200)}")
                    persist()
                }
            } finally {
                synchronized(lock) { sending = false }
                done?.invoke(accepted)
            }
        })
    }

    private fun persist() {
        val f = file ?: return
        val lines = synchronized(lock) { pending.map { it.toString() } }
        try { f.parentFile?.mkdirs(); val tmp = File(f.path + ".tmp"); tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")); tmp.renameTo(f) } catch (_: Exception) {}
    }

    companion object { const val MAX_KEPT = 2000 }
}
