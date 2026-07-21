package com.dlrgallery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaSortOrder
import java.util.Locale

@Composable
fun PhotosBrowserScreen(
    uiState: GalleryUiState,
    gridColumns: Int,
    sortOrder: MediaSortOrder,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteRequest: (List<MediaImage>) -> Unit,
    onPhotoClick: (MediaImage, List<MediaImage>) -> Unit,
) {
    MediaAccessContainer(
        uiState = uiState,
        onRequestAccess = onRequestAccess,
        onRefresh = onRefresh,
        emptyContent = { EmptyMediaState() },
    ) {
        PhotoCollectionScreen(
            title = "Фото",
            images = uiState.images,
            gridColumns = gridColumns,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            showPartialAccessBanner = uiState.access == MediaAccess.Partial,
            onChangeAccess = onRequestAccess,
            onRefresh = onRefresh,
            onPhotoClick = onPhotoClick,
            onDeleteRequest = onDeleteRequest,
        )
    }
}

@Composable
fun AlbumDetailBrowserScreen(
    album: GalleryAlbum,
    images: List<MediaImage>,
    gridColumns: Int,
    sortOrder: MediaSortOrder,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    onBack: () -> Unit,
    onDeleteRequest: (List<MediaImage>) -> Unit,
    onPhotoClick: (MediaImage, List<MediaImage>) -> Unit,
) {
    PhotoCollectionScreen(
        title = album.name,
        images = images,
        gridColumns = gridColumns,
        sortOrder = sortOrder,
        onSortOrderChange = onSortOrderChange,
        showPartialAccessBanner = false,
        onChangeAccess = {},
        onBack = onBack,
        onPhotoClick = onPhotoClick,
        onDeleteRequest = onDeleteRequest,
        emptyContent = { EmptyMediaState() },
    )
}

private enum class AlbumSortOrder(val label: String) {
    Recent("Сначала недавние"),
    Name("По названию"),
    Count("По количеству фото"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsBrowserScreen(
    uiState: GalleryUiState,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onAlbumClick: (GalleryAlbum) -> Unit,
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrderName by rememberSaveable { mutableStateOf(AlbumSortOrder.Recent.name) }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    val sortOrder = AlbumSortOrder.entries.firstOrNull { it.name == sortOrderName }
        ?: AlbumSortOrder.Recent

    val albums = remember(uiState.albums, query, sortOrder) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val filtered = uiState.albums.filter { album ->
            normalized.isEmpty() || album.name.lowercase(Locale.getDefault()).contains(normalized)
        }
        when (sortOrder) {
            AlbumSortOrder.Recent -> filtered.sortedByDescending(GalleryAlbum::latestImageMillis)
            AlbumSortOrder.Name -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            AlbumSortOrder.Count -> filtered.sortedByDescending(GalleryAlbum::imageCount)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Альбомы",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (uiState.albums.isNotEmpty()) {
                        Text(
                            text = "${albums.size} альбомов · ${sortOrder.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) query = ""
                    },
                ) {
                    Icon(
                        imageVector = if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (searchVisible) "Закрыть поиск" else "Поиск альбомов",
                    )
                }
                IconButton(onClick = { showSortDialog = true }) {
                    Icon(Icons.Outlined.Sort, contentDescription = "Сортировка альбомов")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить альбомы")
                }
            },
        )

        if (searchVisible) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                placeholder = { Text("Название альбома") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
            )
        }

        when {
            uiState.access == MediaAccess.None -> PermissionRequiredState(onRequestAccess)
            uiState.isLoading && uiState.images.isEmpty() -> LoadingMediaState()
            uiState.errorMessage != null && uiState.images.isEmpty() -> {
                MediaErrorState(uiState.errorMessage, onRefresh)
            }
            uiState.albums.isEmpty() -> EmptyAlbumsState()
            albums.isEmpty() -> EmptyAlbumSearchState(onReset = { query = "" })
            else -> AlbumGrid(
                albums = albums,
                showPartialAccessBanner = uiState.access == MediaAccess.Partial,
                onChangeAccess = onRequestAccess,
                onAlbumClick = onAlbumClick,
            )
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Сортировка альбомов") },
            text = {
                Column {
                    AlbumSortOrder.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortOrderName = option.name
                                    showSortDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == sortOrder,
                                onClick = {
                                    sortOrderName = option.name
                                    showSortDialog = false
                                },
                            )
                            Text(option.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Закрыть")
                }
            },
        )
    }
}

@Composable
private fun MediaAccessContainer(
    uiState: GalleryUiState,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    emptyContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        uiState.access == MediaAccess.None -> PermissionRequiredState(onRequestAccess)
        uiState.isLoading && uiState.images.isEmpty() -> LoadingMediaState()
        uiState.errorMessage != null && uiState.images.isEmpty() -> {
            MediaErrorState(uiState.errorMessage, onRefresh)
        }
        uiState.images.isEmpty() -> emptyContent()
        else -> content()
    }
}

@Composable
private fun EmptyAlbumSearchState(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Альбомы не найдены",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onReset) {
                Text("Сбросить поиск")
            }
        }
    }
}
