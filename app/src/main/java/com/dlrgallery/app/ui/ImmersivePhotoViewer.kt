package com.dlrgallery.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dlrgallery.app.data.MediaImage
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImmersivePhotoViewer(
    images: List<MediaImage>,
    initialPhotoId: Long,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onEdit: (MediaImage) -> Unit,
    onBack: () -> Unit,
) {
    if (images.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val initialPage = remember(images, initialPhotoId) {
        images.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = images::size,
    )
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var currentScale by remember { mutableFloatStateOf(1f) }
    var lastPage by remember { mutableIntStateOf(initialPage) }

    val currentPage = pagerState.currentPage.coerceIn(images.indices)
    val currentImage = images[currentPage]
    val isFavorite = currentImage.id in favoriteIds

    LaunchedEffect(currentPage) {
        if (lastPage != currentPage) {
            lastPage = currentPage
            currentScale = 1f
            showInfo = false
            controlsVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = currentScale <= 1.01f,
            key = { page -> images[page].id },
        ) { page ->
            ZoomablePhoto(
                image = images[page],
                active = page == currentPage,
                onSingleTap = { controlsVisible = !controlsVisible },
                onScaleChanged = { scale ->
                    if (page == pagerState.currentPage) {
                        currentScale = scale
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.72f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currentPage + 1} из ${images.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = currentImage.displayName.ifBlank { "Фотография" },
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && showInfo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PhotoInfoCard(image = currentImage)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.82f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerAction(
                        icon = Icons.Outlined.Share,
                        label = "Поделиться",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = currentImage.mimeType.ifBlank { "image/*" }
                                putExtra(Intent.EXTRA_STREAM, currentImage.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Поделиться фото"),
                            )
                        },
                    )
                    ViewerAction(
                        icon = Icons.Outlined.Edit,
                        label = "Изменить",
                        onClick = { onEdit(currentImage) },
                    )
                    ViewerAction(
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = if (isFavorite) "В избранном" else "Избранное",
                        onClick = { onToggleFavorite(currentImage.id) },
                    )
                    ViewerAction(
                        icon = Icons.Outlined.Info,
                        label = "Сведения",
                        onClick = { showInfo = !showInfo },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    image: MediaImage,
    active: Boolean,
    onSingleTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
) {
    var scale by remember(image.id) { mutableFloatStateOf(1f) }
    var offset by remember(image.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(image.id) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(active, scale) {
        if (active) {
            onScaleChanged(scale)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { newSize ->
                containerSize = newSize
                if (scale > 1.01f) {
                    offset = clampOffset(offset, scale, newSize)
                }
            }
            .pointerInput(image.id, active) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        val targetScale = if (scale > 1.01f) 1f else 2.5f
                        scale = targetScale
                        offset = Offset.Zero
                        if (active) {
                            onScaleChanged(targetScale)
                        }
                    },
                )
            }
            .pointerInput(image.id, active) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val pressedPointers = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = pressedPointers > 1
                        val panningZoomedImage = scale > 1.01f && panChange.getDistance() > 0.5f

                        if (multiTouch || panningZoomedImage) {
                            val oldScale = scale
                            val calculatedScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                            val nextScale = if (calculatedScale <= 1.01f) 1f else calculatedScale

                            offset = if (nextScale == 1f) {
                                Offset.Zero
                            } else {
                                val centroid = event.calculateCentroid()
                                val viewportCenter = Offset(
                                    x = containerSize.width / 2f,
                                    y = containerSize.height / 2f,
                                )
                                val scaleRatio = nextScale / oldScale
                                val focalPoint = centroid - viewportCenter
                                val zoomAdjustedOffset = if (multiTouch && nextScale != oldScale) {
                                    offset * scaleRatio + focalPoint * (1f - scaleRatio)
                                } else {
                                    offset
                                }

                                clampOffset(
                                    value = zoomAdjustedOffset + panChange,
                                    scale = nextScale,
                                    size = containerSize,
                                )
                            }
                            scale = nextScale
                            event.changes.forEach { change -> change.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.displayName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

private fun clampOffset(
    value: Offset,
    scale: Float,
    size: IntSize,
): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        x = value.x.coerceIn(-maxX, maxX),
        y = value.y.coerceIn(-maxY, maxY),
    )
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PhotoInfoCard(image: MediaImage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xEE1A1A1A),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = image.displayName.ifBlank { "Фотография" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${image.width} × ${image.height} · ${formatViewerFileSize(image.sizeBytes)}",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = image.bucketName.ifBlank { "Без альбома" },
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatViewerFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ")
    val group = (ln(bytes.toDouble()) / ln(1024.0))
        .toInt()
        .coerceIn(units.indices)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) {
        "${value.toInt()} ${units[group]}"
    } else {
        "%.1f %s".format(value, units[group])
    }
}
