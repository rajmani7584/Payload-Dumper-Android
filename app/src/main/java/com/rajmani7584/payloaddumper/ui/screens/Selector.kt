package com.rajmani7584.payloaddumper.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.R
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.ExplorerModel
import com.rajmani7584.payloaddumper.model.FileData
import com.rajmani7584.payloaddumper.model.PayloadType
import com.rajmani7584.payloaddumper.ui.components.components.Button
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Selector(appNavController: NavHostController) {
    val mainModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val dataModel: ExplorerModel = viewModel()
    val currentPath by dataModel.lastDirectory
    val externalStoragePath = mainModel.externalStorage

    val isDir =
        appNavController.currentBackStackEntry?.arguments?.getBoolean("directory") == true

    LaunchedEffect(Unit) {
        dataModel.setLastDirectory(mainModel.lastDirectory.value)
    }
    val list by dataModel.list
    val canGoBack by dataModel.canGoBack
    val canWrite by dataModel.canWrite
    val invalidPath by dataModel.invalidPath
    val scroll = rememberScrollState(0)

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(
                    R.string.selector_header,
                    if (isDir) stringResource(R.string.selector_director) else stringResource(
                        R.string.payload
                    )
                ),
                nav = true,
                onNavClick = {
                    appNavController.popBackStack()
                },
                actions = {
                    if (!isDir && canWrite) {
                        Button(
                            onClick = {
                                mainModel.setOutputDirectory(currentPath)
                                appNavController.popBackStack()
                            },
                            text = "Select"
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val fileModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)

        val headerHeightPx = with(LocalDensity.current) { 45.dp.toPx() }
        val minHeightPx = with(LocalDensity.current) { 0.dp.toPx() }
        val headerHeight = remember { mutableFloatStateOf(headerHeightPx) }

        val scrollBehavior = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val newHeight = headerHeight.floatValue + delta
                    headerHeight.floatValue = newHeight.coerceIn(minHeightPx, headerHeightPx)
                    return Offset(0f, if (newHeight in minHeightPx..headerHeightPx) delta else 0f)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior)
        )
        {
            Row(
                Modifier
                    .horizontalScroll(scroll)
                    .height(with(LocalDensity.current) { headerHeight.floatValue.toDp() })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val directories =
                    currentPath.replace(externalStoragePath, "Internal Storage").split("/")
                for (index in directories.indices) {

                    if (index != 0)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "",
                            modifier = Modifier.fillMaxHeight()
                        )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(enabled = index != directories.size - 1, onClick = {
                                dataModel.setLastDirectory(
                                    directories.subList(0, index + 1).joinToString("/")
                                        .replace("Internal Storage", externalStoragePath)
                                )
                            })
                    ) {
                        Text(
                            directories[index],
                            fontStyle = FontStyle.Italic,
                            maxLines = 1
                        )
                    }
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                            alpha = .15f,
                            red = .9f,
                            green = .93f,
                            blue = .95f
                        )
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (list.isEmpty()) {
                    item {
                        if (invalidPath) Text(
                            stringResource(R.string.selector_screen_invalid_path),
                            color = Color.Red
                        )
                        else Text(
                            if (File(currentPath).canRead()) stringResource(R.string.selector_empty) else stringResource(
                                R.string.selector_no_permission
                            )
                        )
                    }
                } else {
                    items(list.size) {
                        val file = list[it]
                        if (file is FileData.Folder) {
                            Box(modifier = fileModifier.clickable(true, onClick = {
                                dataModel.setLastDirectory("$currentPath/${file.name}")
                            })) {
                                FileButton(
                                    file,
                                    Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            if (!isDir) {
                                Box(fileModifier.clickable(true, onClick = {
                                    mainModel.init(
                                        PayloadType.LocalPayload("$currentPath/${file.name}")
                                    )
                                    appNavController.popBackStack()
                                })) {
                                    FileButton(
                                        file,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(currentPath) {
            scroll.animateScrollTo(scroll.maxValue)
            mainModel.setLastDirectory(currentPath)
        }

        BackHandler(canGoBack, onBack = {
            dataModel.setLastDirectory(File(currentPath).parentFile?.absolutePath.toString())
        })
    }
}

@Composable
fun FileButton(
    file: FileData,
    modifier: Modifier
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (file) {
                is FileData.File -> Icons.Default.FilePresent
                is FileData.Folder -> Icons.Default.Folder
            },
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
