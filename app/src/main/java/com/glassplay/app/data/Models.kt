package com.glassplay.app.data

import org.json.JSONArray
import org.json.JSONObject

// ---------- JSON helpers ----------

internal fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotEmpty() && it != "null" } else null

internal fun JSONObject.strList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val v = arr.opt(i) ?: continue
        if (v == JSONObject.NULL) continue
        val s = v.toString().trim()
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}

internal inline fun <T : Any> JSONArray?.mapObjects(block: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val v = block(o)
        if (v != null) out.add(v)
    }
    return out
}

// ---------- Add-on manifest ----------

data class ExtraDef(val name: String, val isRequired: Boolean, val options: List<String>)

data class CatalogDef(
    val type: String,
    val id: String,
    val name: String,
    val extras: List<ExtraDef>,
) {
    val requiredExtras: List<ExtraDef> get() = extras.filter { it.isRequired }
    fun supports(extra: String): Boolean = extras.any { it.name == extra }
    val supportsSearch: Boolean get() = supports("search")
    val genres: List<String> get() = extras.firstOrNull { it.name == "genre" }?.options.orEmpty()
    val genreRequired: Boolean get() = requiredExtras.any { it.name == "genre" }

    /** Can be shown as a home row (no extra needed). */
    val isHomeRow: Boolean get() = requiredExtras.isEmpty()

    /** Can be browsed in Discover (no extra, or only a genre with known options). */
    val isBrowsable: Boolean
        get() = requiredExtras.all { it.name == "genre" && it.options.isNotEmpty() }
}

data class ResourceDef(val name: String, val types: List<String>?, val idPrefixes: List<String>?)

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val logo: String?,
    val types: List<String>,
    val idPrefixes: List<String>?,
    val resources: List<ResourceDef>,
    val catalogs: List<CatalogDef>,
)

data class InstalledAddon(
    val transportUrl: String,
    val manifest: AddonManifest,
    val raw: String,
) {
    val baseUrl: String get() = transportUrl.substringBeforeLast("/manifest.json")

    fun hasResource(name: String): Boolean = manifest.resources.any { it.name == name }

    fun supports(resource: String, type: String, id: String): Boolean {
        val r = manifest.resources.firstOrNull { it.name == resource } ?: return false
        val types = r.types ?: manifest.types
        if (types.isNotEmpty() && type !in types) return false
        val prefixes = r.idPrefixes ?: manifest.idPrefixes
        return prefixes.isNullOrEmpty() || prefixes.any { id.startsWith(it) }
    }
}

fun parseManifest(j: JSONObject): AddonManifest {
    val resources = mutableListOf<ResourceDef>()
    val resArr = j.optJSONArray("resources")
    if (resArr != null) {
        for (i in 0 until resArr.length()) {
            when (val r = resArr.opt(i)) {
                is String -> resources.add(ResourceDef(r, null, null))
                is JSONObject -> {
                    val n = r.str("name") ?: continue
                    resources.add(
                        ResourceDef(
                            n,
                            if (r.has("types")) r.strList("types") else null,
                            if (r.has("idPrefixes")) r.strList("idPrefixes") else null,
                        )
                    )
                }
            }
        }
    }

    val catalogs = j.optJSONArray("catalogs").mapObjects { c ->
        val type = c.str("type")
        val id = c.str("id")
        if (type == null || id == null) {
            null
        } else {
            val extras = c.optJSONArray("extra").mapObjects { e ->
                e.str("name")?.let { ExtraDef(it, e.optBoolean("isRequired", false), e.strList("options")) }
            }.toMutableList()
            // Older manifests describe extras with these arrays instead.
            val legacyRequired = c.strList("extraRequired")
            for (n in c.strList("extraSupported")) {
                if (extras.none { it.name == n }) {
                    extras.add(ExtraDef(n, n in legacyRequired, if (n == "genre") c.strList("genres") else emptyList()))
                }
            }
            CatalogDef(type, id, c.str("name") ?: id.replaceFirstChar { it.uppercase() }, extras)
        }
    }
    if (catalogs.isNotEmpty() && resources.none { it.name == "catalog" }) {
        resources.add(ResourceDef("catalog", null, null))
    }

    return AddonManifest(
        id = j.str("id") ?: "",
        name = j.str("name") ?: "Unnamed add-on",
        version = j.str("version") ?: "",
        description = j.str("description"),
        logo = j.str("logo"),
        types = j.strList("types"),
        idPrefixes = if (j.has("idPrefixes")) j.strList("idPrefixes") else null,
        resources = resources,
        catalogs = catalogs,
    )
}

