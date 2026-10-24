package com.glassplay.app

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

    /** The report from the last crash, if there is one not yet seen. */
    fun pending(): String? = if (::file.isInitialized && file.exists()) file.readText().take(6_000) else null

    fun clear() {
        if (::file.isInitialized) file.delete()
    }
}
