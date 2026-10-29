package com.glassplay.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.glassplay.app.ui.AppRoot
import com.glassplay.app.ui.theme.GlassTheme
import com.glassplay.app.ui.theme.rememberDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Unfinished torrent downloads continue from where they stopped.
        if (savedInstanceState == null) {
            Graph.downloads.resumeAll()
            Graph.updater.check(manual = false)
        }
        setContent {
            val mode by Graph.settings.theme.collectAsState()
            val dark = rememberDarkTheme(mode)
            // Status and navigation bar icons follow the theme.
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            GlassTheme(dark = dark) {
                AppRoot()
            }
        }
    }
}
