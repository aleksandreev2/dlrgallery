package com.dlrgallery.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    uiState: GalleryUiState,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onPhotoClick: (MediaImage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                ScreenTitle(
                    title = "Фото",
                    subtitle = uiState.images.takeIf { it.isNotEmpty() }?.let { formatPhotoCount(it.size) },
                )
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить фотографии")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Сортировка и фильтры")
                }
            },
        )

        MediaAccessContent(
            uiState = uiState,
            emptyState = { EmptyMediaState() },
            onRequestAccess = onRequestAccess,
            onRetry = onRefresh,
        ) {
            PhotoGrid(
                images = uiState.images,
                showPartialAccessBanner = uiState.access == MediaAccess.Partial,
                onChangeAccess = onRequestAccess,
                onPhotoClick = onPhotoClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onAlbumClick: (GalleryAlbum) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                ScreenTitle(
                    title = "Альбомы",
                    subtitle = uiState.albums.takeIf { it.isNotEmpty() }?.let { "${it.size} альбомов" },
                )
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить альбомы")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Другие действия")
                }
            },
        )

        MediaAccessContent(
            uiState = uiState,
            emptyState = { EmptyAlbumsState() },
            onRequestAccess = onRequestAccess,
            onRetry = onRefresh,
        ) {
            AlbumGrid(
                albums = uiState.albums,
                showPartialAccessBanner = uiState.access == MediaAccess.Partial,
                onChangeAccess = onRequestAccess,
                onAlbumClick = onAlbumClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: GalleryAlbum,
    images: List<MediaImage>,
    onBack: () -> Unit,
    onPhotoClick: (MediaImage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                ScreenTitle(
                    title = album.name,
                    subtitle = formatPhotoCount(images.size),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                }
            },
        )
        if (images.isEmpty()) {
            EmptyMediaState()
        } else {
            PhotoGrid(
                images = images,
                showPartialAccessBanner = false,
                onChangeAccess = {},
                onPhotoClick = onPhotoClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { ScreenTitle(title = "Избранное") },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Другие действия")
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Избранное появится в следующей версии",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Сначала мы подключили настоящую галерею. Следующим этапом добавим отметки, базу данных и синхронизацию списка.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    photoCount: Int,
    albumCount: Int,
) {
    var saveAsCopy by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { ScreenTitle(title = "Настройки") })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsRow(
                    icon = Icons.Outlined.Collections,
                    title = "Медиатека",
                    subtitle = "$photoCount фото · $albumCount альбомов",
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "Тема",
                    subtitle = "Как в системе",
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.GridView,
                    title = "Размер сетки",
                    subtitle = "3 столбца",
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = "Качество экспорта",
                    subtitle = "Высокое",
                )
            }
            item { SettingsDivider() }
            item {
                SettingsToggleRow(
                    icon = Icons.Outlined.ContentCopy,
                    title = "Сохранять как копию",
                    subtitle = "Не изменять оригиналы",
                    checked = saveAsCopy,
                    onCheckedChange = { saveAsCopy = it },
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Скрытые альбомы",
                    subtitle = "Нет скрытых альбомов",
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "О приложении",
                    subtitle = "DLR Gallery 0.2.0",
                )
            }
        }
    }
}

@Composable
private fun MediaAccessContent(
    uiState: GalleryUiState,
    emptyState: @Composable () -> Unit,
    onRequestAccess: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        uiState.access == MediaAccess.None -> PermissionRequiredState(onRequestAccess)
        uiState.isLoading && uiState.images.isEmpty() -> LoadingMediaState()
        uiState.errorMessage != null && uiState.images.isEmpty() -> {
            MediaErrorState(message = uiState.errorMessage, onRetry = onRetry)
        }
        uiState.images.isEmpty() -> emptyState()
        else -> content()
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    subtitle: String? = null,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}
