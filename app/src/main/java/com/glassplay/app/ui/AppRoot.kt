package com.glassplay.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassplay.app.ui.addons.AddonsScreen
import com.glassplay.app.ui.components.GlassTabBar
import com.glassplay.app.ui.components.TabItem
import com.glassplay.app.ui.detail.DetailScreen
import com.glassplay.app.ui.discover.DiscoverScreen
import com.glassplay.app.ui.home.HomeScreen
import com.glassplay.app.ui.search.SearchScreen
import com.glassplay.app.ui.theme.AuroraBackground

private val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val tabs = remember {
        listOf(
            TabItem("home", "Home", Icons.Rounded.Home),
            TabItem("discover", "Discover", Icons.Rounded.Explore),
            TabItem("search", "Search", Icons.Rounded.Search),
            TabItem("addons", "Add-ons", Icons.Rounded.Extension),
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

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        NavHost(
            navController = nav,
            startDestination = "home",
            enterTransition = { fadeIn(tween(280)) + scaleIn(tween(380, easing = IosEase), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("home") {
                HomeScreen(onOpen = openDetail, onGoAddons = { selectTab("addons") })
            }
            composable("discover") { DiscoverScreen(onOpen = openDetail) }
            composable("search") { SearchScreen(onOpen = openDetail) }
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

        AnimatedVisibility(
            visible = tabIndex >= 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(380, easing = IosEase)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            GlassTabBar(tabs, tabIndex.coerceAtLeast(0)) { i -> selectTab(tabs[i].route) }
        }
    }
}
