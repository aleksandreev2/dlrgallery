package com.dlrgallery.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.renamedAlbumRelativePath


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOrganizerScreen(
    images: List<MediaImage>,
    albums: List<GalleryAlbum>,
    gridColumns: Int,
    isBusy: Boolean,
    onBack: () -> Unit,
    onMove: (List<MediaImage>, String) -> Unit,
    onCopy: (List<MediaImage>, String) -> Unit,
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var operation by remember { mutableStateOf<AlbumOperationKind?>(null) }
    var showAlbumManager by rememberSaveable { mutableStateOf(false) }
    var renameAlbum by remember { mutableStateOf<GalleryAlbum?>(null) }
    val selectedImages = remember(images, selectedIds) {
        images.filter { it.id in selectedIds }
    }
    val selectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(images) {
        val validIds = images.mapTo(mutableSetOf(), MediaImage::id)
        selectedIds = selectedIds.intersect(validIds)
    }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = if (selectionMode) "Выбрано: ${selectedIds.size}" else "Управление медиатекой",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (selectionMode) {
                            "Выберите действие внизу"
                        } else {
                            "Создание, копирование и переименование альбомов"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = if (selectionMode) ({ selectedIds = emptySet() }) else onBack) {
                    Icon(
                        imageVector = if (selectionMode) Icons.Outlined.Close else Icons.Outlined.ArrowBack,
                        contentDescription = if (selectionMode) "Снять выделение" else "Назад",
                    )
                }
            },
            actions = {
                if (selectionMode) {
                    IconButton(
                        onClick = {
                            selectedIds = if (images.all { it.id in selectedIds }) {
                                emptySet()
                            } else {
                                images.mapTo(mutableSetOf(), MediaImage::id)
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.SelectAll, contentDescription = "Выбрать всё")
                    }
                } else {
                    IconButton(onClick = { showAlbumManager = true }) {
                        Icon(
                            Icons.Outlined.DriveFileRenameOutline,
                            contentDescription = "Переименовать альбом",
                        )
                    }
                }
            },
        )

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f)) {
            if (images.isEmpty()) {
                EmptyMediaState()
            } else {
                PhotoGrid(
                    images = images,
                    gridColumns = gridColumns,
                    showPartialAccessBanner = false,
                    onChangeAccess = {},
                    onPhotoClick = { image ->
                        selectedIds = if (image.id in selectedIds) {
                            selectedIds - image.id
                        } else {
                            selectedIds + image.id
                        }
                    },
                    selectedIds = selectedIds,
                    selectionMode = true,
                    groupByDate = true,
                    newestDatesFirst = true,
                    onPhotoLongClick = { image -> selectedIds = selectedIds + image.id },
                    onPhotoSelectionToggle = { image ->
                        selectedIds = if (image.id in selectedIds) {
                            selectedIds - image.id
                        } else {
                            selectedIds + image.id
                        }
                    },
                )
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = { operation = AlbumOperationKind.Copy },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Копировать")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = { operation = AlbumOperationKind.Move },
                    ) {
                        Icon(Icons.Outlined.DriveFileMove, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Переместить")
                    }
                }
            }
        }
    }

    operation?.let { selectedOperation ->
        AlbumDestinationDialog(
            operation = selectedOperation,
            selectedCount = selectedImages.size,
            albums = albums,
            currentRelativePath = selectedImages
                .map(MediaImage::relativePath)
                .distinct()
                .singleOrNull(),
            onDismiss = { operation = null },
            onConfirm = { targetPath ->
                val items = selectedImages
                operation = null
                selectedIds = emptySet()
                if (selectedOperation == AlbumOperationKind.Move) {
                    onMove(items, targetPath)
                } else {
                    onCopy(items, targetPath)
                }
            },
        )
    }

    if (showAlbumManager) {
        ModalBottomSheet(onDismissRequest = { showAlbumManager = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text = "Переименовать альбом",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Android переместит все файлы альбома в папку с новым названием.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(albums, key = GalleryAlbum::id) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAlbumManager = false
                                    renameAlbum = album
                                }
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Collections,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp),
                            ) {
                                Text(album.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${album.imageCount} файлов",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(
                                Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    renameAlbum?.let { album ->
        RenameAlbumDialog(
            currentName = album.name,
            onDismiss = { renameAlbum = null },
            onConfirm = { newName ->
                val items = images.filter { it.bucketId == album.id }
                renameAlbum = null
                onMove(items, renamedAlbumRelativePath(album.relativePath, newName))
            },
        )
    }
}
