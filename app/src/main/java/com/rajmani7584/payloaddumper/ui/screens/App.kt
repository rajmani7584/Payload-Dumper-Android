package com.rajmani7584.payloaddumper.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import kotlinx.coroutines.launch


@Composable
fun App() {
    val appNavController = rememberNavController()
    val homeNavController = rememberNavController()

    NavHost(appNavController, Screens.App.route) {
        composable(Screens.App.route, enterTransition = {
            fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
        }, exitTransition = {
            fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        }) {
            AppLayout(appNavController, homeNavController)
        }
        composable(Screens.Selector.route, arguments = listOf(navArgument("directory") {
            type =
                NavType.BoolType
        })) {
            Selector(appNavController)
        }
    }
}

@Composable
fun AppLayout(appNavController: NavHostController, homeNavController: NavHostController) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(0) { 4 }
    NavigationSuiteScaffold(
        containerColor = Color.Transparent,
        navigationSuiteColors = NavigationSuiteDefaults.colors(navigationBarContainerColor = AppTheme.colors.surface),
        navigationItems = {
            NavigationSuiteItem(
                selected = pagerState.currentPage == 0,
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage == 0) {
                            homeNavController.popBackStack(Screens.Home.route, false)
                        } else {
                            pagerState.animateScrollToPage(0)
                        }
                    }
                },
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home") })
            NavigationSuiteItem(
                selected = pagerState.currentPage == 1,
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                },
                icon = {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Logs"
                    )
                },
                label = { Text("Logs") })
            NavigationSuiteItem(
                selected = pagerState.currentPage == 2,
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                },
                icon = {
                    Icon(
                        Icons.Default.FindInPage,
                        contentDescription = "Analyzer"
                    )
                },
                label
                = { Text("Analyze") })
            NavigationSuiteItem(
                selected = pagerState.currentPage == 3,
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(3) }
                },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Setting") },
                label = { Text("Setting") })
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pagerState) { page ->
                when (page) {
                    0 -> HomeScreen(appNavController, homeNavController)
                    1 -> LogScreen()
                    2 -> AnalyzeScreen()
                    3 -> SettingScreen()
                }
            }
        }
    }
}