package com.rajmani7584.payloaddumper.ui.screens

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.R
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.PayloadState
import com.rajmani7584.payloaddumper.model.PayloadType
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.LocalColors
import com.rajmani7584.payloaddumper.ui.components.components.Button
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.components.components.textfield.OutlinedTextField
import com.rajmani7584.payloaddumper.ui.customviews.LoadingIndicator
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar


@Composable
fun HomeScreen(appNavController: NavHostController, homeNavController: NavHostController) {
    val dataModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val payloadState by dataModel.payload.collectAsStateWithLifecycle()

    Box(Modifier
        .fillMaxSize()
    ) {
        NavHost(homeNavController, Screens.Home.route, Modifier.fillMaxSize()) {
            composable(Screens.Home.route, enterTransition = {
                fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
            }, exitTransition = {
                fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start)
            }) {
                HomeScreenUI(payloadState, appNavController, homeNavController)
            }
            composable(Screens.Extract.route, enterTransition = {
                fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
            }, exitTransition = {
                fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
            }) {
                    ExtractScreen(payloadState, appNavController, homeNavController)
            }
        }
    }
}

@Composable
fun HomeScreenUI(
    payloadState: PayloadState,
    appNavController: NavHostController,
    homeNavController: NavHostController
) {
    val dataModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val hasNotifyPermission by dataModel.hasNotifyPermission
//    val settings by LocalSettings.current.settings.collectAsState()
    val activity = LocalActivity.current


    LaunchedEffect(Unit) {
        dataModel.navEvent.collect {
            homeNavController.navigate(Screens.Extract.route) {
                popUpTo(Screens.Home.route) {
                    saveState = true
                }
                launchSingleTop = false
            }
        }
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Payload Dumper") }) { innerPadding ->
        Column(
            Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column {
                when (payloadState) {
                    PayloadState.Loading -> LoadingIndicator()
                    else -> {
                        hasNotifyPermission?.let {
                            if (!it)
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                        .clip(RoundedCornerShape(8.dp)).background(
                                        LocalColors.current.surface
                                    ).border(
                                        BorderStroke(1.dp, LocalContentColor.current),
                                        RoundedCornerShape(8.dp)
                                    ), horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val activity = LocalActivity.current
                                    Spacer(Modifier.height(4.dp))
                                    Text("Enable notification permission to process payload in background")
                                    Spacer(Modifier.height(6.dp))
                                    Button(text = "Allow notifications", onClick = {
                                        if (activity == null) return@Button
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            dataModel.requestNotifyPermission(activity)
                                        }
                                    })
                                    Spacer(Modifier.height(4.dp))
                                }
                        }
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    vertical = 16.dp,
                                    horizontal = 24.dp
                                )
                            ) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = "File",
                                    Modifier.size(80.dp).align(
                                        Alignment.CenterHorizontally
                                    )
                                )
                                Spacer(Modifier.height(24.dp))
                                dataModel.hasPermission.value?.let {
                                    if (!it) {
                                        Button(
                                            text = stringResource(R.string.first_start_allow_file_access),
                                            onClick = {
                                                if (activity == null) return@Button
                                                dataModel.requestPermission(activity)
                                            })
                                    } else
                                        Button(text = "Select a file", onClick = {
                                            appNavController.navigate(
                                                Screens.Selector.createRoute(
                                                    false
                                                )
                                            ) {
                                                popUpTo(Screens.Home.route) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                            }
                                        })
                                }
                            }
                            Spacer(Modifier.height(24.dp))

                            Text("OR", color = AppTheme.colors.text.copy(alpha = .4f))
                            Spacer(Modifier.height(24.dp))
                            Column {
                                val url by dataModel.remoteUrl
                                OutlinedTextField(
                                    value = url, onValueChange = { dataModel.setURL(it) },
                                    singleLine = true,
                                    modifier = Modifier.widthIn(Dp.Unspecified, 540.dp)
                                        .fillMaxWidth(.75f),
                                    placeholder = {
                                        Text(
                                            "https://website.com/ota.zip",
                                            color = AppTheme.colors.text.copy(alpha = .4f)
                                        )
                                    }
                                )
                                Button(
                                    onClick = {
                                        dataModel.init(
                                            PayloadType.RemotePayload(url)
                                        )
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                        .padding(vertical = 12.dp),
                                    text = "Fetch"
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            if (payloadState is PayloadState.Ready) {
                                Row(
                                    modifier = Modifier.widthIn(Dp.Unspecified, 540.dp)
                                        .fillMaxWidth(.8f)
                                        .clip(RoundedCornerShape(8.dp)).background(
                                            AppTheme.colors.primary.copy(alpha = .1f)
                                        ).clickable {
                                            homeNavController.navigate(Screens.Extract.route) {
                                                popUpTo(Screens.Home.route) {
                                                    saveState = true
                                                }
                                                launchSingleTop = false
                                            }
                                        }.padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        payloadState.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(modifier = Modifier.size(16.dp)) {
                                        Icon(
                                            Icons.AutoMirrored.Default.ArrowForward,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                            if (payloadState is PayloadState.Error) {
                                Box(
                                    Modifier.widthIn(Dp.Unspecified, 840.dp).fillMaxWidth(.75f)
                                ) {
                                    Text(
                                        payloadState.message,
                                        color = Color.Red,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
sealed class Screens(val route: String) {
    data object App: Screens("app")
    data object Home: Screens("home")
    data object Extract: Screens("extract")
    data object Selector: Screens("selector/{directory}") {
        fun createRoute(isDirectory: Boolean) = "selector/$isDirectory"
    }
}