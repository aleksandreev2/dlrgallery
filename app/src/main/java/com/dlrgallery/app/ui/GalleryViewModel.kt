package com.dlrgallery.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaStoreRepository
import com.dlrgallery.app.data.currentMediaAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val access: MediaAccess = MediaAccess.None,
    val isLoading: Boolean = false,
    val images: List<MediaImage> = emptyList(),
    val albums: List<GalleryAlbum> = emptyList(),
    val errorMessage: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application.contentResolver)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val access = currentMediaAccess(getApplication())
        if (access == MediaAccess.None) {
            refreshJob?.cancel()
            _uiState.value = GalleryUiState(access = MediaAccess.None)
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                access = access,
                isLoading = true,
                errorMessage = null,
            )
            try {
                val images = repository.loadImages()
                _uiState.value = GalleryUiState(
                    access = access,
                    isLoading = false,
                    images = images,
                    albums = repository.buildAlbums(images),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (security: SecurityException) {
                _uiState.value = GalleryUiState(
                    access = MediaAccess.None,
                    errorMessage = "Android отозвал доступ к фотографиям.",
                )
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Не удалось прочитать фотографии.",
                )
            }
        }
    }
}
