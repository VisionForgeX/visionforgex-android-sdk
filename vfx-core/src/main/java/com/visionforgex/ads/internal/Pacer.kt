package com.visionforgex.ads.internal

import java.util.Calendar
import java.util.TimeZone

/** A small key-value store (SharedPreferences in the app, a map in tests). */
internal interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getLong(key: String, def: Long): Long = getString(key)?.toLongOrNull() ?: def
    fun putLong(key: String, value: Long) = putString(key, value.toString())
}

internal class MapStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/**
 * Whether a full-screen ad may be shown now, by the placement's cap and the app's gap between
 * full-screen ads. What must survive a restart (when each placement last showed, its shows in the
 * last day, the last full-screen ad) is kept in the store; the session's counts are not.
 */
internal class Pacer(private val store: KeyValueStore, private val clock: () -> Long, private val tz: () -> TimeZone = { TimeZone.getDefault() }) {
    private var sessionStart = 0L
    private var sessionNumber = 0
    private val perSession = HashMap<String, Int>()
    private val eligibleCalls = HashMap<String, Int>()

    fun startSession(number: Int, at: Long) {
        sessionNumber = number
        sessionStart = at
        perSession.clear()
        eligibleCalls.clear()
    }

    /** Null when it may show; otherwise why not (PROTOCOL.md "capped" reasons). A pass counts as one eligible call for everyNth. */
    fun check(p: Placement, fullGapSec: Int): String? {
        val now = clock()
        val c = p.cap
        if (c.graceSessions > 0 && sessionNumber <= c.graceSessions) return "grace"
        if (c.graceSec > 0 && now - sessionStart < c.graceSec * 1000L) return "grace"
        if (!p.format.isRewarded && fullGapSec > 0 && now - store.getLong(LAST_FULL, 0L) < fullGapSec * 1000L) return "gap"
        if (c.minIntervalSec > 0 && now - store.getLong(LAST + p.name, 0L) < c.minIntervalSec * 1000L) return "interval"
        if (c.maxPerSession > 0 && (perSession[p.name] ?: 0) >= c.maxPerSession) return "session"
        if (c.maxPerHour > 0 || c.maxPerDay > 0) {
            val ts = times(p.name)
            if (c.maxPerHour > 0 && ts.count { it > now - 3_600_000L } >= c.maxPerHour) return "hour"
            if (c.maxPerDay > 0) { val day = dayOf(now); if (ts.count { dayOf(it) == day } >= c.maxPerDay) return "day" }
        }
        if (c.everyNth > 1) {
            val n = (eligibleCalls[p.name] ?: 0) + 1
            eligibleCalls[p.name] = n
            if (n % c.everyNth != 0) return "nth"
        }
        return null
    }

    fun recordShow(p: Placement) {
        val now = clock()
        store.putLong(LAST + p.name, now)
        if (!p.format.isRewarded && p.format.isFullScreen) store.putLong(LAST_FULL, now)
        perSession[p.name] = (perSession[p.name] ?: 0) + 1
        val ts = times(p.name).filter { it > now - 2 * 86_400_000L }.takeLast(500) + now
        store.putString(TIMES + p.name, ts.joinToString(","))
    }

    /** Debug panel: forget every count. */
    fun reset(names: Collection<String>) {
        store.putString(LAST_FULL, null)
        names.forEach { store.putString(LAST + it, null); store.putString(TIMES + it, null) }
        perSession.clear(); eligibleCalls.clear()
    }

    fun shownThisSession(name: String): Int = perSession[name] ?: 0
    fun lastShown(name: String): Long = store.getLong(LAST + name, 0L)

    private fun times(name: String): List<Long> = store.getString(TIMES + name)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    private fun dayOf(t: Long): Int { val c = Calendar.getInstance(tz()); c.timeInMillis = t; return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR) }

    companion object {
        const val LAST = "vfx.p.last."
        const val TIMES = "vfx.p.ts."
        const val LAST_FULL = "vfx.p.lastFull"
    }
}
