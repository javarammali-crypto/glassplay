package com.glassplay.app.ui

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassplay.app.CrashLog
import com.glassplay.app.Graph
import com.glassplay.app.data.UpdateState
import com.glassplay.app.ui.account.AuthScreen
import com.glassplay.app.ui.account.ProfileScreen
import com.glassplay.app.ui.addons.AddonsScreen
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.GlassTabBar
import com.glassplay.app.ui.components.ProgressLine
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.TabItem
import com.glassplay.app.ui.components.frostedBackdrop
import com.glassplay.app.ui.detail.DetailScreen
import com.glassplay.app.ui.discover.DiscoverScreen
import com.glassplay.app.ui.discover.FranchiseScreen
import com.glassplay.app.ui.downloads.DownloadsScreen
import com.glassplay.app.ui.home.HomeScreen
import com.glassplay.app.ui.search.SearchOverlay
import com.glassplay.app.ui.settings.SettingsScreen
import com.glassplay.app.ui.theme.AuroraBackground
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.LocalPalette
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)

@Composable
fun AppRoot() {
    val accounts = Graph.accounts
    val ready by accounts.ready.collectAsState()
    val user by accounts.user.collectAsState()
    when {
        // Waiting to hear whether someone is signed in.
        !ready -> Box(Modifier.fillMaxSize()) {
            AuroraBackground()
            Spinner(Modifier.align(Alignment.Center))
        }
        accounts.enabled && user == null -> AuthScreen()
        else -> MainApp()
    }
    CrashReportDialog()
    UpdateDialog()
}

@Composable
private fun MainApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val tabs = remember {
        listOf(
            TabItem("home", "Home", Icons.Rounded.Home),
            TabItem("discover", "Discover", Icons.Rounded.Explore),
            TabItem("downloads", "Downloads", Icons.Rounded.Download),
            TabItem("addons", "Add-ons", Icons.Rounded.Extension),
            TabItem("settings", "Settings", Icons.Rounded.Settings),
        )
    }
    val tabIndex = tabs.indexOfFirst { it.route == route }

    fun selectTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openDetail: (String, String) -> Unit = { type, id ->
        nav.navigate("detail/${Uri.encode(type)}/${Uri.encode(id)}")
    }

    // Search opens over a blurred Home. The query is kept here so it's still there after
    // opening a result and coming back.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searching = searchOpen && route == "home"
    val blur by animateDpAsState(if (searching) 26.dp else 0.dp, tween(320), label = "searchBlur")

    val haze = remember { HazeState() }

    Box(Modifier.fillMaxSize()) {
        // Everything under the tab bar is the source of its frosted blur.
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AuroraBackground()

            NavHost(
                modifier = Modifier.fillMaxSize().blur(blur),
                navController = nav,
                startDestination = "home",
                enterTransition = { fadeIn(tween(280)) + scaleIn(tween(380, easing = IosEase), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable("home") {
                    HomeScreen(
                        onOpen = openDetail,
                        onGoAddons = { selectTab("addons") },
                        onOpenProfile = { nav.navigate("profile") },
                        onOpenSearch = { searchOpen = true },
                    )
                }
                composable("discover") {
                    DiscoverScreen(onOpen = openDetail, onOpenFranchise = { nav.navigate("franchise/${Uri.encode(it)}") })
                }
                composable(
                    route = "franchise/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(420, easing = IosEase)) +
                            fadeIn(tween(250))
                    },
                    exitTransition = { fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 0.96f) },
                    popEnterTransition = { fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.96f) },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(340, easing = IosEase)) +
                            fadeOut(tween(300))
                    },
                ) { entry ->
                    FranchiseScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = openDetail,
                    )
                }
                composable("settings") { SettingsScreen(onOpenProfile = { nav.navigate("profile") }) }
                composable("downloads") { DownloadsScreen() }
                composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }, onOpen = openDetail) }
                composable("addons") { AddonsScreen() }
                composable(
                    route = "detail/{type}/{id}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                    ),
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(420, easing = IosEase)) +
                            fadeIn(tween(250))
                    },
                    exitTransition = { fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 0.96f) },
                    popEnterTransition = { fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.96f) },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(340, easing = IosEase)) +
                            fadeOut(tween(300))
                    },
                ) { entry ->
                    DetailScreen(
                        type = entry.arguments?.getString("type").orEmpty(),
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = searching,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200)),
        ) {
            val palette = LocalPalette.current
            Box(
                Modifier
                    .fillMaxSize()
                    // Blur needs Android 12; older phones get a stronger veil instead.
                    .background(palette.background.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.45f else 0.9f))
            ) {
                SearchOverlay(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onOpen = openDetail,
                    onClose = {
                        searchOpen = false
                        searchQuery = ""
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = tabIndex >= 0 && !searching,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(380, easing = IosEase)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            GlassTabBar(tabs, tabIndex.coerceAtLeast(0), onSelect = { i -> selectTab(tabs[i].route) }, backdrop = Modifier.frostedBackdrop(haze))
        }
    }
}

/** Offers a newer GlassPlay release, downloads it and hands it to Android's installer. */
@Composable
private fun UpdateDialog() {
    val state by Graph.updater.state.collectAsState()
    val s = state
    val release = when (s) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return
    GlassDialog(onDismiss = { if (s !is UpdateState.Downloading) Graph.updater.dismiss() }) {
        Text("Update available", style = GlassType.title3, color = Ink)
        Text(
            "${release.name} is ready. You have ${Graph.updater.currentName}.",
            style = GlassType.subhead,
            color = TextSecondary,
        )
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                release.notes,
                style = GlassType.footnote,
                color = TextSecondary,
                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(16.dp))
        when (s) {
            is UpdateState.Downloading -> {
                ProgressLine(s.progress, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Downloading… ${(s.progress * 100).toInt()}%", style = GlassType.footnote, color = TextSecondary)
            }
            is UpdateState.ReadyToInstall -> {
                Text(
                    "If Android asks, allow GlassPlay to install apps, then come back and tap Install again.",
                    style = GlassType.footnote,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Later", onClick = { Graph.updater.dismiss() }, modifier = Modifier.weight(1f))
                    GlassButton("Install", primary = true, onClick = { Graph.updater.install(s.file) }, modifier = Modifier.weight(1f))
                }
            }
            else -> {
                if (s is UpdateState.Failed) {
                    Text(s.message, style = GlassType.footnote, color = Danger)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Later", onClick = { Graph.updater.dismiss() }, modifier = Modifier.weight(1f))
                    GlassButton("Update", primary = true, onClick = { Graph.updater.download(release) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** After a crash, shows what went wrong so it can be copied and reported. */
@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pending(context)) }
    val clipboard = LocalClipboardManager.current
    val text = report ?: return
    val dismiss = {
        CrashLog.clear()
        report = null
    }
    GlassDialog(onDismiss = dismiss) {
        Text("GlassPlay closed unexpectedly", style = GlassType.title3, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sorry about that. Copy the details below and share them so the problem can be fixed.",
            style = GlassType.subhead,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            fontSize = 10.sp,
            color = TextTertiary,
            modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Close", onClick = dismiss, modifier = Modifier.weight(1f))
            GlassButton(
                "Copy details",
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    dismiss()
                },
                primary = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
