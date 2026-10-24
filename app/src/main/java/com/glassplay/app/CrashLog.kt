package com.glassplay.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the details of a crash so the app can show them the next time it opens. */
object CrashLog {
    private lateinit var file: File

    fun install(context: Context) {
        file = File(context.filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                file.writeText(
                    "GlassPlay $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n$trace"
                )
            } catch (e: Throwable) {
                // Nothing more we can do while crashing.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * The report from the last crash, if there is one not yet seen. Crashes inside native
     * code (like the torrent library) skip the handler above, so Android's own record of
     * how the app last ended is checked too.
     */
    fun pending(context: Context): String? {
        if (::file.isInitialized && file.exists()) return file.readText().take(6_000)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val prefs = context.getSharedPreferences("glassplay", Context.MODE_PRIVATE)
            val am = context.getSystemService(ActivityManager::class.java)
            val last = am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() ?: return null
            if (last.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return null
            // Report each native crash once.
            if (prefs.getLong(KEY_SEEN_EXIT, 0L) == last.timestamp) return null
            prefs.edit().putLong(KEY_SEEN_EXIT, last.timestamp).apply()
            "GlassPlay · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Native crash (signal ${last.status}) at ${java.util.Date(last.timestamp)}\n" +
                (last.description ?: "")
        } catch (e: Exception) {
            null
        }
    }

    private const val KEY_SEEN_EXIT = "last_native_crash_seen"

    fun clear() {
        if (::file.isInitialized) file.delete()
    }
}
