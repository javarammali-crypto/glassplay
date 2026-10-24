package com.glassplay.app

import android.content.Context
import com.glassplay.app.data.AddonClient
import com.glassplay.app.data.AddonRepository
import com.glassplay.app.data.RecentSearches
import com.glassplay.app.data.WatchHistory
import com.glassplay.app.torrent.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons. */
object Graph {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var client: AddonClient
        private set
    lateinit var addons: AddonRepository
        private set
    lateinit var history: WatchHistory
        private set
    lateinit var recents: RecentSearches
        private set
    lateinit var torrents: TorrentEngine
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("glassplay", Context.MODE_PRIVATE)
        client = AddonClient()
        addons = AddonRepository(prefs, client, appScope)
        history = WatchHistory(prefs)
        recents = RecentSearches(prefs)
        torrents = TorrentEngine(context.applicationContext)
        addons.bootstrap()
    }
}
