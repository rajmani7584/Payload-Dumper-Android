package com.rajmani7584.payloaddumper.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajmani7584.payloaddumper.R
import com.rajmani7584.payloaddumper.models.DataViewModel
import com.rajmani7584.payloaddumper.ui.customviews.CSwitch

@Composable
fun SettingScreen(dataModel: DataViewModel) {
    val isDynamicColor by dataModel.isDynamicColor.collectAsState()
    val isDarkTheme by dataModel.isDarkTheme.collectAsState()
    val isListView by dataModel.isListView.collectAsState()
    val concurrency by dataModel.concurrency.collectAsState()
    val autoDelete by dataModel.autoDelete.collectAsState()
    val isExtracting by dataModel.isExtracting
    val ctx = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column {
            Text(
                text = "SETTINGS",
                fontFamily = FontFamily(Font(R.font.doto)),
                style = MaterialTheme.typography.headlineMedium
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    var showConcurrentOpts by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable (!isExtracting) { showConcurrentOpts = !showConcurrentOpts },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Concurrency")
                        Box(Modifier.wrapContentWidth()) {
                            Text(
                                "$concurrency",
                                Modifier
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Justify
                            )
                            DropdownMenu(
                                showConcurrentOpts,
                                onDismissRequest = { showConcurrentOpts = false }
                            ) {
                                for (i in 1..8) {
                                    DropdownMenuItem(text = {
                                        Text(
                                            "$i",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }, onClick = {
                                        dataModel.setConcurrency(i)
                                        showConcurrentOpts = false
                                    })
                                    if (i != 8) HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.2f
                                        ), modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    var showListOpts by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showListOpts = !showListOpts },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default View")
                        Box(
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                if (isListView) "List" else "Grid",
                                maxLines = 1,
                                modifier = Modifier
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 16.sp
                            )
                            DropdownMenu(
                                showListOpts,
                                onDismissRequest = { showListOpts = false },
                            ) {
                                DropdownMenuItem(text = {
                                    Text(
                                        "List",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }, onClick = {
                                    dataModel.setListView(true)
                                    showListOpts = false
                                })
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(text = {
                                    Text(
                                        "Grid",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }, onClick = {
                                    dataModel.setListView(false)
                                    showListOpts = false
                                })
                            }
                        }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(!isExtracting) { dataModel.setAutoDelete(!autoDelete) }) {
                        Column {
                            Text("Auto Delete")
                            Text("Delete output file on failure", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        CSwitch(
                            enabled = !isExtracting,
                            isDarkTheme = isDarkTheme,
                            isDynamicColor = isDynamicColor,
                            checked = autoDelete,
                            onCheckedChange = { dataModel.setAutoDelete(it) })
                    }
                }
                item {
                    var showThemeOpts by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showThemeOpts = !showThemeOpts },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    )
                    {
                        Column {
                            Text("Theme Style")
                            Text(
                                "System require android-12+",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Box(
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                if (isDynamicColor) "System" else "App",
                                maxLines = 1,
                                modifier = Modifier
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 16.sp
                            )
                            DropdownMenu(
                                showThemeOpts,
                                onDismissRequest = { showThemeOpts = false }
                            ) {
                                DropdownMenuItem(text = {
                                    Text(
                                        "System",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }, onClick = {
                                    dataModel.setDynamicColor(true)
                                    showThemeOpts = false
                                })
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(text = {
                                    Text(
                                        "App",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }, onClick = {
                                    dataModel.setDynamicColor(false)
                                    showThemeOpts = false
                                })
                            }
                        }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(!isDynamicColor) { dataModel.setDarkTheme(!isDarkTheme) }) {
                        Text("Dark Theme")
                        Spacer(Modifier.weight(1f))
                        CSwitch(
                            enabled = !isDynamicColor,
                            checked = isDarkTheme,
                            isDarkTheme = isDarkTheme,
                            isDynamicColor = isDynamicColor,
                            onCheckedChange = { dataModel.setDarkTheme(it) })
                    }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/rajmani7584/Payload-Dumper-Android/releases/latest")
                            )
                            ctx.startActivity(intent)
                        }) {
                        val version =
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                                ?: "Unable to query"
                        Text(
                            "Version: $version",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Check for Update",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                item {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/rajmani7584/Payload-Dumper-Android")
                            )
                            ctx.startActivity(intent)
                        }) {
                        Text(
                            "Source Code",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "@GitHub",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                item {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/rajmani7584")
                                )
                            ctx.startActivity(intent)
                        }) {
                        Text(
                            "GitHub",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "@Rajmani7584",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}