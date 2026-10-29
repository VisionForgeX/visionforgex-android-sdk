package com.visionforgex.ads.internal

import android.util.Log
import com.visionforgex.ads.TAG
import com.visionforgex.ads.VfxLogLevel
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

internal object VLog {
    @Volatile var level: VfxLogLevel = VfxLogLevel.WARN
    fun e(m: String, t: Throwable? = null) { if (level >= VfxLogLevel.ERROR) Log.e(TAG, m, t) }
    fun w(m: String) { if (level >= VfxLogLevel.WARN) Log.w(TAG, m) }
    fun i(m: String) { if (level >= VfxLogLevel.INFO) Log.i(TAG, m) }
    fun d(m: String) { if (level >= VfxLogLevel.DEBUG) Log.d(TAG, m) }
}

internal class HttpResult(val code: Int, val body: String, val etag: String?)

/** Plain HttpURLConnection: no library added to the app. Call from a background thread. */
internal object Http {
    var userAgent = "VisionForgeX-Android"

    fun get(url: String, etag: String?): HttpResult = request(url, "GET", null, etag)

    /** A JSON body, gzipped when large. */
    fun postJson(url: String, json: String): HttpResult {
        val raw = json.toByteArray(Charsets.UTF_8)
        return request(url, "POST", raw, null)
    }

    private fun request(url: String, method: String, body: ByteArray?, etag: String?): HttpResult {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 10000
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
                if (etag != null) setRequestProperty("If-None-Match", etag)
            }
            if (body != null) {
                val gz = body.size > 1024
                val out = if (gz) gzip(body) else body
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (gz) c.setRequestProperty("Content-Encoding", "gzip")
                c.setFixedLengthStreamingMode(out.size)
                c.outputStream.use { it.write(out) }
            }
            val code = c.responseCode
            val stream = if (code in 200..399) c.inputStream else c.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            HttpResult(code, text, c.getHeaderField("ETag"))
        } catch (e: Exception) {
            VLog.d("$method $url failed: ${e.javaClass.simpleName} ${e.message}")
            HttpResult(0, "", null)
        } finally {
            c?.disconnect()
        }
    }

    private fun gzip(b: ByteArray): ByteArray { val o = ByteArrayOutputStream(); GZIPOutputStream(o).use { it.write(b) }; return o.toByteArray() }
}
