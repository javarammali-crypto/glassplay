package com.glassplay.app.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.view.Display
import com.glassplay.app.Graph
import com.glassplay.app.data.MetaDetail
import com.glassplay.app.data.PlaybackContext
import com.glassplay.app.data.Tracking
import java.util.Locale

/** What this phone and its connection can handle, for ranking streams. */
fun playbackContext(context: Context, meta: MetaDetail): PlaybackContext {
    val measured = Graph.settings.measuredBandwidthMbps
    // Before anything has been measured, Android's own rough estimate of the link speed
    // (capped, as it's often optimistic), or a modest default.
    val bandwidth = when {
        measured > 0 -> measured
        else -> linkEstimateMbps(context)?.coerceAtMost(50.0) ?: 20.0
    }
    val runtimeMs = Tracking.parseRuntimeMs(meta.runtime).takeIf { it > 0 }
        ?: if (meta.isSeries) 45L * 60_000 else 120L * 60_000
    val hdrTypes = try {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        display?.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
    } catch (e: Exception) {
        emptySet()
    }
    return PlaybackContext(
        bandwidthMbps = bandwidth,
        runtimeSeconds = runtimeMs / 1000,
        supportsHdr = hdrTypes.isNotEmpty(),
        supportsDolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes,
        prefersHindi = Locale.getDefault().country.equals("IN", ignoreCase = true),
    )
}

private fun linkEstimateMbps(context: Context): Double? = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.let { it / 1000.0 }
} catch (e: Exception) {
    null
}
