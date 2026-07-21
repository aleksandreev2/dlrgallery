package com.dlrgallery.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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

@Composable
fun PhotoGrid(
    images: List<MediaImage>,
    gridColumns: Int,
    showPartialAccessBanner: Boolean,
    onChangeAccess: () -> Unit,
    onPhotoClick: (MediaImage) -> Unit,
    selectedIds: Set<Long> = emptySet(),
    selectionMode: Boolean = false,
    groupByDate: Boolean = true,
    newestDatesFirst: Boolean = true,
    onPhotoLongClick: (MediaImage) -> Unit = {},
    onPhotoSelectionToggle: (MediaImage) -> Unit = {},
) {
    val groups = remember(images, newestDatesFirst) {
        groupPhotosByDay(images, newestDatesFirst)
    }
    val columns = gridColumns.coerceIn(2, 5)
    val spacing = if (columns >= 5) 2.dp else 3.dp
    val sidePadding = if (columns >= 4) 4.dp else 6.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = sidePadding,
            end = sidePadding,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (showPartialAccessBanner) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PartialAccessBanner(onChangeAccess = onChangeAccess)
            }
        }

        if (groupByDate) {
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
                    PhotoTile(
                        image = image,
                        selected = image.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = { onPhotoClick(image) },
                        onLongClick = { onPhotoLongClick(image) },
                        onSelectionToggle = { onPhotoSelectionToggle(image) },
                    )
                }
            }
        } else {
            items(images, key = MediaImage::id) { image ->
                PhotoTile(
                    image = image,
                    selected = image.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onPhotoClick(image) },
                    onLongClick = { onPhotoLongClick(image) },
                    onSelectionToggle = { onPhotoSelectionToggle(image) },
                )
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
fun PermissionRequiredState(onRequestAccess: () -> Unit) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    image: MediaImage,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onSelectionToggle() else onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (selectionMode) onSelectionToggle() else onLongClick()
                },
            ),
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Выбрано",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(25.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: GalleryAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
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
private fun PartialAccessBanner(onChangeAccess: () -> Unit) {
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

private fun groupPhotosByDay(
    images: List<MediaImage>,
    newestFirst: Boolean,
): List<PhotoDayGroup> {
    val zone = ZoneId.systemDefault()
    val groups = images
        .groupBy { image ->
            Instant.ofEpochMilli(image.dateTakenMillis)
                .atZone(zone)
                .toLocalDate()
        }
        .map { (date, items) -> PhotoDayGroup(date = date, images = items) }

    return if (newestFirst) {
        groups.sortedByDescending(PhotoDayGroup::date)
    } else {
        groups.sortedBy(PhotoDayGroup::date)
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
    }
}

fun formatPhotoCount(count: Int): String =
    "${count.toString().reversed().chunked(3).joinToString(" ").reversed()} фото"
