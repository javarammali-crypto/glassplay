package com.glassplay.app

import android.content.Context
import com.glassplay.app.account.AccountRepository
import com.glassplay.app.data.AddonClient
import com.glassplay.app.data.AddonRepository
import com.glassplay.app.data.AppSettings
import com.glassplay.app.data.AppUpdater
import com.glassplay.app.data.DownloadRepository
import com.glassplay.app.data.FranchiseRepository
import com.glassplay.app.data.RecentSearches
import com.glassplay.app.data.WatchHistory
import com.glassplay.app.data.WatchTracker
import com.glassplay.app.torrent.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    lateinit var downloads: DownloadRepository
        private set
    lateinit var franchises: FranchiseRepository
        private set
    lateinit var accounts: AccountRepository
        private set
    lateinit var tracker: WatchTracker
        private set
    lateinit var updater: AppUpdater
        private set
    lateinit var settings: AppSettings
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("glassplay", Context.MODE_PRIVATE)
        settings = AppSettings(prefs)
        client = AddonClient()
        addons = AddonRepository(prefs, client, appScope)
        history = WatchHistory(prefs)
        recents = RecentSearches(prefs)
        franchises = FranchiseRepository(prefs, client)
        torrents = TorrentEngine(context.applicationContext)
        downloads = DownloadRepository(context.applicationContext, prefs, torrents, appScope)
        addons.bootstrap()

        accounts = AccountRepository(context.applicationContext)
        tracker = WatchTracker(appScope, addons, prefs)
        updater = AppUpdater(context.applicationContext, appScope)
        accounts.start()
        // Watch history belongs to whoever is signed in.
        appScope.launch {
            accounts.user.collect { user -> tracker.bind(accounts.db, user?.uid, history.entries.value) }
        }
    }
}
