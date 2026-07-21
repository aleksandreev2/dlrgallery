package com.dlrgallery.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dlrgallery.app.data.MediaImage
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun ImmersiveVideoPlayer(
    video: MediaImage,
    isFavorite: Boolean,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (MediaImage) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var savedPosition by rememberSaveable(video.id) { mutableLongStateOf(0L) }
    var controlsVisible by rememberSaveable(video.id) { mutableStateOf(true) }
    var showInfo by rememberSaveable(video.id) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(video.id) { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMillis by remember { mutableLongStateOf(savedPosition) }
    var durationMillis by remember { mutableLongStateOf(video.durationMillis.coerceAtLeast(0L)) }
    var bufferedMillis by remember { mutableLongStateOf(0L) }
    var scrubPosition by remember { mutableFloatStateOf(savedPosition.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var repeatEnabled by rememberSaveable(video.id) { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<PlaybackException?>(null) }

    fun showControls() {
        controlsVisible = true
        interactionToken += 1
    }

    val player = remember(video.uri) {
        ExoPlayer.Builder(context.applicationContext)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MILLIS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MILLIS)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(video.uri))
                if (savedPosition > 0L) seekTo(savedPosition)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                val playerDuration = player.duration
                if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                    durationMillis = playerDuration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            savedPosition = player.currentPosition.coerceAtLeast(0L)
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (true) {
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            positionMillis = currentPosition
            savedPosition = currentPosition
            bufferedMillis = player.bufferedPosition.coerceAtLeast(0L)
            val playerDuration = player.duration
            if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                durationMillis = playerDuration
            }
            if (!isScrubbing) {
                scrubPosition = currentPosition.toFloat()
            }
            delay(if (player.isPlaying) 250L else 500L)
        }
    }

    LaunchedEffect(repeatEnabled, player) {
        player.repeatMode = if (repeatEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(controlsVisible, isPlaying, interactionToken, showInfo, showDeleteDialog) {
        if (controlsVisible && isPlaying && !showInfo && !showDeleteDialog) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, activity) {
        activity?.setVideoSystemBarsVisible(controlsVisible)
    }
    DisposableEffect(activity) {
        onDispose { activity?.setVideoSystemBarsVisible(true) }
    }

    BackHandler(enabled = showDeleteDialog || showInfo) {
        when {
            showDeleteDialog -> showDeleteDialog = false
            showInfo -> showInfo = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.id) {
                    detectTapGestures(
                        onTap = {
                            if (controlsVisible) controlsVisible = false else showControls()
                        },
                        onDoubleTap = { offset ->
                            val seekDelta = if (offset.x < size.width / 2f) {
                                -SEEK_INCREMENT_MILLIS
                            } else {
                                SEEK_INCREMENT_MILLIS
                            }
                            player.seekTo(
                                (player.currentPosition + seekDelta)
                                    .coerceIn(0L, durationMillis.coerceAtLeast(0L)),
                            )
                            showControls()
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.86f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName.ifBlank { "Видео" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = video.bucketName.ifBlank { "Без альбома" },
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        onToggleFavorite(video.id)
                        showControls()
                    },
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Убрать из избранного" else "Добавить в избранное",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && playbackError == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoControlButton(
                    icon = Icons.Outlined.Replay10,
                    contentDescription = "Назад на 10 секунд",
                    size = 54,
                    onClick = {
                        player.seekBack()
                        showControls()
                    },
                )
                VideoControlButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                    size = 76,
                    emphasized = true,
                    onClick = {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                        }
                        if (isPlaying) player.pause() else player.play()
                        showControls()
                    },
                )
                VideoControlButton(
                    icon = Icons.Outlined.Forward10,
                    contentDescription = "Вперёд на 10 секунд",
                    size = 54,
                    onClick = {
                        player.seekForward()
                        showControls()
                    },
                )
            }
        }

        if (playbackState == Player.STATE_BUFFERING && playbackError == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Подготовка видео…",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        playbackError?.let { error ->
            PlaybackErrorCard(
                error = error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                onRetry = {
                    playbackError = null
                    player.prepare()
                    player.play()
                    showControls()
                },
                onOpenExternal = { openVideoExternally(context, video) },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && showInfo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 170.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            VideoInfoCard(video = video, durationMillis = durationMillis)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 36.dp, bottom = 8.dp),
            ) {
                val safeDuration = durationMillis.coerceAtLeast(1L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatVideoDuration(if (isScrubbing) scrubPosition.toLong() else positionMillis),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = scrubPosition.coerceIn(0f, safeDuration.toFloat()),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubPosition = value
                            showControls()
                        },
                        onValueChangeFinished = {
                            player.seekTo(scrubPosition.toLong())
                            isScrubbing = false
                            showControls()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        valueRange = 0f..safeDuration.toFloat(),
                    )
                    Text(
                        text = formatVideoDuration(durationMillis),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                if (durationMillis > 0L && bufferedMillis > positionMillis) {
                    Text(
                        text = "Загружено ${((bufferedMillis * 100L) / durationMillis).coerceIn(0L, 100L)}%",
                        modifier = Modifier.align(Alignment.End),
                        color = Color.White.copy(alpha = 0.48f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoAction(
                        icon = Icons.Outlined.Share,
                        label = "Поделиться",
                        onClick = {
                            shareVideo(context, video)
                            showControls()
                        },
                    )
                    VideoAction(
                        icon = Icons.Outlined.Repeat,
                        label = if (repeatEnabled) "Повтор вкл." else "Повтор",
                        active = repeatEnabled,
                        onClick = {
                            repeatEnabled = !repeatEnabled
                            showControls()
                        },
                    )
                    VideoAction(
                        icon = Icons.Outlined.Info,
                        label = "Сведения",
                        active = showInfo,
                        onClick = {
                            showInfo = !showInfo
                            showControls()
                        },
                    )
                    VideoAction(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "В корзину",
                        onClick = {
                            player.pause()
                            showDeleteDialog = true
                            showControls()
                        },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Переместить видео в корзину?") },
            text = {
                Text("Android покажет системное подтверждение. Видео можно будет восстановить из корзины устройства.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(video)
                    },
                ) {
                    Text("В корзину")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun VideoControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: Int,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (emphasized) {
            Color.White.copy(alpha = 0.96f)
        } else {
            Color.Black.copy(alpha = 0.52f)
        },
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size((size * if (emphasized) 0.52f else 0.46f).dp),
                tint = if (emphasized) Color.Black else Color.White,
            )
        }
    }
}

@Composable
private fun VideoAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun VideoInfoCard(
    video: MediaImage,
    durationMillis: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xF21B1B1E),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = video.displayName.ifBlank { "Видео" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            InfoLine("Длительность", formatVideoDuration(durationMillis))
            InfoLine("Разрешение", "${video.width} × ${video.height}")
            InfoLine("Размер", formatVideoFileSize(video.sizeBytes))
            InfoLine("Альбом", video.bucketName.ifBlank { "Без альбома" })
            InfoLine("Формат", video.mimeType.ifBlank { "video/*" })
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackErrorCard(
    error: PlaybackException,
    modifier: Modifier,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF2222226),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Не удалось воспроизвести видео",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error.errorCodeName,
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenExternal) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Другой плеер")
                }
                Button(onClick = onRetry) {
                    Text("Повторить")
                }
            }
        }
    }
}

private fun shareVideo(context: Context, video: MediaImage) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = video.mimeType.ifBlank { "video/*" }
        putExtra(Intent.EXTRA_STREAM, video.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "Видео", video.uri)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Поделиться видео"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не найдено приложение для отправки", Toast.LENGTH_SHORT).show()
    }
}

private fun openVideoExternally(context: Context, video: MediaImage) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(video.uri, video.mimeType.ifBlank { "video/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не найден другой видеоплеер", Toast.LENGTH_SHORT).show()
    }
}

private fun formatVideoDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatVideoFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(units.indices)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) "${value.toInt()} ${units[group]}" else "%.1f %s".format(value, units[group])
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.setVideoSystemBarsVisible(visible: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (visible) {
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private const val SEEK_INCREMENT_MILLIS = 10_000L
private const val CONTROLS_TIMEOUT_MILLIS = 3_500L
