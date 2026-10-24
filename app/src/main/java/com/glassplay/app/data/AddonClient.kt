package com.glassplay.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Talks to add-ons using the Stremio add-on protocol. */
class AddonClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()
    private val ttlMs = 10 * 60 * 1000L

    suspend fun getText(url: String, useCache: Boolean = true): String = withContext(Dispatchers.IO) {
        if (useCache) {
            val hit = cache[url]
            if (hit != null && System.currentTimeMillis() - hit.first < ttlMs) return@withContext hit.second
        }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Server returned error ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("Empty response")
            cache[url] = System.currentTimeMillis() to body
            body
        }
    }

    fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.startsWith("stremio://", ignoreCase = true)) u = "https://" + u.substring("stremio://".length)
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        val path = u.substringBefore('?')
        val query = u.substringAfter('?', "")
        if (!path.endsWith("manifest.json")) u = path.trimEnd('/') + "/manifest.json" + if (query.isEmpty()) "" else "?$query"
        return u
    }

    suspend fun fetchManifest(raw: String): InstalledAddon {
        val url = try {
            normalizeUrl(raw)
        } catch (e: Exception) {
            throw IOException("That doesn't look like a valid link")
        }
        val text = try {
            getText(url, useCache = false)
        } catch (e: IllegalArgumentException) {
            throw IOException("That doesn't look like a valid link")
        }
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("That link didn't return an add-on manifest")
        }
        val manifest = parseManifest(json)
        if (manifest.id.isEmpty()) throw IOException("That link didn't return an add-on manifest")
        return InstalledAddon(url, manifest, text)
    }

    suspend fun catalog(
        addon: InstalledAddon,
        cat: CatalogDef,
        extra: Map<String, String> = emptyMap(),
    ): List<MetaPreview> {
        val extraPath = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val url = addon.resourceUrl("/catalog/${enc(cat.type)}/${enc(cat.id)}$extraPath.json")
        val json = JSONObject(getText(url))
        return json.optJSONArray("metas").mapObjects { parsePreview(it, cat.type) }
            .distinctBy { it.type + ":" + it.id }
    }

    suspend fun meta(addon: InstalledAddon, type: String, id: String): MetaDetail? {
        val url = addon.resourceUrl("/meta/${enc(type)}/${enc(id)}.json")
        val json = JSONObject(getText(url))
        return json.optJSONObject("meta")?.let { parseMeta(it, type) }
    }

    suspend fun streams(addon: InstalledAddon, type: String, id: String): List<StreamItem> {
        val url = addon.resourceUrl("/stream/${enc(type)}/${enc(id)}.json")
        val json = JSONObject(getText(url))
        return json.optJSONArray("streams").mapObjects { parseStream(it, addon.manifest.name) }
    }

    suspend fun subtitles(addon: InstalledAddon, type: String, id: String): List<SubtitleItem> {
        val url = addon.resourceUrl("/subtitles/${enc(type)}/${enc(id)}.json")
        val json = JSONObject(getText(url))
        return json.optJSONArray("subtitles").mapObjects { parseSubtitle(it, addon.manifest.name) }
    }

    private fun enc(s: String): String = Uri.encode(s)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) GlassPlay/1.0"
    }
}
