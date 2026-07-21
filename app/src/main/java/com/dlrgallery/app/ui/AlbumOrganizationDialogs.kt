package com.dlrgallery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.newAlbumRelativePath
import com.dlrgallery.app.data.sanitizeAlbumName


enum class AlbumOperationKind {
    Move,
    Copy,
}

@Composable
fun AlbumDestinationDialog(
    operation: AlbumOperationKind,
    selectedCount: Int,
    albums: List<GalleryAlbum>,
    currentRelativePath: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var createNew by rememberSaveable { mutableStateOf(albums.isEmpty()) }
    var newAlbumName by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val availableAlbums = remember(albums, currentRelativePath) {
        albums
            .filter { album ->
                album.relativePath.isNotBlank() && album.relativePath != currentRelativePath
            }
            .distinctBy(GalleryAlbum::relativePath)
            .sortedBy { it.name.lowercase() }
    }
    val actionTitle = if (operation == AlbumOperationKind.Move) {
        "Переместить в альбом"
    } else {
        "Копировать в альбом"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (operation == AlbumOperationKind.Move) {
                    Icons.Outlined.DriveFileMove
                } else {
                    Icons.Outlined.ContentCopy
                },
                contentDescription = null,
            )
        },
        title = { Text(actionTitle) },
        text = {
            Column {
                Text(
                    text = formatSelectedFileCount(selectedCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                if (availableAlbums.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(availableAlbums, key = GalleryAlbum::relativePath) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        createNew = false
                                        onConfirm(album.relativePath)
                                    }
                                    .padding(vertical = 8.dp),
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
                                        .padding(start = 12.dp),
                                ) {
                                    Text(album.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = formatSelectedFileCount(album.imageCount),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createNew = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = createNew, onClick = { createNew = true })
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(
                        text = "Новый альбом",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (createNew) {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = {
                            newAlbumName = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название альбома") },
                        supportingText = {
                            Text(errorMessage ?: "Альбом будет создан в папке Pictures")
                        },
                        isError = errorMessage != null,
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (createNew) {
                TextButton(
                    onClick = {
                        try {
                            val cleanName = sanitizeAlbumName(newAlbumName)
                            onConfirm(newAlbumRelativePath(cleanName))
                        } catch (error: IllegalArgumentException) {
                            errorMessage = error.message
                        }
                    },
                ) {
                    Text(if (operation == AlbumOperationKind.Move) "Создать и переместить" else "Создать и копировать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
fun RenameAlbumDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(currentName) { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text("Переименовать альбом") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Новое название") },
                supportingText = {
                    Text(errorMessage ?: "Все файлы останутся на устройстве")
                },
                isError = errorMessage != null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        onConfirm(sanitizeAlbumName(value))
                    } catch (error: IllegalArgumentException) {
                        errorMessage = error.message
                    }
                },
            ) {
                Text("Переименовать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

private fun formatSelectedFileCount(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
    else -> "$count файлов"
}
