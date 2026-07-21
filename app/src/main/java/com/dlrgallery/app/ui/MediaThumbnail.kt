package com.dlrgallery.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dlrgallery.app.data.MediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ThumbnailState {
    data object Loading : ThumbnailState
    data class Ready(val bitmap: ImageBitmap) : ThumbnailState
    data object Fallback : ThumbnailState
}

@Composable
fun MediaThumbnail(
    mediaStoreId: Long,
    uri: Uri,
    isVideo: Boolean,
    durationMillis: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val state by produceState<ThumbnailState>(
        initialValue = ThumbnailState.Loading,
        key1 = uri,
        key2 = mediaStoreId,
        key3 = isVideo,
    ) {
        value = withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                loadSystemThumbnail(
                    resolver = resolver,
                    uri = uri,
                    mediaStoreId = mediaStoreId,
                    isVideo = isVideo,
                )
            }.getOrNull()

            if (bitmap != null) {
                ThumbnailState.Ready(bitmap.asImageBitmap())
            } else {
                ThumbnailState.Fallback
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            ThumbnailState.Loading -> Icon(
                imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            is ThumbnailState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
            ThumbnailState.Fallback -> ThumbnailFallback(
                uri = uri,
                contentDescription = contentDescription,
                contentScale = contentScale,
            )
        }

        if (isVideo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Видео",
                        modifier = Modifier.size(16.dp),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                    if (durationMillis > 0L) {
                        Text(
                            text = formatMediaDuration(durationMillis),
                            modifier = Modifier.padding(start = 3.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaThumbnail(
    image: MediaImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    MediaThumbnail(
        mediaStoreId = image.mediaStoreId,
        uri = image.uri,
        isVideo = image.isVideo,
        durationMillis = image.durationMillis,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
private fun ThumbnailFallback(
    uri: Uri,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    var failed by remember(uri) { mutableStateOf(false) }

    if (failed) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = "Не удалось загрузить миниатюру",
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    } else {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = contentScale,
            onError = { failed = true },
        )
    }
}

@Suppress("DEPRECATION")
private fun loadSystemThumbnail(
    resolver: android.content.ContentResolver,
    uri: Uri,
    mediaStoreId: Long,
    isVideo: Boolean,
): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    resolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX), null)
} else if (isVideo) {
    MediaStore.Video.Thumbnails.getThumbnail(
        resolver,
        mediaStoreId,
        MediaStore.Video.Thumbnails.MINI_KIND,
        null,
    )
} else {
    MediaStore.Images.Thumbnails.getThumbnail(
        resolver,
        mediaStoreId,
        MediaStore.Images.Thumbnails.MINI_KIND,
        null,
    )
}

private fun formatMediaDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val THUMBNAIL_SIZE_PX = 512
