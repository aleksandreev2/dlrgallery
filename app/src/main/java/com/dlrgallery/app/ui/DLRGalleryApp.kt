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
import com.dlrgallery.app.data.AppSettings
import com.dlrgallery.app.data.AppThemeMode
import com.dlrgallery.app.data.ExportQuality
import com.dlrgallery.app.data.GalleryGridSize
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaSortOrder
import com.dlrgallery.app.data.mediaPermissionsForCurrentVersion
import com.dlrgallery.app.ui.theme.DLRGalleryTheme

@Composable
fun DLRGalleryRoot(
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    DLRGalleryTheme(themeMode = settings.themeMode) {
        DLRGalleryApp(
            settings = settings,
            onThemeModeChange = settingsViewModel::setThemeMode,
            onGridSizeChange = settingsViewModel::setGridSize,
            onExportQualityChange = settingsViewModel::setExportQuality,
            onSaveAsCopyChange = settingsViewModel::setSaveAsCopy,
            onMediaSortOrderChange = settingsViewModel::setMediaSortOrder,
        )
    }
}

@Composable
fun DLRGalleryApp(
    settings: AppSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onGridSizeChange: (GalleryGridSize) -> Unit,
    onExportQualityChange: (ExportQuality) -> Unit,
    onSaveAsCopyChange: (Boolean) -> Unit,
    onMediaSortOrderChange: (MediaSortOrder) -> Unit,
    galleryViewModel: GalleryViewModel = viewModel(),
    favoritesViewModel: FavoritesViewModel = viewModel(),
) {
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(GalleryDestination.Photos) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerImageIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val requestDelete = rememberMediaDeleteRequester(
        onFinished = galleryViewModel::refresh,
    )

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
    val editorPhoto = remember(uiState.images, editorPhotoId) {
        editorPhotoId?.let { id -> uiState.images.firstOrNull { it.id == id } }
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
    LaunchedEffect(editorPhotoId, editorPhoto, uiState.isLoading) {
        if (editorPhotoId != null && editorPhoto == null && !uiState.isLoading) {
            editorPhotoId = null
        }
    }

    if (editorPhoto != null) {
        BackHandler { editorPhotoId = null }
        PhotoEditorScreen(
            image = editorPhoto,
            onBack = { editorPhotoId = null },
            onSaved = {
                editorPhotoId = null
                galleryViewModel.refresh()
            },
        )
        return
    }

    val activePhotoId = selectedPhotoId
    if (activePhotoId != null && viewerImages.isNotEmpty()) {
        BackHandler(onBack = ::closeViewer)
        ImmersivePhotoViewer(
            images = viewerImages,
            initialPhotoId = activePhotoId,
            favoriteIds = favoriteIds,
            onToggleFavorite = favoritesViewModel::toggleFavorite,
            onEdit = { image -> editorPhotoId = image.id },
            onBack = ::closeViewer,
        )
        return
    }

    if (selectedAlbum != null) {
        val albumImages = remember(uiState.images, selectedAlbum.id) {
            uiState.images.filter { it.bucketId == selectedAlbum.id }
        }
        BackHandler { selectedAlbumId = null }
        AlbumDetailBrowserScreen(
            album = selectedAlbum,
            images = albumImages,
            gridColumns = settings.gridSize.columns,
            sortOrder = settings.mediaSortOrder,
            onSortOrderChange = onMediaSortOrderChange,
            onBack = { selectedAlbumId = null },
            onDeleteRequest = requestDelete,
            onPhotoClick = ::openViewer,
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
                GalleryDestination.Photos -> PhotosBrowserScreen(
                    uiState = uiState,
                    gridColumns = settings.gridSize.columns,
                    sortOrder = settings.mediaSortOrder,
                    onSortOrderChange = onMediaSortOrderChange,
                    onRequestAccess = requestMediaAccess,
                    onRefresh = galleryViewModel::refresh,
                    onDeleteRequest = requestDelete,
                    onPhotoClick = ::openViewer,
                )
                GalleryDestination.Albums -> AlbumsBrowserScreen(
                    uiState = uiState,
                    onRequestAccess = requestMediaAccess,
                    onRefresh = galleryViewModel::refresh,
                    onAlbumClick = { selectedAlbumId = it.id },
                )
                GalleryDestination.Favorites -> FavoriteGalleryScreen(
                    allImages = uiState.images,
                    favoriteIds = favoriteIds,
                    gridColumns = settings.gridSize.columns,
                    sortOrder = settings.mediaSortOrder,
                    onSortOrderChange = onMediaSortOrderChange,
                    onDeleteRequest = requestDelete,
                    onPhotoClick = ::openViewer,
                )
                GalleryDestination.Settings -> SettingsScreen(
                    photoCount = uiState.images.size,
                    albumCount = uiState.albums.size,
                    settings = settings,
                    onThemeModeChange = onThemeModeChange,
                    onGridSizeChange = onGridSizeChange,
                    onExportQualityChange = onExportQualityChange,
                    onSaveAsCopyChange = onSaveAsCopyChange,
                )
            }
        }
    }
}
