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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dlrgallery.app.data.MediaImage
import java.time.Duration
import java.time.Instant


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    images: List<MediaImage>,
    gridColumns: Int,
    onBack: () -> Unit,
    onRestore: (List<MediaImage>) -> Unit,
    onDeletePermanently: (List<MediaImage>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var permanentDeleteItems by remember { mutableStateOf<List<MediaImage>>(emptyList()) }
    var showEmptyTrashDialog by rememberSaveable { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedImages = remember(images, selectedIds) {
        images.filter { it.id in selectedIds }
    }

    LaunchedEffect(images) {
        val validIds = images.mapTo(mutableSetOf(), MediaImage::id)
        selectedIds = selectedIds.intersect(validIds)
    }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = {
                    Text(
                        text = "Выбрано: ${selectedIds.size}",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Снять выделение")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val items = selectedImages
                            selectedIds = emptySet()
                            onRestore(items)
                        },
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = "Восстановить")
                    }
                    IconButton(onClick = { permanentDeleteItems = selectedImages }) {
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
                        if (images.isNotEmpty()) {
                            Text(
                                text = "${formatTrashCount(images.size)} · хранение до 30 дней",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (images.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        IconButton(
                            onClick = { onRestore(images) },
                        ) {
                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Восстановить всё")
                        }
                        IconButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить корзину")
                        }
                    }
                },
            )
        }

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> UnsupportedTrashState()
            images.isEmpty() -> EmptyTrashState()
            else -> TrashGrid(
                images = images,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
                onToggle = { image ->
                    selectedIds = if (image.id in selectedIds) {
                        selectedIds - image.id
                    } else {
                        selectedIds + image.id
                    }
                },
            )
        }
    }

    if (permanentDeleteItems.isNotEmpty()) {
        PermanentDeleteDialog(
            count = permanentDeleteItems.size,
            onDismiss = { permanentDeleteItems = emptyList() },
            onConfirm = {
                val items = permanentDeleteItems
                permanentDeleteItems = emptyList()
                selectedIds = emptySet()
                onDeletePermanently(items)
            },
        )
    }

    if (showEmptyTrashDialog) {
        PermanentDeleteDialog(
            count = images.size,
            emptyTrash = true,
            onDismiss = { showEmptyTrashDialog = false },
            onConfirm = {
                showEmptyTrashDialog = false
                onDeletePermanently(images)
            },
        )
    }
}

@Composable
private fun TrashGrid(
    images: List<MediaImage>,
    gridColumns: Int,
    selectedIds: Set<Long>,
    onToggle: (MediaImage) -> Unit,
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
        items(images, key = MediaImage::id) { image ->
            TrashTile(
                image = image,
                selected = image.id in selectedIds,
                onToggle = { onToggle(image) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashTile(
    image: MediaImage,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
    ) {
        MediaThumbnail(
            image = image,
            contentDescription = image.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = formatExpiration(image.dateExpiresMillis),
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
private fun EmptyTrashState() {
    TrashMessageState(
        icon = Icons.Outlined.DeleteSweep,
        title = "Корзина пуста",
        message = "Удалённые через DLR Gallery файлы будут временно появляться здесь.",
    )
}

@Composable
private fun UnsupportedTrashState() {
    TrashMessageState(
        icon = Icons.Outlined.DeleteSweep,
        title = "Системная корзина недоступна",
        message = "Android 10 и более старые версии удаляют медиафайлы без системного восстановления. Корзина поддерживается начиная с Android 11.",
    )
}

@Composable
private fun TrashMessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
) {
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
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.size(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
        title = { Text(if (emptyTrash) "Очистить корзину?" else "Удалить навсегда?") },
        text = {
            Text(
                if (emptyTrash) {
                    "Все ${formatTrashCount(count)} будут окончательно удалены. Это действие нельзя отменить."
                } else {
                    "${formatTrashCount(count)} будут окончательно удалены. Восстановить их после этого не получится."
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
