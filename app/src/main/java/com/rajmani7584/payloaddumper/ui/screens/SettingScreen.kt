package com.rajmani7584.payloaddumper.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajmani7584.payloaddumper.BuildConfig
import com.rajmani7584.payloaddumper.LocalSettings
import com.rajmani7584.payloaddumper.R
import com.rajmani7584.payloaddumper.model.BufSize
import com.rajmani7584.payloaddumper.model.ColorTheme
import com.rajmani7584.payloaddumper.model.DarkMode
import com.rajmani7584.payloaddumper.model.Utils
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.Colors
import com.rajmani7584.payloaddumper.ui.components.LocalColors
import com.rajmani7584.payloaddumper.ui.components.LocalContentColor
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.components.components.Slider
import com.rajmani7584.payloaddumper.ui.components.components.Switch
import com.rajmani7584.payloaddumper.ui.components.components.card.OutlinedCard
import com.rajmani7584.payloaddumper.ui.components.contentColorFor
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingScreen() {

    val model = LocalSettings.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val cs = rememberCoroutineScope()
    var concurrency by remember { mutableIntStateOf(settings.concurrency) }
    val ctx = LocalContext.current

//    var showUpdateDialog by remember { mutableStateOf(false) }
    val contentPad = 16.dp
    val shape = RoundedCornerShape(8.dp)

    Scaffold(topBar = { ScreenTopBar(title = "Settings") }) { innerPadding ->
        LazyColumn(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(contentPad)) {
                        Text(
                            stringResource(R.string.settings_concurrency, concurrency),
                            style = AppTheme.typography.h4
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Effective from next session", style = AppTheme.typography.body3)
                        Spacer(Modifier.height(12.dp))
                        Slider(
                            concurrency.toFloat(),
                            onValueChange = { concurrency = it.roundToInt() },
                            onValueChangeFinished = { cs.launch { model.setConcurrency(concurrency) } },
                            steps = model.processors - 2,
                            valueRange = 1f..model.processors.toFloat()
                        )
                    }
                }
            }

            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .padding(contentPad)
                            .fillMaxWidth()
                    ) {
                        Text("Buffer Size", style = AppTheme.typography.h4)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "for calculating sha checksum\nand also for downloading from remote",
                            style = AppTheme.typography.body3
                        )
                        Spacer(Modifier.height(12.dp))
                        val options =
                            BufSize.entries.associate { it.code to Utils.parseBufSize(it.code) }
                        ChoiceSelect(
                            options = options,
                            selected = settings.bufferSize.code,
                            onSelectChange = { cs.launch { model.setBufferSize(it) } },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(contentPad),

                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Verify Hash",
                                style = AppTheme.typography.h4
                            )
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = settings.verifyHash,
                                onCheckedChange = { cs.launch { model.setVerifyHash(it) } })
                        }
                        Spacer(Modifier.height(6.dp))

                        Text(
                            "verify checksum of each extracted partition",
                            style = AppTheme.typography.body3
                        )
                    }
                }
            }
            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(contentPad),

                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_auto_delete),
                                style = AppTheme.typography.h4
                            )
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = settings.autoDelete,
                                onCheckedChange = { cs.launch { model.setAutoDelete(it) } })
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_auto_delete_description),
                            style = AppTheme.typography.body3
                        )
                    }
                }
            }

            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(contentPad),

                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Overwrite Existing Partitions",
                                style = AppTheme.typography.h4
                            )
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = settings.overwrite,
                                onCheckedChange = { cs.launch { model.setOverWrite(it) } })
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "if disabled existing partitions will auto renamed to name(i++)",
                            style = AppTheme.typography.body3
                        )
                    }
                }
            }

            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .padding(contentPad)
                            .fillMaxWidth()
                    ) {
                        Text("Color Theme", style = AppTheme.typography.h4)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_theme_style_info),
                            style = AppTheme.typography.body3
                        )
                        Spacer(Modifier.height(12.dp))
                        val options =
                            ColorTheme.entries.associate { it.code to it.name.toDisplayName() }
                        ChoiceSelect(
                            options = options,
                            selected = settings.colorTheme.code,
                            onSelectChange = { cs.launch { model.setColorTheme(it) } },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            item {

                OutlinedCard(
                    shape = shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .padding(contentPad)
                            .fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_dark_theme),
                            style = AppTheme.typography.h4
                        )
                        Spacer(Modifier.height(12.dp))
                        val options =
                            DarkMode.entries.associate { it.code to it.name.toDisplayName() }
                        ChoiceSelect(
                            options = options,
                            selected = settings.darkTheme.code,
                            onSelectChange = { cs.launch { model.setDarkTheme(it) } },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                OutlinedCard(
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape),
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/rajmani7584/Payload-Dumper-Android/releases/latest".toUri()
                        )
                        ctx.startActivity(intent)
                    }
                ) {
                    Row(
                        Modifier.padding(contentPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                            style = AppTheme.typography.h4
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.settings_about_version_desc),
                            color = LocalContentColor.current.copy(alpha = .4f)
                        )
                    }
                }
            }

            item {
                OutlinedCard(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/rajmani7584".toUri()
                    )
                    ctx.startActivity(intent)
                }, shape = shape, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(contentPad)) {
                        Text("Author", style = AppTheme.typography.h4)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_about_github_username))
                            Spacer(Modifier.weight(1f))
                            Icon(
                                ImageVector.vectorResource(R.drawable.github_lockup_black),
                                contentDescription = null,
                                tint = LocalContentColor.current,
                                modifier = Modifier.height(16.dp)
                            )
                        }
                    }
                }
            }

            item {

                OutlinedCard(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/rajmani7584/Payload-Dumper-Android".toUri()
                    )
                    ctx.startActivity(intent)
                }, shape = shape, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(contentPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Source Code")
                        Spacer(Modifier.weight(1f))
                        Icon(
                            ImageVector.vectorResource(R.drawable.github_lockup_black),
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
            }
        }