// ---------- Catalog items & details ----------

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val rating: String?,
)

fun parsePreview(o: JSONObject, fallbackType: String): MetaPreview? {
    val id = o.str("id") ?: o.str("imdb_id") ?: return null
    val name = o.str("name") ?: return null
    return MetaPreview(
        id = id,
        type = o.str("type") ?: fallbackType,
        name = name,
        poster = o.str("poster"),
        background = o.str("background"),
        logo = o.str("logo"),
        description = o.str("description"),
        releaseInfo = o.str("releaseInfo") ?: o.str("year"),
        rating = o.str("imdbRating"),
    )
}

data class Video(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val thumbnail: String?,
    val overview: String?,
) {
    val label: String? get() = if (season != null && episode != null) "S$season E$episode" else null
}

data class MetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val runtime: String?,
    val rating: String?,
    val genres: List<String>,
    val cast: List<String>,
    val directors: List<String>,
    val trailerYtId: String?,
    val videos: List<Video>,
) {
    val isSeries: Boolean get() = type != "movie" && videos.isNotEmpty()
}

fun parseMeta(o: JSONObject, fallbackType: String): MetaDetail? {
    val id = o.str("id") ?: return null
    val name = o.str("name") ?: return null
    val trailer = o.optJSONArray("trailers").mapObjects { it.str("source") }.firstOrNull()
        ?: o.optJSONArray("trailerStreams").mapObjects { it.str("ytId") }.firstOrNull()
    val videos = o.optJSONArray("videos").mapObjects { v ->
        val vid = v.str("id")
        if (vid == null) null else Video(
            id = vid,
            title = v.str("title") ?: v.str("name") ?: "",
            season = if (v.has("season") && !v.isNull("season")) v.optInt("season") else null,
            episode = when {
                v.has("episode") && !v.isNull("episode") -> v.optInt("episode")
                v.has("number") && !v.isNull("number") -> v.optInt("number")
                else -> null
            },
            released = v.str("released"),
            thumbnail = v.str("thumbnail"),
            overview = v.str("overview") ?: v.str("description"),
        )
    }
    return MetaDetail(
        id = id,
        type = o.str("type") ?: fallbackType,
        name = name,
        poster = o.str("poster"),
        background = o.str("background"),
        logo = o.str("logo"),
        description = o.str("description"),
        releaseInfo = o.str("releaseInfo") ?: o.str("year"),
        runtime = o.str("runtime"),
        rating = o.str("imdbRating"),
        genres = o.strList("genres").ifEmpty { o.strList("genre") },
        cast = o.strList("cast"),
        directors = o.strList("director"),
        trailerYtId = trailer,
        videos = videos,
    )
}

// ---------- Streams ----------

data class StreamItem(
    val addonName: String,
    val name: String?,
    val title: String?,
    val url: String?,
    val ytId: String?,
    val externalUrl: String?,
    val infoHash: String?,
    val headers: Map<String, String>,
) {
    /** Streams GlassPlay can open: direct video links, YouTube, or external links. */
    val playable: Boolean get() = url != null || ytId != null || externalUrl != null
}

fun parseStream(o: JSONObject, addonName: String): StreamItem {
    val headers = mutableMapOf<String, String>()
    o.optJSONObject("behaviorHints")
        ?.optJSONObject("proxyHeaders")
        ?.optJSONObject("request")
        ?.let { h -> h.keys().forEach { k -> h.str(k)?.let { headers[k] = it } } }
    val url = o.str("url")?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    return StreamItem(
        addonName = addonName,
        name = o.str("name"),
        title = o.str("title") ?: o.str("description"),
        url = url,
        ytId = o.str("ytId"),
        externalUrl = o.str("externalUrl"),
        infoHash = o.str("infoHash"),
        headers = headers,
    )
}

const val TYPE_MOVIE = "movie"
const val TYPE_SERIES = "series"
val MAIN_TYPES = setOf(TYPE_MOVIE, TYPE_SERIES)

fun typeLabel(type: String): String = when (type) {
    TYPE_MOVIE -> "Movies"
    TYPE_SERIES -> "TV Shows"
    else -> type.replaceFirstChar { it.uppercase() }
}
