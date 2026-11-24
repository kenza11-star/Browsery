package dev.browsery.app.sync

import dev.browsery.app.auth.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SyncException(message: String) : Exception(message)

/** Klien PostgREST untuk tabel browsery_bookmarks. Akses dibatasi RLS (hanya baris milik pengguna). */
class SyncClient(
    private val base: String = SupabaseConfig.URL,
    private val key: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private fun open(path: String, method: String, token: String): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("apikey", key)
        c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    private fun fail(code: Int): SyncException = SyncException(
        if (code == 401 || code == 403) "Sesi tidak diterima server; masuk ulang." else "Server menolak permintaan (HTTP $code).",
    )

    fun fetchBookmarks(token: String): List<SyncMerge.Item> {
        val c = open("/rest/v1/browsery_bookmarks?select=url,title&deleted_at=is.null&order=created_at.asc&limit=5000", "GET", token)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw fail(code)
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SyncMerge.Item(o.getString("url"), o.optString("title"))
            }
        } catch (e: IOException) {
            throw SyncException("Tidak bisa terhubung ke server.")
        } finally {
            c.disconnect()
        }
    }

    fun pushBookmarks(token: String, items: List<SyncMerge.Item>) {
        for (chunk in items.chunked(200)) {
            val c = open("/rest/v1/browsery_bookmarks", "POST", token)
            try {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Prefer", "return=minimal")
                val body = JSONArray()
                chunk.forEach { body.put(JSONObject().put("url", it.url).put("title", it.title)) }
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                if (code !in 200..299) throw fail(code)
            } catch (e: IOException) {
                throw SyncException("Tidak bisa terhubung ke server.")
            } finally {
                c.disconnect()
            }
        }
    }
}