//        if (showUpdateDialog)
//            BasicAlertDialog(
//                onDismissRequest = { showUpdateDialog = false },
//                modifier = Modifier
//                    .widthIn(AlertDialogDefaults.DialogMinWidth, AlertDialogDefaults.DialogMaxWidth)
//                    .clip(RoundedCornerShape(24.dp)).background(LocalColors.current.surface)
//            ) {
//                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
//                    LoadingIndicator()
//                    Spacer(Modifier.height(12.dp))
//                    Text("Checking for Updates....")
//                    Spacer(Modifier.height(12.dp))
//                    Row {
//                        Spacer(Modifier.weight(1f))
//                        Button(variant = ButtonVariant.Ghost, text = "Cancel", onClick = { showUpdateDialog = false })
//                    }
//                }
//            }
//        if (showUpdateDialog)
//            BasicAlertDialog(
//                onDismissRequest = { showUpdateDialog = false },
//                modifier = Modifier
//                    .widthIn(AlertDialogDefaults.DialogMinWidth, AlertDialogDefaults.DialogMaxWidth)
//                    .clip(RoundedCornerShape(36.dp))
//                    .background(LocalColors.current.surface)
//            ) {
//                Column(Modifier.padding(contentPad)) {
//                    Text("New Update Found!: v4.1", style = AppTheme.typography.h2)
//                    Spacer(Modifier.height(12.dp))
//                    HorizontalDivider()
//                    Spacer(Modifier.height(12.dp))
//                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                        val markdown = """
//                        ### Placeholder for change log of new updates
//                    """.trimIndent()
//                        MarkdownText(markdown)
//                        Spacer(Modifier.height(12.dp))
//                        HorizontalDivider()
//                        Row {
//                            Spacer(Modifier.weight(1f))
//                            Button(
//                                variant = ButtonVariant.Ghost,
//                                text = "Cancel",
//                                onClick = { showUpdateDialog = false })
//                            Button(
//                                variant = ButtonVariant.Ghost,
//                                text = "Download",
//                                onClick = { showUpdateDialog = false })
//                        }
//                    }
//                }
//            }
    }
}

@Composable
fun ChoiceSelect(
    modifier: Modifier = Modifier,
    options: Map<Int, String>,
    selected: Int,
    onSelectChange: (Int) -> Unit,
    content: @Composable (code: Int, isSelected: Boolean) -> Unit = { code, isSelected ->
        Row {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = LocalColors.current.surface,
                    modifier = Modifier.size(AppTheme.typography.label2.fontSize.value.dp + 4.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = options[code] ?: "Unknown",
                style = AppTheme.typography.label2,
                color = if (isSelected)
                    AppTheme.colors.onPrimary
                else
                    LocalContentColor.current,
            )
        }
    },
) {
    val offsetAnim by animateFloatAsState(
        targetValue = options.keys.indexOf(selected).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicator"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
//            .border(BorderStroke(1.dp, AppTheme.colors.primary), CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(1f / options.size)
                .fillMaxHeight()
                .layout { m, c ->
                    val placeable = m.measure(c)
                    val x = (c.maxWidth * offsetAnim).toInt()
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(x, 0)
                    }
                }
                .clip(RoundedCornerShape(6.dp))
                .background(AppTheme.colors.onSurface)
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (code, _) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.colors.primary.copy(alpha = .1f))
                        .clickable { onSelectChange(code) },
                    contentAlignment = Alignment.Center
                ) {
                    content(code, selected == code)
                }
            }
        }
    }
}
fun String.toDisplayName(): String = split("_", "-", " ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }