package com.dlrgallery.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaSortOrder

@Composable
fun FavoriteGalleryScreen(
    allImages: List<MediaImage>,
    favoriteIds: Set<Long>,
    gridColumns: Int,
    sortOrder: MediaSortOrder,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    onDeleteRequest: (List<MediaImage>) -> Unit,
    onPhotoClick: (MediaImage, List<MediaImage>) -> Unit,
) {
    val favoriteImages = remember(allImages, favoriteIds) {
        allImages.filter { image -> image.id in favoriteIds }
    }

    PhotoCollectionScreen(
        title = "Избранное",
        images = favoriteImages,
        gridColumns = gridColumns,
        sortOrder = sortOrder,
        onSortOrderChange = onSortOrderChange,
        showPartialAccessBanner = false,
        onChangeAccess = {},
        onPhotoClick = onPhotoClick,
        onDeleteRequest = onDeleteRequest,
        emptyContent = { EmptyFavoritesState() },
    )
}

@Composable
private fun EmptyFavoritesState() {
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
                text = "Здесь будут любимые фотографии",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Откройте фотографию и нажмите сердечко. Отметка сохранится после перезапуска приложения.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
