package com.rajmani7584.payloaddumper.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen() {
    val dataModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val scrollState = rememberLazyListState()
    val isDarkTheme by dataModel.isDarkTheme.collectAsState()
    val logs by LogManager.logs.collectAsState()
    Scaffold(topBar = { ScreenTopBar(title = "Logs") }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            SelectContainer {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                        .background(AppTheme.colors.onBackground, RoundedCornerShape(4.dp)),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                        ) {
                            Text(
                                entry.timestamp,
                                fontFamily = FontFamily.Monospace,
                                fontStyle = FontStyle.Italic,
                                fontSize = 12.sp,
                                color = when (entry.log) {
                                    is LogType.Success -> Color.Green.copy(red = .75f, blue = .75f)
                                    is LogType.Failure -> Color.Red
                                    is LogType.Log -> Color(0xffaaaaff)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.log.message,
                                fontFamily = FontFamily.Monospace,
                                fontStyle = FontStyle.Italic,
                                fontSize = 12.sp,
                                color = when (entry.log) {
                                    is LogType.Success -> Color.Green
                                    is LogType.Failure -> Color.Red
                                    is LogType.Log -> Color.Blue.copy(
                                        blue = 1f,
                                        red = .2f,
                                        green = if (isDarkTheme) .6f else .2f
                                    )
                                }
                            )
                        }
                    }
                }
                LaunchedEffect(logs.size) {
                    scrollState.animateScrollToItem(logs.size)
                }
            }
        }
    }
}
data class LogEntry(val log: LogType, val timestamp: String)
object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private fun log(message: LogType) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { it + LogEntry(message, timestamp) }
    }

    fun success(message: String) {
        log(LogType.Success(message))
    }

    fun error(message: String) {
        log(LogType.Failure(message))
    }

    fun log(message: String) {
        log(LogType.Log(message))
    }
}
sealed class LogType {
    abstract val message: String
    data class Success(override val message: String): LogType()
    data class Failure(override val message: String): LogType()
    data class Log(override val message: String): LogType()
}

@Composable
fun SelectContainer(content: @Composable () -> Unit) {
    val colors = TextSelectionColors(Color.Red, Color.Green)

    CompositionLocalProvider(LocalTextSelectionColors provides colors) {
        SelectionContainer { content() }
    }
}