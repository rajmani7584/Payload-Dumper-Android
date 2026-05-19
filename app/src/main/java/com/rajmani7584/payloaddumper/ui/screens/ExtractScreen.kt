package com.rajmani7584.payloaddumper.ui.screens

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.protobuf.ByteString
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.PartStatus
import com.rajmani7584.payloaddumper.model.PartitionState
import com.rajmani7584.payloaddumper.model.PayloadState
import com.rajmani7584.payloaddumper.model.Utils
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.components.Button
import com.rajmani7584.payloaddumper.ui.components.components.ButtonVariant
import com.rajmani7584.payloaddumper.ui.components.components.IconButton
import com.rajmani7584.payloaddumper.ui.components.components.IconButtonVariant
import com.rajmani7584.payloaddumper.ui.components.components.ModalBottomSheet
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.components.components.Text
import com.rajmani7584.payloaddumper.ui.components.components.topbar.TopBarDefaults
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExtractScreen(payloadState: PayloadState, appNavController: NavHostController, homeNavController: NavHostController) {

    val dataViewModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val cs = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val scrollBehavior = TopBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(topBar = {
        ScreenTopBar(
            title = "payload name...",
            nav = true,
            onNavClick = { homeNavController.popBackStack() },
            scrollBehavior = scrollBehavior
        )
    }) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (payloadState !is PayloadState.Ready) {
                Text("Payload not initialized yet!")
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(Dp.Unspecified, 840.dp)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
//                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (payloadState.manifest.partitionsList.any { it.incremental })
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Incremental Detected! Some Partitions can't be extracted!", fontFamily = FontFamily.Monospace, style = AppTheme.typography.label2, color = Color.Red)
                        }
                    }
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Partition Available (${payloadState.partitions.size})",
                            style = AppTheme.typography.h4
                        )
                        Spacer(Modifier.weight(1f))
                        Button(text = "Save All", onClick = {
                            cs.launch {
                                dataViewModel.dumpAll()
                            }
                        })
                    }
                }
                itemsIndexed(items = payloadState.partitions, key = { _, p -> p.id}) { _, p ->
                    Spacer(Modifier.height(12.dp))
                    ListItem(p, payloadState.errors[p.id])
                }
            }
        }
    }
}

@Composable
fun ListItem(partition: PartitionState, string: String?) {
    var sheetState by remember { mutableStateOf(false) }
    val dataModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val cs = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            sheetState = !sheetState
        }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(Modifier.size(48.dp)) {
            if (partition.status == PartStatus.RUNNING)
                CircularWavyProgressIndicator(
                    progress = { partition.progress },
                    amplitude = { if (partition.progress !in .04f.. .9f) 0f else 6f },
                    stroke = Stroke(width = 6.5f, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = 6.5f),
                    modifier = Modifier.fillMaxSize()
                )
            else if (partition.status == PartStatus.PENDING)
                CircularWavyProgressIndicator(
                    amplitude = 0f,
                    stroke = Stroke(width = 6.5f, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = 6.5f),
                    modifier = Modifier.fillMaxSize()
                )
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                Modifier.animateContentSize().size(36.dp).align(Alignment.Center)
                    .padding(if (partition.status == PartStatus.PENDING || partition.status == PartStatus.RUNNING) 4.dp else 0.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(partition.name, style = AppTheme.typography.label1)
            Spacer(Modifier.height(6.dp))

            Text(text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight(600))) {
                    append("${Utils.parseSize(partition.size)} ")
                }
                withStyle(SpanStyle(background = AppTheme.colors.primaryContainer.copy(alpha = .08f))) {
                    when (partition.status) {
                        PartStatus.PENDING -> append(" pending...")
                        PartStatus.RUNNING -> append(" extracting...")
                        PartStatus.COMPLETED -> append(" saved")
                        PartStatus.FAILED -> append(" failed! tap for info")
                        else -> {}
                    }
                }
            }, style = AppTheme.typography.body3)
        }
        Spacer(Modifier.weight(1f))
        when (partition.status) {
            PartStatus.PENDING, PartStatus.RUNNING -> {
                IconButton(onClick = {
                    cs.launch {
                        dataModel.cancel(partition)
                    }
                }, variant = IconButtonVariant.Ghost) {
                    Icon(Icons.Default.Close, contentDescription = "cancel")
                }
            }

            else -> {
                Button(enabled = !partition.incremental, variant = ButtonVariant.PrimaryOutlined, onClick = {
                    cs.launch {
                        dataModel.dump(partition)
                    }
                }) {
                    Text(if (partition.incremental) "Incremental" else "Save", style = AppTheme.typography.button)
                }
            }
        }
    }

    ModalBottomSheet(
        modifier = Modifier.padding(top = 100.dp),
        isVisible = sheetState,
        onDismissRequest = { sheetState = false }) {

        val clipManager = LocalClipboard.current
        Column(
            Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text(partition.name, style = AppTheme.typography.h2)
            Spacer(Modifier.height(20.dp))
            Text("Size: ${Utils.parseSize(partition.size)}")

            val hash = partition.hash?.decodeToString() ?: "Couldn't get hash"
            Text("Hash: ")
            Row(
                modifier = Modifier.background(
                    Color(0xFF101010),
                    RoundedCornerShape(6.dp)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    hash,
                    color = Color.White,
                    modifier = Modifier
                        .padding(8.dp).weight(1f),
                    fontFamily = FontFamily.Monospace
                )
                var copied by remember { mutableStateOf(false) }
                Image(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable(!copied) {
                            CoroutineScope(Dispatchers.IO).launch {
                                clipManager.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            "hash",
                                            AnnotatedString(hash)
                                        )
                                    )
                                )
                                copied = true
                                delay(3000)
                                copied = false
                            }
                        },
                    colorFilter = ColorFilter.lighting(Color.Black, Color.White)
                )
            }
        }
    }
}

private fun ByteString.decodeToString(): String {
    return toByteArray().joinToString("") { "%02x".format(it) }
}
