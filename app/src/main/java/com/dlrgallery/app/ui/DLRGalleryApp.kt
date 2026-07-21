package com.dlrgallery.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.mediaPermissionsForCurrentVersion

@Composable
fun DLRGalleryApp(
    galleryViewModel: GalleryViewModel = viewModel(),
    favoritesViewModel: FavoritesViewModel = viewModel(),
) {
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(GalleryDestination.Photos) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerImageIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        galleryViewModel.refresh()
    }
    val requestMediaAccess = remember(permissionLauncher) {
        { permissionLauncher.launch(mediaPermissionsForCurrentVersion()) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                galleryViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedAlbum = remember(uiState.albums, selectedAlbumId) {
        selectedAlbumId?.let { id -> uiState.albums.firstOrNull { it.id == id } }
    }
    val viewerImages = remember(uiState.images, viewerImageIds) {
        val imagesById = uiState.images.associateBy(MediaImage::id)
        viewerImageIds.mapNotNull(imagesById::get)
    }

    fun openViewer(image: MediaImage, source: List<MediaImage>) {
        viewerImageIds = source.map(MediaImage::id)
        selectedPhotoId = image.id
    }

    fun closeViewer() {
        selectedPhotoId = null
        viewerImageIds = emptyList()
    }

    LaunchedEffect(uiState.images, uiState.isLoading, uiState.access) {
        if (!uiState.isLoading && uiState.access == MediaAccess.Full) {
            favoritesViewModel.removeMissingImages(uiState.images.mapTo(mutableSetOf(), MediaImage::id))
        }
    }
    LaunchedEffect(uiState.albums, selectedAlbumId) {
        if (selectedAlbumId != null && selectedAlbum == null && !uiState.isLoading) {
            selectedAlbumId = null
        }
    }
    LaunchedEffect(viewerImages, selectedPhotoId, uiState.isLoading) {
        if (
            selectedPhotoId != null &&
            viewerImages.none { it.id == selectedPhotoId } &&
            !uiState.isLoading
        ) {
            closeViewer()
        }
    }

    val activePhotoId = selectedPhotoId
    if (activePhotoId != null && viewerImages.isNotEmpty()) {
        BackHandler(onBack = ::closeViewer)
        ImmersivePhotoViewer(
            images = viewerImages,
            initialPhotoId = activePhotoId,
            favoriteIds = favoriteIds,
            onToggleFavorite = favoritesViewModel::toggleFavorite,
            onBack = ::closeViewer,
        )
        return
    }

    if (selectedAlbum != null) {
        val albumImages = remember(uiState.images, selectedAlbum.id) {
            uiState.images.filter { it.bucketId == selectedAlbum.id }
        }
        BackHandler { selectedAlbumId = null }
        AlbumDetailScreen(
            album = selectedAlbum,
            images = albumImages,
            onBack = { selectedAlbumId = null },
            onPhotoClick = { image -> openViewer(image, albumImages) },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                GalleryDestination.entries.forEach { item ->
                    val selected = item == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { destination = item },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Crossfade(
            targetState = destination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            animationSpec = tween(durationMillis = 160),
            label = "main-tabs",
        ) { selected ->
            when (selected) {
                GalleryDestination.Photos -> PhotosScreen(
                    uiState = uiState,
                    onRequestAccess = requestMediaAccess,
                    onRefresh = galleryViewModel::refresh,
                    onPhotoClick = { image -> openViewer(image, uiState.images) },
                )
                GalleryDestination.Albums -> AlbumsScreen(
                    uiState = uiState,
                    onRequestAccess = requestMediaAccess,
                    onRefresh = galleryViewModel::refresh,
                    onAlbumClick = { selectedAlbumId = it.id },
                )
                GalleryDestination.Favorites -> {
                    val favoriteImages = remember(uiState.images, favoriteIds) {
                        uiState.images.filter { it.id in favoriteIds }
                    }
                    FavoriteGalleryScreen(
                        allImages = uiState.images,
                        favoriteIds = favoriteIds,
                        onPhotoClick = { image -> openViewer(image, favoriteImages) },
                    )
                }
                GalleryDestination.Settings -> SettingsScreen(
                    photoCount = uiState.images.size,
                    albumCount = uiState.albums.size,
                )
            }
        }
    }
}
