package com.rajmani7584.payloaddumper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rajmani7584.payloaddumper.model.DarkMode
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.DumpService
import com.rajmani7584.payloaddumper.model.PartStatus
import com.rajmani7584.payloaddumper.model.PayloadState
import com.rajmani7584.payloaddumper.model.SettingDeps
import com.rajmani7584.payloaddumper.model.SettingModel
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.screens.App
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.woheller69.freeDroidWarn.FreeDroidWarn

val LocalSettings = staticCompositionLocalOf<SettingModel> { error("No vm provided") }
class MainActivity : ComponentActivity() {
    private var requestCounter by mutableIntStateOf(0)
    val dataModel: DataModel by viewModels()
    private var dumpService: DumpService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            dumpService = (binder as DumpService.LocalBinder).getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            dumpService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingDeps.init(this)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(1, 0))

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                dataModel.payload.collect { state ->
                    if (state is PayloadState.Ready) {
                        val anyActive = state.partitions.any {
                            it.status == PartStatus.RUNNING ||
                                    it.status == PartStatus.PENDING
                        }
                        if (anyActive && !serviceBound) {
                            startAndBindService()
                        } else if (!anyActive && serviceBound) {
                            stopAndUnbindService()
                        }
                        val running = state.partitions.filter { it.status == PartStatus.RUNNING }
                        if (running.isNotEmpty()) {
                            val avg = running.map { it.progress }.average()
                            dumpService?.updateNotification(
                                "${running.size} partition(s) dumping — ${(avg * 100).toInt()}%"
                            )
                        }
                    }
                }
            }
        }

        setContent {
            val settingModel: SettingModel = viewModel(factory = SettingModel.Factory)
            val settings by settingModel.settings.collectAsStateWithLifecycle()
            val theme = settings.darkTheme
            val dark = (theme == DarkMode.DARK) || (theme == DarkMode.AUTO && isSystemInDarkTheme())
            val view = LocalView.current

            LaunchedEffect(theme) {
                WindowCompat.getInsetsController((view.context as Activity).window, view).let {
                    it.isAppearanceLightStatusBars = !dark
                    it.isAppearanceLightNavigationBars = !dark
                }
            }

            AppTheme(isDarkTheme = dark, colorTheme = settings.colorTheme) {
                CompositionLocalProvider(LocalSettings provides settingModel) {
                    App()
                }
            }
            LaunchedEffect(requestCounter) {
                delay(40)
                dataModel.setPermissions(this@MainActivity)
                if (dataModel.hasPermission.value != true) LogManager.error("File Permission Denied")
            }
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, DumpService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun stopAndUnbindService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        dumpService?.stop()
        dumpService = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            stopAndUnbindService()
        }
    }

    override fun onResume() {
        super.onResume()
        requestCounter++
    }
}

//@Composable
//fun MDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit = {}) {
//    Dialog (onDismissRequest = onDismissRequest) {
//        Box(
//            Modifier.widthIn(Dp.Unspecified, 560.dp).fillMaxWidth(.9f).background(
//                Color.White,
//                RoundedCornerShape((12.dp))
//            ).padding(12.dp)
//        ) {
//            content()
//        }
//    }
//}