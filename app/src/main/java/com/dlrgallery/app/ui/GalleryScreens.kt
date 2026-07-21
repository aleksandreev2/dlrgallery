package com.dlrgallery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.BuildConfig
import com.dlrgallery.app.data.AppSettings
import com.dlrgallery.app.data.AppThemeMode
import com.dlrgallery.app.data.ExportQuality
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.GalleryGridSize
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    uiState: GalleryUiState,
    gridColumns: Int,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onPhotoClick: (MediaImage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                ScreenTitle(
                    title = "Фото",
                    subtitle = uiState.images.takeIf { it.isNotEmpty() }
                        ?.let { formatPhotoCount(it.size) },
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
                gridColumns = gridColumns,
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
                    subtitle = uiState.albums.takeIf { it.isNotEmpty() }
                        ?.let { "${it.size} альбомов" },
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
    gridColumns: Int,
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
                gridColumns = gridColumns,
                showPartialAccessBanner = false,
                onChangeAccess = {},
                onPhotoClick = onPhotoClick,
            )
        }
    }
}

private enum class SettingsDialog {
    Theme,
    Grid,
    Export,
    About,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    photoCount: Int,
    albumCount: Int,
    settings: AppSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onGridSizeChange: (GalleryGridSize) -> Unit,
    onExportQualityChange: (ExportQuality) -> Unit,
    onSaveAsCopyChange: (Boolean) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

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
                    showChevron = false,
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "Тема",
                    subtitle = settings.themeMode.label,
                    onClick = { dialog = SettingsDialog.Theme },
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.GridView,
                    title = "Размер сетки",
                    subtitle = gridSizeDescription(settings.gridSize),
                    onClick = { dialog = SettingsDialog.Grid },
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = "Качество экспорта",
                    subtitle = "${settings.exportQuality.label} · JPEG ${settings.exportQuality.jpegQuality}",
                    onClick = { dialog = SettingsDialog.Export },
                )
            }
            item { SettingsDivider() }
            item {
                SettingsToggleRow(
                    icon = Icons.Outlined.ContentCopy,
                    title = "Сохранять как копию",
                    subtitle = if (settings.saveAsCopy) {
                        "Оригинал не изменяется"
                    } else {
                        "Замена оригинала появится позже — пока сохраняется копия"
                    },
                    checked = settings.saveAsCopy,
                    onCheckedChange = onSaveAsCopyChange,
                )
            }
            item { SettingsDivider() }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "О приложении",
                    subtitle = "DLR Gallery ${BuildConfig.VERSION_NAME}",
                    onClick = { dialog = SettingsDialog.About },
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = "Тема",
            options = AppThemeMode.entries,
            selected = settings.themeMode,
            label = AppThemeMode::label,
            onSelect = {
                onThemeModeChange(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Grid -> ChoiceDialog(
            title = "Размер сетки",
            options = GalleryGridSize.entries,
            selected = settings.gridSize,
            label = ::gridSizeDescription,
            onSelect = {
                onGridSizeChange(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Export -> ChoiceDialog(
            title = "Качество экспорта",
            options = ExportQuality.entries,
            selected = settings.exportQuality,
            label = { "${it.label} · JPEG ${it.jpegQuality}" },
            onSelect = {
                onExportQualityChange(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.About -> AboutDialog(onDismiss = { dialog = null })
        null -> Unit
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
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            .clickable { onCheckedChange(!checked) }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                        Text(
                            text = label(option),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DLR Gallery") },
        text = {
            Text(
                "Версия ${BuildConfig.VERSION_NAME}\n\n" +
                    "Лёгкая галерея для просмотра и базового редактирования фотографий. " +
                    "Приложение работает офлайн, не требует аккаунта и не содержит рекламы.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        },
    )
}

private fun gridSizeDescription(value: GalleryGridSize): String {
    val columnsWord = if (value.columns == 5) "столбцов" else "столбца"
    return "${value.label} · ${value.columns} $columnsWord"
}
