package com.glassplay.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade

class GlassPlayApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Graph.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
}
