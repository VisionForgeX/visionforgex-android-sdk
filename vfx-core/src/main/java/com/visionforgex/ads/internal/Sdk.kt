package com.visionforgex.ads.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.visionforgex.ads.BuildConfig
import com.visionforgex.ads.VfxFormat
import com.visionforgex.ads.VfxInitListener
import com.visionforgex.ads.VfxLogLevel
import com.visionforgex.ads.VfxOptions
import com.visionforgex.ads.VfxStatus
import com.visionforgex.ads.engine.AdEngine
import com.visionforgex.ads.engine.ConsentDebug
import com.visionforgex.ads.engine.EngineSettings
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

internal class PrefsStore(private val p: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = p.getString(key, null)
    override fun putString(key: String, value: String?) { p.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

/**
 * The SDK's state, on the main thread: the settings (cached, refreshed when the app comes back
 * after their time is up), consent, the session, the slots, the events.
 */
internal class Sdk(
    val app: Application,
    val key: String,
    val options: VfxOptions,
    override val engine: AdEngine,
    private val store: KeyValueStore = PrefsStore(app.getSharedPreferences("visionforgex", Context.MODE_PRIVATE)),
    private val clock: () -> Long = System::currentTimeMillis,
) : SlotEnv {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "visionforgex-io").apply { isDaemon = true } }
    override val pacer = Pacer(store, clock)
    val slots = HashMap<String, FullScreenSlot>()
    val nativeCache = NativeCache(this)
    val events: EventLog
    val installId: String
    var sessionId: String = ""; private set
    var sessionNumber: Int = 0; private set
    private var config: Config? = null
    var configFrom: String = "none"; private set
    var configAt: Long = 0; private set
    private var engineStarted = false
    private var engineReady = false
    private var consentDone = false
    private var consentAsked = false
    var consentError: String? = null; private set
    private var started = 0
    private var backgroundAt = 0L
    private var resumed: WeakReference<Activity>? = null
    private var listeners = ArrayList<VfxInitListener>()
    private var readyAnnounced = false
    private var flushToken: Any? = null
    private var skipNextAppOpen = false
    private var coldStartAt = clock()
    private val appKv = LinkedHashMap<String, String>()
    override var fullScreenShowing = false

    init {
        VLog.level = options.logLevel ?: VfxLogLevel.WARN
        Http.userAgent = "VisionForgeX-Android/${BuildConfig.VFX_VERSION} (Android ${Build.VERSION.RELEASE}; ${app.packageName})"
        installId = store.getString(INSTALL) ?: UUID.randomUUID().toString().also { store.putString(INSTALL, it) }
        events = EventLog(File(app.noBackupFilesDir, "visionforgex"), clock, { r -> io.execute(r) })
        events.inSample = true
        if (options.settingsForTesting == null) store.getString(CONFIG)?.let { ConfigParser.parse(it) }?.let { config = it; configFrom = "cache"; configAt = store.getLong(CONFIG_AT, 0) }
        config?.let(::applyLog)
        app.registerActivityLifecycleCallbacks(Tracker())
    }

    fun addListener(l: VfxInitListener?) {
        if (l == null) return
        if (readyAnnounced) l.onReady(status()) else listeners.add(l)
    }

    // ---------------------------------------------------------------- the settings

    private var fetching = false
    fun fetchConfig(reason: String) {
        if (fetching || options.settingsForTesting != null) return
        fetching = true
        val av = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        val url = options.baseUrl + "/sdk/v1/config.php?k=" + enc(key) + "&i=" + enc(installId) + "&sv=" + enc(BuildConfig.VFX_VERSION) + "&p=android&b=" + enc(app.packageName) + "&av=" + enc(av)
        val etag = store.getString(CONFIG_ETAG).takeIf { config != null }
        VLog.d("Settings: asking ($reason)")
        io.execute {
            val r = Http.get(url, etag)
            main.post {
                fetching = false
                when {
                    r.code == 304 -> { configAt = clock(); store.putLong(CONFIG_AT, configAt); configFrom = "platform (unchanged)" }
                    r.code == 200 -> {
                        val c = ConfigParser.parse(r.body)
                        if (c == null) VLog.w("Settings: not understood (is the SDK out of date?)")
                        else {
                            store.putString(CONFIG, r.body); store.putString(CONFIG_ETAG, r.etag); configAt = clock(); store.putLong(CONFIG_AT, configAt)
                            setConfig(c, "platform")
                        }
                    }
                    r.code == 404 -> VLog.e("Settings: the platform does not know the key \"$key\" (VisionForgeX SDK panel on the app's page).")
                    else -> VLog.w("Settings: the platform could not be reached (${r.code}); ${if (config != null) "using the last ones" else "no ads until it can"}.")
                }
                proceed()
            }
        }
    }

    private fun setConfig(c: Config, from: String) {
        val old = config
        config = c
        configFrom = from
        applyLog(c)
        events.inSample = (fnv1a(installId) % 10000) < (c.sample * 10000).toLong()
        // a placement whose unit or floors changed starts again
        for ((n, s) in slots) { val a = old?.placements?.get(n); val b = c.placements[n]; if (a == null || b == null || a.unit != b.unit || a.floors != b.floors || !b.on) s.reset() }
        if (engineStarted) engine.apply(engineSettings(c))
        if (sessionId.isNotEmpty() && !sesSent) sendSession()   // the first settings of an install arrive after its session began
        scheduleFlush()
    }

    private fun applyLog(c: Config) {
        if (options.logLevel != null) return
        VLog.level = when (c.log) { "debug" -> VfxLogLevel.DEBUG; "info" -> VfxLogLevel.INFO; "error" -> VfxLogLevel.ERROR; "none" -> VfxLogLevel.NONE; else -> VfxLogLevel.WARN }
    }

    private fun engineSettings(c: Config?) = EngineSettings(c?.child ?: false, c?.underAge ?: false, c?.rating ?: "", options.testDeviceIds)

    /** After settings (or none): start the engine, ask for consent, preload, tell the app. */
    private fun proceed() {
        val c = config
        if (c == null) { announce(); return }
        if (!c.enabled) { VLog.w("Ads are off for this app on the platform: ${c.reason}"); announce(); return }
        if (!consentDone) { askConsent(); if (!consentDone) return }
        if (!engineStarted) {
            engineStarted = true
            engine.initialize(app, engineSettings(c)) { main.post { engineReady = true; VLog.i("Engine ${engine.name} ready"); afterReady() } }
            return
        }
        if (engineReady) afterReady()
    }

    private fun afterReady() {
        announce()
        if (options.autoPreload) preloadAll()
    }

    fun preloadAll() {
        val c = config ?: return
        for ((n, p) in c.placements) {
            if (!p.on || !p.preload) continue
            when {
                p.format.isFullScreen -> slot(n).load()
                p.format == VfxFormat.NATIVE -> nativeCache.prefetch(p)
                else -> {}
            }
        }
    }

    private fun askConsent() {
        val c = config ?: return
        val ce = engine.consent
        if (!c.ump || ce == null) { consentDone = true; return }
        val act = resumed?.get()
        if (act == null || consentAsked) return   // asked when the first screen shows
        consentAsked = true
        val debug = if (options.consentDebugEea) ConsentDebug("EEA", options.consentTestDeviceHashedIds) else null
        ce.gather(act, c.underAge, debug) { can, err ->
            main.post {
                consentDone = true
                consentError = err
                if (err != null) VLog.w("Consent: $err")
                VLog.i("Consent: ads ${if (can) "may" else "may not"} be requested")
                proceed()
            }
        }
    }

    private fun status(): VfxStatus {
        val c = config
        val ready = c != null && c.enabled && engineReady && canRequest()
        val why = when {
            c == null -> "no settings yet"
            !c.enabled -> c.reason.ifBlank { "off on the platform" }
            !consentDone -> "waiting for consent"
            !canRequest() -> "consent does not allow ads"
            !engineReady -> "starting"
            else -> ""
        }
        return VfxStatus(ready, why, configFrom == "cache")
    }

    /** Tells the app once: ready, or why not (it never waits forever). */
    private fun announce() {
        if (readyAnnounced) return
        readyAnnounced = true
        val st = status()
        val l = listeners; listeners = ArrayList(); l.forEach { it.onReady(st) }
    }

    // ---------------------------------------------------------------- SlotEnv

    override fun now(): Long = clock()
    override fun post(delayMs: Long, r: Runnable): Any { main.postDelayed(r, delayMs); return r }
    override fun cancel(token: Any) { (token as? Runnable)?.let(main::removeCallbacks) }
    override fun context(): Context = resumed?.get() ?: app
    override fun config(): Config? = config
    override fun canRequest(): Boolean {
        val c = config ?: return false
        if (!c.enabled || !engineReady) return false
        val ce = engine.consent
        return !c.ump || ce == null || ce.canRequestAds(app)
    }
    override fun keyValues(placement: Placement): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m.putAll(appKv)
        config?.kv?.let(m::putAll)
        m["vfx_pl"] = placement.name
        config?.ab?.takeIf { it.isNotEmpty() }?.let { m["vfx_ab"] = it.replace(':', '_') }
        return m
    }
    override fun event(type: String, p: Placement, fields: Map<String, Any?>) {
        try {
            com.visionforgex.ads.VisionForgeX.eventListener?.onEvent(com.visionforgex.ads.VfxEvent(type, p.name, p.format, fields, clock()))
            if (type == "paid") com.visionforgex.ads.VisionForgeX.revenueListener?.onRevenue(p.name, p.format, (fields["v"] as? Number)?.toLong() ?: 0L, fields["cur"] as? String ?: "", fields["prec"] as? String ?: "", fields["src"] as? String)
        } catch (t: Throwable) { VLog.e("The app's event listener failed", t) }   // the app's own code never stops ours
        val c = config ?: return
        if (!c.eventsOn) return
        events.add(type, mapOf("pl" to p.name, "f" to p.format.wire) + fields)
        if (events.size() >= c.batch) flushNow()
    }
    override fun onFullScreenClosed() { skipNextAppOpen = false }

    fun slot(name: String): FullScreenSlot = slots.getOrPut(name) { FullScreenSlot(name, this) }

    fun setAppKeyValue(k: String, v: String?) { if (v == null) appKv.remove(k) else appKv[k] = v }

    // ---------------------------------------------------------------- events

    private fun header(): EventLog.Header {
        val av = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        return EventLog.Header(key, installId, sessionId, BuildConfig.VFX_VERSION, av, Build.VERSION.RELEASE ?: "", (Build.MANUFACTURER + " " + Build.MODEL).trim().take(60),
            Locale.getDefault().country.take(2), config?.ab ?: "", config?.sample ?: 1.0)
    }

    fun flushNow() {
        val c = config ?: return
        if (!c.eventsOn) return
        events.flush(c.eventsUrl, header())
    }

    private fun scheduleFlush() {
        flushToken?.let(::cancel)
        val c = config ?: return
        if (!c.eventsOn) return
        flushToken = post(c.flushSec * 1000L, Runnable { flushToken = null; flushNow(); scheduleFlush() })
    }

    // ---------------------------------------------------------------- the app in front, or not

    private fun onForeground() {
        val now = clock()
        val timeout = (config?.sessionTimeoutSec ?: 1800) * 1000L
        if (sessionId.isEmpty() || (backgroundAt > 0 && now - backgroundAt > timeout)) startSession()
        val c = config
        if (options.settingsForTesting == null && (c == null || now - configAt > c.ttlSec * 1000L)) fetchConfig("foreground")
        // after a while away, give up on giving up: load again
        slots.values.forEach { it.load(fresh = it.state == "gave up") }
        if (backgroundAt > 0) maybeAppOpen(cold = false)
        scheduleFlush()
    }

    private fun onBackground() {
        backgroundAt = clock()
        if (fullScreenShowing) skipNextAppOpen = true   // the user left from an ad (a click): no app open ad on the way back
        flushNow()
        flushToken?.let(::cancel); flushToken = null
    }

    private fun startSession() {
        sessionNumber = (store.getLong(SESSIONS, 0) + 1).toInt()
        store.putLong(SESSIONS, sessionNumber.toLong())
        sessionId = UUID.randomUUID().toString()
        pacer.startSession(sessionNumber, clock())
        sesSent = false
        if (config?.eventsOn == true) sendSession()
    }
    private var sesSent = false
    private fun sendSession() { sesSent = true; events.add("ses", mapOf("n" to sessionNumber, "first" to (sessionNumber == 1))) }

    /** App open ads: on a return to the app, or at a cold start when one is ready soon enough. */
    fun maybeAppOpen(cold: Boolean) {
        val c = config ?: return
        if (skipNextAppOpen) { skipNextAppOpen = false; return }
        val p = c.placements.values.firstOrNull { it.format == VfxFormat.APP_OPEN && it.on && it.autoShow } ?: return
        val act = resumed?.get() ?: return
        if (fullScreenShowing) return
        val s = slot(p.name)
        if (s.isReady()) s.show(act, null)
        else if (cold && clock() - coldStartAt < 4000) post(800, Runnable { if (clock() - coldStartAt < 4000) maybeAppOpen(cold = true) })
        else s.load()
    }

    inner class Tracker : Application.ActivityLifecycleCallbacks {
        private fun ours(a: Activity) = !a.javaClass.name.startsWith("com.google.android.gms.ads") && !a.javaClass.name.startsWith("com.google.android.ump")
        override fun onActivityStarted(a: Activity) {
            if (!ours(a)) return
            if (started++ == 0) onForeground()
        }
        override fun onActivityStopped(a: Activity) {
            if (!ours(a)) return
            if (--started <= 0) { started = 0; onBackground() }
        }
        override fun onActivityResumed(a: Activity) {
            if (!ours(a)) return
            val first = resumed == null
            resumed = WeakReference(a)
            if (!consentDone && config != null) proceed()
            if (first) maybeAppOpen(cold = true)
        }
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) { if (resumed?.get() === a) resumed = null }
    }

    fun currentActivity(): Activity? = resumed?.get()

    /** [current]: the screen in front when the app initializes late (from an Activity). */
    fun start(current: Activity?) {
        if (current != null && started == 0) {   // the first screen started before us: count it
            started = 1
            resumed = WeakReference(current)
            onForeground()
        }
        val testing = options.settingsForTesting
        if (testing != null) {   // a demo or a test: these settings, nothing fetched, nothing kept
            val c = ConfigParser.parse(testing)
            if (c == null) { VLog.e("settingsForTesting: not settings this SDK understands."); announce(); return }
            VLog.w("Using settings given by the app (testing only), not the platform's.")
            configAt = clock()
            setConfig(c, "testing")
            proceed()
            return
        }
        if (config != null) proceed()
        if (configFrom != "platform") fetchConfig("start")
    }

    fun resetConsent() { engine.consent?.reset(app); consentDone = false; consentAsked = false }
    fun configSnapshot(): Config? = config
    fun isEngineReady(): Boolean = engineReady

    companion object {
        const val INSTALL = "vfx.install"
        const val CONFIG = "vfx.config"
        const val CONFIG_ETAG = "vfx.config.etag"
        const val CONFIG_AT = "vfx.config.at"
        const val SESSIONS = "vfx.sessions"
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
