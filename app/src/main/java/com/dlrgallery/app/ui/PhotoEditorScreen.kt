package com.dlrgallery.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.CropPreset
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.PhotoAdjustments
import com.dlrgallery.app.data.editorColorMatrixValues
import com.dlrgallery.app.data.exportEditedCopy
import com.dlrgallery.app.data.loadEditorPreview
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class EditorTool(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Crop("Обрезка", Icons.Outlined.Crop),
    Rotate("Поворот", Icons.Outlined.RotateRight),
    Brightness("Свет", Icons.Outlined.Brightness6),
    Contrast("Контраст", Icons.Outlined.Contrast),
    Saturation("Цвет", Icons.Outlined.ColorLens),
    Warmth("Тепло", Icons.Outlined.ColorLens),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    image: MediaImage,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var previewBitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    var adjustments by remember(image.id) { mutableStateOf(PhotoAdjustments()) }
    var selectedTool by remember { mutableStateOf(EditorTool.Crop) }
    var isLoading by remember(image.id) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var loadError by remember(image.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(image.id) {
        isLoading = true
        loadError = null
        try {
            previewBitmap = loadEditorPreview(context, image)
        } catch (error: Throwable) {
            loadError = error.message ?: "Не удалось открыть изображение"
        } finally {
            isLoading = false
        }
    }

    val colorFilter = remember(adjustments) {
        ColorFilter.colorMatrix(ColorMatrix(editorColorMatrixValues(adjustments)))
    }
    val defaultAdjustments = remember { PhotoAdjustments() }

    Scaffold(
        containerColor = Color(0xFF101010),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Редактор",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    if (adjustments != defaultAdjustments) {
                        TextButton(
                            onClick = { adjustments = defaultAdjustments },
                            enabled = !isSaving,
                        ) {
                            Text("Сбросить")
                        }
                    }
                    TextButton(
                        enabled = previewBitmap != null && !isLoading && !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    exportEditedCopy(context, image, adjustments)
                                    onSaved()
                                } catch (error: Throwable) {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "Не удалось сохранить фотографию",
                                    )
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                    ) {
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            EditorPreview(
                bitmap = previewBitmap,
                adjustments = adjustments,
                isLoading = isLoading,
                errorMessage = loadError,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 10.dp),
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(EditorTool.entries) { tool ->
                            EditorToolButton(
                                tool = tool,
                                selected = tool == selectedTool,
                                onClick = { selectedTool = tool },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    EditorToolControls(
                        tool = selectedTool,
                        adjustments = adjustments,
                        onAdjustmentsChange = { adjustments = it },
                    )
                }
            }
        }

        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = "Сохраняем копию…",
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(
    bitmap: Bitmap?,
    adjustments: PhotoAdjustments,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            errorMessage != null -> Text(
                text = errorMessage,
                color = Color.White,
                modifier = Modifier.padding(24.dp),
            )
            bitmap != null -> {
                val sourceAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val rotatedAspect = if (adjustments.rotationQuarterTurns.mod(2) == 0) {
                    sourceAspect
                } else {
                    1f / sourceAspect
                }
                val frameAspect = adjustments.cropPreset.aspectRatio ?: rotatedAspect
                val filter = remember(adjustments) {
                    ColorFilter.colorMatrix(ColorMatrix(editorColorMatrixValues(adjustments)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .aspectRatio(frameAspect.coerceIn(0.45f, 2.2f))
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Предпросмотр редактирования",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = adjustments.rotationQuarterTurns.mod(4) * 90f
                                scaleX = if (adjustments.flipHorizontal) -1f else 1f
                            },
                        contentScale = if (adjustments.cropPreset == CropPreset.Original) {
                            ContentScale.Fit
                        } else {
                            ContentScale.Crop
                        },
                        colorFilter = filter,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    tool: EditorTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.large,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EditorToolControls(
    tool: EditorTool,
    adjustments: PhotoAdjustments,
    onAdjustmentsChange: (PhotoAdjustments) -> Unit,
) {
    when (tool) {
        EditorTool.Crop -> {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CropPreset.entries) { preset ->
                    FilterChip(
                        selected = adjustments.cropPreset == preset,
                        onClick = { onAdjustmentsChange(adjustments.copy(cropPreset = preset)) },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
        EditorTool.Rotate -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onAdjustmentsChange(
                            adjustments.copy(
                                rotationQuarterTurns = (adjustments.rotationQuarterTurns + 1).mod(4),
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = null)
                    Text("Повернуть", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onAdjustmentsChange(
                            adjustments.copy(flipHorizontal = !adjustments.flipHorizontal),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Flip, contentDescription = null)
                    Text("Отразить", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        EditorTool.Brightness -> AdjustmentSlider(
            label = "Яркость",
            value = adjustments.brightness,
            valueRange = -1f..1f,
            displayValue = "${(adjustments.brightness * 100).roundToInt()}",
            onValueChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
        )
        EditorTool.Contrast -> AdjustmentSlider(
            label = "Контраст",
            value = adjustments.contrast,
            valueRange = 0.5f..1.5f,
            displayValue = "${((adjustments.contrast - 1f) * 100).roundToInt()}",
            onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
        )
        EditorTool.Saturation -> AdjustmentSlider(
            label = "Насыщенность",
            value = adjustments.saturation,
            valueRange = 0f..2f,
            displayValue = "${((adjustments.saturation - 1f) * 100).roundToInt()}",
            onValueChange = { onAdjustmentsChange(adjustments.copy(saturation = it)) },
        )
        EditorTool.Warmth -> AdjustmentSlider(
            label = "Температура",
            value = adjustments.warmth,
            valueRange = -1f..1f,
            displayValue = "${(adjustments.warmth * 100).roundToInt()}",
            onValueChange = { onAdjustmentsChange(adjustments.copy(warmth = it)) },
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
