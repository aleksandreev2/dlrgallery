package com.dlrgallery.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.MediaImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun PhotoGrid(
    images: List<MediaImage>,
    showPartialAccessBanner: Boolean,
    onChangeAccess: () -> Unit,
    onPhotoClick: (MediaImage) -> Unit,
) {
    val groups = remember(images) { groupPhotosByDay(images) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (showPartialAccessBanner) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PartialAccessBanner(onChangeAccess = onChangeAccess)
            }
        }

        groups.forEach { group ->
            item(
                key = "header-${group.date}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DateHeader(
                    title = formatDateHeader(group.date),
                    count = formatPhotoCount(group.images.size),
                )
            }
            items(group.images, key = MediaImage::id) { image ->
                PhotoTile(image = image, onClick = { onPhotoClick(image) })
            }
        }
    }
}

@Composable
fun AlbumGrid(
    albums: List<GalleryAlbum>,
    showPartialAccessBanner: Boolean,
    onChangeAccess: () -> Unit,
    onAlbumClick: (GalleryAlbum) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showPartialAccessBanner) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PartialAccessBanner(onChangeAccess = onChangeAccess)
            }
        }
        items(albums, key = GalleryAlbum::id) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
fun PermissionRequiredState(
    onRequestAccess: () -> Unit,
) {
    MessageState(
        icon = Icons.Outlined.Lock,
        title = "Нужен доступ к фотографиям",
        message = "DLR Gallery читает изображения только на устройстве. Фотографии никуда не отправляются.",
        action = {
            Button(onClick = onRequestAccess) {
                Text("Разрешить доступ")
            }
        },
    )
}

@Composable
fun LoadingMediaState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Сканируем фотографии…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyMediaState() {
    MessageState(
        icon = Icons.Outlined.Image,
        title = "Фотографий пока нет",
        message = "Для проверки перетащите несколько JPG или PNG прямо в окно эмулятора, затем нажмите «Обновить».",
    )
}

@Composable
fun EmptyAlbumsState() {
    MessageState(
        icon = Icons.Outlined.Collections,
        title = "Альбомов пока нет",
        message = "Альбомы появятся автоматически после добавления фотографий на устройство.",
    )
}

@Composable
fun MediaErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    MessageState(
        icon = Icons.Outlined.ErrorOutline,
        title = "Не удалось загрузить галерею",
        message = message,
        action = {
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Повторить")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    image: MediaImage,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = image.displayName.ifBlank { "Фотография" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = image.mimeType.ifBlank { "image/*" }
                                putExtra(Intent.EXTRA_STREAM, image.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться фото"))
                        },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Поделиться")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.88f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Surface(color = Color.Black.copy(alpha = 0.9f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.76f),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "${image.width} × ${image.height}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatFileSize(image.sizeBytes),
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    ) { padding ->
        AsyncImage(
            model = image.uri,
            contentDescription = image.displayName,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PhotoTile(
    image: MediaImage,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun AlbumCard(
    album: GalleryAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = album.coverUri,
            contentDescription = "Обложка альбома ${album.name}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.32f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatPhotoCount(album.imageCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PartialAccessBanner(
    onChangeAccess: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Показаны только выбранные фото",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Android ограничил доступ к остальной галерее.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
            FilledTonalButton(onClick = onChangeAccess) {
                Text("Изменить")
            }
        }
    }
}

@Composable
private fun DateHeader(
    title: String,
    count: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(20.dp))
                action()
            }
        }
    }
}

private data class PhotoDayGroup(
    val date: LocalDate,
    val images: List<MediaImage>,
)

private fun groupPhotosByDay(images: List<MediaImage>): List<PhotoDayGroup> {
    val zone = ZoneId.systemDefault()
    return images
        .groupBy { image ->
            Instant.ofEpochMilli(image.dateTakenMillis)
                .atZone(zone)
                .toLocalDate()
        }
        .map { (date, items) -> PhotoDayGroup(date = date, images = items) }
        .sortedByDescending(PhotoDayGroup::date)
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
    }
}

fun formatPhotoCount(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    val word = when {
        lastTwo in 11..14 -> "фото"
        last == 1 -> "фото"
        last in 2..4 -> "фото"
        else -> "фото"
    }
    return "${count.toString().reversed().chunked(3).joinToString(" ").reversed()} $word"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Неизвестный размер"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    val unitIndex = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(unitIndex.toDouble())
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}
