package com.glassplay.app.data

/** A streaming service whose popular titles can be browsed in Discover. */
data class StreamingService(val id: String, val name: String, val color: Long, val types: Set<String>)

/**
 * Catalogs from the public "Streaming Catalogs" add-on, built in so Discover can show what's
 * popular on each service without anything installed. It serves every service it knows,
 * whichever ones its link was configured with.
 */
object StreamingServices {
    const val ADDON_ID = "pw.ers.netflix-catalog"
    private const val URL = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json"

    private val BOTH = setOf(TYPE_MOVIE, TYPE_SERIES)

    val all = listOf(
        StreamingService("nfx", "Netflix", 0xFFE50914, BOTH),
        StreamingService("netflix-top10-global", "Netflix Top 10", 0xFFB20710, BOTH),
        StreamingService("amp", "Prime Video", 0xFF00A8E1, BOTH),
        StreamingService("jhs", "JioHotstar", 0xFF1F4BFF, BOTH),
        StreamingService("dnp", "Disney+", 0xFF113CCF, BOTH),
        StreamingService("hbm", "HBO Max", 0xFF7B2CF5, BOTH),
        StreamingService("atp", "Apple TV+", 0xFF8E8E93, BOTH),
        StreamingService("zee", "ZEE5", 0xFF8230C6, BOTH),
        StreamingService("sonyliv", "Sony LIV", 0xFF1C1C8C, BOTH),
        StreamingService("hlu", "Hulu", 0xFF1CE783, BOTH),
        StreamingService("pmp", "Paramount+", 0xFF0064FF, BOTH),
        StreamingService("pcp", "Peacock", 0xFFF5C518, BOTH),
        StreamingService("cru", "Crunchyroll", 0xFFF47521, BOTH),
        StreamingService("mbi", "MUBI", 0xFF4A4A4A, setOf(TYPE_MOVIE)),
        StreamingService("stz", "Starz", 0xFF2E2E2E, BOTH),
        StreamingService("dpe", "Discovery+", 0xFF2175D9, setOf(TYPE_SERIES)),
        StreamingService("vik", "Rakuten Viki", 0xFF1E90FF, BOTH),
        StreamingService("shd", "Shudder", 0xFFE21F26, BOTH),
    )

    /** The add-on, described in code instead of fetched, so it works offline from install. */
    val addon: InstalledAddon by lazy {
        val catalogs = all.flatMap { s -> s.types.map { CatalogDef(it, s.id, s.name, emptyList()) } }
        InstalledAddon(
            transportUrl = URL,
            manifest = AddonManifest(
                id = ADDON_ID,
                name = "Streaming Catalogs",
                version = "",
                description = null,
                logo = null,
                types = listOf(TYPE_MOVIE, TYPE_SERIES),
                idPrefixes = listOf("tt"),
                resources = listOf(ResourceDef("catalog", null, null)),
                catalogs = catalogs,
            ),
            raw = "",
        )
    }

    fun forType(type: String): List<Pair<StreamingService, CatalogDef>> =
        all.filter { type in it.types }.mapNotNull { s ->
            addon.manifest.catalogs.firstOrNull { it.id == s.id && it.type == type }?.let { s to it }
        }
}
