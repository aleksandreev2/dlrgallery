package com.dlrgallery.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dlrgallery.app.data.TrashItem
import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<TrashItem>,
    gridColumns: Int,
    isBusy: Boolean,
    onRestore: (List<TrashItem>) -> Unit,
    onDeletePermanently: (List<TrashItem>) -> Unit,
) {
    val localTrash = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var permanentDeleteItems by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var showEmptyTrashDialog by rememberSaveable { mutableStateOf(false) }
    val selectionMode = selectedKeys.isNotEmpty()
    val selectedItems = remember(items, selectedKeys) {
        items.filter { it.key in selectedKeys }
    }
    val totalSize = remember(items) { items.sumOf(TrashItem::sizeBytes) }

    LaunchedEffect(items) {
        val validKeys = items.mapTo(mutableSetOf(), TrashItem::key)
        selectedKeys = selectedKeys.intersect(validKeys)
    }
    BackHandler(enabled = selectionMode) { selectedKeys = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = {
                    Text(
                        text = "Выбрано: ${selectedKeys.size}",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { selectedKeys = emptySet() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Снять выделение")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isBusy,
                        onClick = {
                            val selected = selectedItems
                            selectedKeys = emptySet()
                            onRestore(selected)
                        },
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = "Восстановить")
                    }
                    IconButton(
                        enabled = !isBusy,
                        onClick = { permanentDeleteItems = selectedItems },
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = "Удалить навсегда")
                    }
                },
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Корзина",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = buildString {
                                    append(formatTrashCount(items.size))
                                    append(" · ")
                                    append(formatTrashSize(totalSize))
                                    append(if (localTrash) " · до ручной очистки" else " · до 30 дней")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(
                            enabled = !isBusy,
                            onClick = { onRestore(items) },
                        ) {
                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Восстановить всё")
                        }
                        IconButton(
                            enabled = !isBusy,
                            onClick = { showEmptyTrashDialog = true },
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить корзину")
                        }
                    }
                },
            )
        }

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (items.isEmpty()) {
            EmptyTrashState(localTrash = localTrash)
        } else {
            TrashGrid(
                items = items,
                gridColumns = gridColumns,
                selectedKeys = selectedKeys,
                onToggle = { item ->
                    selectedKeys = if (item.key in selectedKeys) {
                        selectedKeys - item.key
                    } else {
                        selectedKeys + item.key
                    }
                },
            )
        }
    }

    if (permanentDeleteItems.isNotEmpty()) {
        PermanentDeleteDialog(
            count = permanentDeleteItems.size,
            localTrash = permanentDeleteItems.any { it.localEntryId != null },
            onDismiss = { permanentDeleteItems = emptyList() },
            onConfirm = {
                val selected = permanentDeleteItems
                permanentDeleteItems = emptyList()
                selectedKeys = emptySet()
                onDeletePermanently(selected)
            },
        )
    }

    if (showEmptyTrashDialog) {
        PermanentDeleteDialog(
            count = items.size,
            emptyTrash = true,
            localTrash = items.any { it.localEntryId != null },
            onDismiss = { showEmptyTrashDialog = false },
            onConfirm = {
                showEmptyTrashDialog = false
                onDeletePermanently(items)
            },
        )
    }
}

@Composable
private fun TrashGrid(
    items: List<TrashItem>,
    gridColumns: Int,
    selectedKeys: Set<String>,
    onToggle: (TrashItem) -> Unit,
) {
    val columns = gridColumns.coerceIn(2, 5)
    val spacing = if (columns >= 5) 2.dp else 4.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "trash-info", span = { GridItemSpan(maxLineSpan) }) {
            TrashInfoBanner()
        }
        items(items, key = TrashItem::key) { item ->
            TrashTile(
                item = item,
                selected = item.key in selectedKeys,
                onToggle = { onToggle(item) },
            )
        }
    }
}

@Composable
private fun TrashInfoBanner() {
    val localTrash = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                text = if (localTrash) "Локальная корзина DLR Gallery" else "Системная корзина Android",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (localTrash) {
                    "При перемещении сюда системного окна нет: DLR Gallery сохраняет резервную копию и скрывает оригинал внутри приложения. Другие галереи могут продолжать видеть оригинал. Подтверждение Android понадобится только при удалении навсегда."
                } else {
                    "Файлы хранятся в системной корзине устройства и автоматически удаляются Android."
                },
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashTile(
    item: TrashItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    var previewFailed by remember(item.key) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
    ) {
        val systemMedia = item.systemMedia
        when {
            systemMedia != null -> MediaThumbnail(
                image = systemMedia,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            previewFailed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.BrokenImage,
                    contentDescription = "Не удалось загрузить превью",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> AsyncImage(
                model = item.previewUri,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { previewFailed = true },
            )
        }

        if (item.isVideo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Видео",
                        modifier = Modifier.size(15.dp),
                        tint = Color.White,
                    )
                    if (item.durationMillis > 0L) {
                        Text(
                            text = formatTrashDuration(item.durationMillis),
                            modifier = Modifier.padding(start = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = if (item.localEntryId != null) {
                    "До удаления вручную"
                } else {
                    formatExpiration(item.dateExpiresMillis)
                },
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Выбрано",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyTrashState(localTrash: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Корзина пуста",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (localTrash) {
                    "Удалённые через DLR Gallery фотографии и видео будут храниться здесь до восстановления или ручного удаления."
                } else {
                    "Удалённые через DLR Gallery фотографии и видео будут храниться здесь до 30 дней."
                },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermanentDeleteDialog(
    count: Int,
    emptyTrash: Boolean = false,
    localTrash: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
        title = { Text(if (emptyTrash) "Очистить корзину?" else "Удалить навсегда?") },
        text = {
            Text(
                buildString {
                    if (emptyTrash) {
                        append("Все ${formatTrashCount(count)} будут окончательно удалены. Это действие нельзя отменить.")
                    } else {
                        append("${formatTrashCount(count)} будут окончательно удалены. Восстановить их после этого не получится.")
                    }
                    if (localTrash) {
                        append(" Android 10 попросит подтвердить физическое удаление оригинала.")
                    }
                },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Удалить навсегда")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

private fun formatExpiration(dateExpiresMillis: Long): String {
    if (dateExpiresMillis <= 0L) return "До автоудаления"
    val days = Duration.between(Instant.now(), Instant.ofEpochMilli(dateExpiresMillis))
        .toDays()
        .coerceAtLeast(0L)
    return when {
        days == 0L -> "Удалится сегодня"
        days % 10L == 1L && days % 100L != 11L -> "Остался $days день"
        days % 10L in 2L..4L && days % 100L !in 12L..14L -> "Осталось $days дня"
        else -> "Осталось $days дней"
    }
}

private fun formatTrashCount(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
    else -> "$count файлов"
}

private fun formatTrashSize(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(units.indices)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) {
        "${value.toInt()} ${units[group]}"
    } else {
        "%.1f %s".format(value, units[group])
    }
}

private fun formatTrashDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
