package com.glassplay.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassplay.app.ui.AppRoot
import com.glassplay.app.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Unfinished torrent downloads continue from where they stopped.
        if (savedInstanceState == null) Graph.downloads.resumeAll()
        setContent {
            GlassTheme {
                AppRoot()
            }
        }
    }
}
