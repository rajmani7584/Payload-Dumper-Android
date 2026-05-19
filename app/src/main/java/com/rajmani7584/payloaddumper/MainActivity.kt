package com.rajmani7584.payloaddumper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.components.Surface
import com.rajmani7584.payloaddumper.ui.screens.App
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var requestCounter by mutableIntStateOf(0)
    val model: DataModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(1, 0))
        setContent {
            AppTheme {
                Surface (Modifier.fillMaxSize()) {
                    App()
                }
            }
            LaunchedEffect(requestCounter) {
                delay(40)
                model.setPermission(this@MainActivity)
                if (model.hasPermission.value != true) LogManager.error("File Permission Denied")
            }
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