package com.dlrgallery.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class DemoPhoto(
    val title: String,
    val colors: List<Color>,
    val icon: ImageVector,
)

private data class DemoAlbum(
    val name: String,
    val count: Int,
    val cover: DemoPhoto,
)

private val demoPhotos = listOf(
    DemoPhoto("Озеро", listOf(Color(0xFF176B87), Color(0xFF64CCC5)), Icons.Outlined.Landscape),
    DemoPhoto("Город", listOf(Color(0xFF5C469C), Color(0xFFD4ADFC)), Icons.Outlined.LocationCity),
    DemoPhoto("Лес", listOf(Color(0xFF1B5E20), Color(0xFF81C784)), Icons.Outlined.Forest),
    DemoPhoto("Закат", listOf(Color(0xFFF57C00), Color(0xFFFFCC80)), Icons.Outlined.WbSunny),
    DemoPhoto("Питомец", listOf(Color(0xFF6D4C41), Color(0xFFD7CCC8)), Icons.Outlined.Pets),
    DemoPhoto("Цветы", listOf(Color(0xFFAD1457), Color(0xFFF8BBD0)), Icons.Outlined.LocalFlorist),
    DemoPhoto("Побережье", listOf(Color(0xFF01579B), Color(0xFF4FC3F7)), Icons.Outlined.Landscape),
    DemoPhoto("Улица", listOf(Color(0xFF37474F), Color(0xFF90A4AE)), Icons.Outlined.LocationCity),
    DemoPhoto("Солнце", listOf(Color(0xFFF9A825), Color(0xFFFFF59D)), Icons.Outlined.WbSunny),
    DemoPhoto("Парк", listOf(Color(0xFF33691E), Color(0xFFAED581)), Icons.Outlined.Forest),
    DemoPhoto("Кадр", listOf(Color(0xFF283593), Color(0xFF9FA8DA)), Icons.Outlined.Image),
    DemoPhoto("Букет", listOf(Color(0xFF880E4F), Color(0xFFF48FB1)), Icons.Outlined.LocalFlorist),
)

private val demoAlbums = listOf(
    DemoAlbum("Камера", 1_234, demoPhotos[0]),
    DemoAlbum("Скриншоты", 312, demoPhotos[10]),
    DemoAlbum("Загрузки", 156, demoPhotos[3]),
    DemoAlbum("Мессенджеры", 98, demoPhotos[6]),
    DemoAlbum("Путешествия", 87, demoPhotos[2]),
    DemoAlbum("Разное", 64, demoPhotos[4]),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { ScreenTitle("Фото") },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Search, contentDescription = "Поиск")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Сортировка и фильтры")
                }
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DateHeader("Сегодня, 21 июля", "12 фото")
            }
            items(demoPhotos.take(6), key = { "today-${it.title}" }) { photo ->
                DemoPhotoTile(photo = photo)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                DateHeader("Вчера, 20 июля", "9 фото")
            }
            items(demoPhotos.drop(3).take(6), key = { "yesterday-${it.title}" }) { photo ->
                DemoPhotoTile(photo = photo)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                DateHeader("18 июля", "18 фото")
            }
            items(demoPhotos.drop(6), key = { "older-${it.title}" }) { photo ->
                DemoPhotoTile(photo = photo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { ScreenTitle("Альбомы") },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Add, contentDescription = "Создать альбом")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Другие действия")
                }
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(demoAlbums, key = { it.name }) { album ->
                AlbumCard(album)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { ScreenTitle("Избранное") },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Другие действия")
                }
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Недавно добавленные",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "9 фото",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(demoPhotos.take(9), key = { "favorite-${it.title}" }) { photo ->
                DemoPhotoTile(photo = photo, favorite = true)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FavoriteHint()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var saveAsCopy by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { ScreenTitle("Настройки") })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
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
                    subtitle = "DLR Gallery 0.1.0",
                )
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DateHeader(title: String, count: String) {
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
private fun DemoPhotoTile(
    photo: DemoPhoto,
    favorite: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.linearGradient(photo.colors)),
    ) {
        Icon(
            imageVector = photo.icon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp),
            tint = Color.White.copy(alpha = 0.78f),
        )
        if (favorite) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "В избранном",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp),
                tint = Color.White,
            )
        }
        Text(
            text = photo.title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumCard(album: DemoAlbum) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(album.cover.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = album.cover.icon,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = Color.White.copy(alpha = 0.82f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.count.toString().reversed().chunked(3).joinToString(" ").reversed(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavoriteHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Отмечайте лучшие кадры звездой",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "Они останутся здесь, даже если находятся в разных альбомах.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        shape = RoundedCornerShape(14.dp),
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
