package com.dlrgallery.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.MediaDateFilter
import com.dlrgallery.app.data.MediaFilterState
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaSortOrder
import com.dlrgallery.app.data.MediaTypeFilter
import com.dlrgallery.app.data.filterAndSortImages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCollectionScreen(
    title: String,
    images: List<MediaImage>,
    gridColumns: Int,
    sortOrder: MediaSortOrder,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    showPartialAccessBanner: Boolean,
    onChangeAccess: () -> Unit,
    onPhotoClick: (MediaImage, List<MediaImage>) -> Unit,
    onDeleteRequest: (List<MediaImage>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(MediaFilterState()) }
    var showOptions by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val visibleImages = remember(images, query, sortOrder, filters) {
        filterAndSortImages(images, query, sortOrder, filters)
    }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedImages = remember(visibleImages, selectedIds) {
        visibleImages.filter { it.id in selectedIds }
    }

    LaunchedEffect(images) {
        val validIds = images.mapTo(mutableSetOf(), MediaImage::id)
        selectedIds = selectedIds.intersect(validIds)
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }
    BackHandler(enabled = !selectionMode && searchVisible) {
        searchVisible = false
        query = ""
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedIds.size,
                        allVisibleSelected = visibleImages.isNotEmpty() &&
                            visibleImages.all { it.id in selectedIds },
                        onClose = { selectedIds = emptySet() },
                        onSelectAll = {
                            selectedIds = if (visibleImages.all { it.id in selectedIds }) {
                                emptySet()
                            } else {
                                visibleImages.mapTo(mutableSetOf(), MediaImage::id)
                            }
                        },
                        onShare = {
                            shareImages(context, selectedImages)
                            selectedIds = emptySet()
                        },
                        onDelete = {
                            val items = selectedImages
                            selectedIds = emptySet()
                            onDeleteRequest(items)
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (images.isNotEmpty()) {
                                    Text(
                                        text = "${formatPhotoCount(visibleImages.size)} · ${sortOrder.label}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
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
                                    contentDescription = if (searchVisible) "Закрыть поиск" else "Поиск",
                                )
                            }
                            IconButton(onClick = { showOptions = true }) {
                                Icon(
                                    imageVector = if (filters.isActive) Icons.Outlined.FilterAlt else Icons.Outlined.Tune,
                                    contentDescription = "Сортировка и фильтры",
                                    tint = if (filters.isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (onRefresh != null) {
                                IconButton(onClick = onRefresh) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                                }
                            }
                        },
                    )
                }

                if (searchVisible && !selectionMode) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        placeholder = { Text("Имя файла или альбома") },
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
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                images.isEmpty() && emptyContent != null -> emptyContent()
                visibleImages.isEmpty() -> EmptyFilteredMediaState(
                    onReset = {
                        query = ""
                        filters = MediaFilterState()
                    },
                )
                else -> PhotoGrid(
                    images = visibleImages,
                    gridColumns = gridColumns,
                    showPartialAccessBanner = showPartialAccessBanner,
                    onChangeAccess = onChangeAccess,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    groupByDate = sortOrder == MediaSortOrder.Newest || sortOrder == MediaSortOrder.Oldest,
                    newestDatesFirst = sortOrder != MediaSortOrder.Oldest,
                    onPhotoClick = { image -> onPhotoClick(image, visibleImages) },
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
    }

    if (showOptions) {
        MediaOptionsSheet(
            sortOrder = sortOrder,
            filters = filters,
            onSortOrderChange = onSortOrderChange,
            onFiltersChange = { filters = it },
            onDismiss = { showOptions = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("Выбрано: $selectedCount", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Снять выделение")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Outlined.SelectAll,
                    contentDescription = if (allVisibleSelected) "Снять выделение" else "Выбрать всё",
                )
            }
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.Share, contentDescription = "Поделиться")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "В корзину")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaOptionsSheet(
    sortOrder: MediaSortOrder,
    filters: MediaFilterState,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    onFiltersChange: (MediaFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Сортировка и фильтры",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Сортировка",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            MediaSortOrder.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortOrderChange(option) }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == sortOrder,
                        onClick = { onSortOrderChange(option) },
                    )
                    Text(option.label, modifier = Modifier.padding(start = 6.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FilterSectionTitle("Формат")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MediaTypeFilter.entries) { option ->
                    FilterChip(
                        selected = filters.type == option,
                        onClick = { onFiltersChange(filters.copy(type = option)) },
                        label = { Text(option.label) },
                    )
                }
            }

            FilterSectionTitle("Дата")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MediaDateFilter.entries) { option ->
                    FilterChip(
                        selected = filters.date == option,
                        onClick = { onFiltersChange(filters.copy(date = option)) },
                        label = { Text(option.label) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onFiltersChange(filters.copy(largeFilesOnly = !filters.largeFilesOnly))
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Только большие файлы", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "От 10 МБ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = filters.largeFilesOnly,
                    onCheckedChange = { onFiltersChange(filters.copy(largeFilesOnly = it)) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onFiltersChange(MediaFilterState()) },
                    enabled = filters.isActive,
                ) {
                    Text("Сбросить")
                }
                Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                    Text("Готово")
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun EmptyFilteredMediaState(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Ничего не найдено",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Измените запрос или сбросьте фильтры.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(onClick = onReset) {
                Text("Сбросить")
            }
        }
    }
}

private fun shareImages(context: Context, images: List<MediaImage>) {
    if (images.isEmpty()) return

    val uris = ArrayList<Uri>(images.size).apply {
        addAll(images.map(MediaImage::uri))
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = images.first().mimeType.ifBlank { "image/*" }
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "Фотографии", uris.first()).also { clip ->
            uris.drop(1).forEach { uri -> clip.addItem(ClipData.Item(uri)) }
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Поделиться фотографиями"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не найдено приложение для отправки", Toast.LENGTH_SHORT).show()
    }
}
