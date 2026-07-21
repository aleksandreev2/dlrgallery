package com.dlrgallery.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlrgallery.app.data.GalleryAlbum
import com.dlrgallery.app.data.LocalTrashOperationResult
import com.dlrgallery.app.data.LocalTrashRepository
import com.dlrgallery.app.data.MediaAccess
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaStoreRepository
import com.dlrgallery.app.data.TrashItem
import com.dlrgallery.app.data.currentMediaAccess
import com.dlrgallery.app.data.toTrashItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val access: MediaAccess = MediaAccess.None,
    val isLoading: Boolean = false,
    val images: List<MediaImage> = emptyList(),
    val trashedImages: List<MediaImage> = emptyList(),
    val albums: List<GalleryAlbum> = emptyList(),
    val trashItems: List<TrashItem> = emptyList(),
    val isTrashBusy: Boolean = false,
    val operationMessage: String? = null,
    val errorMessage: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application.contentResolver)
    private val localTrashRepository = LocalTrashRepository(application)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var trashJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val access = currentMediaAccess(getApplication())
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = previous.copy(
                access = access,
                isLoading = true,
                errorMessage = null,
            )
            try {
                val images = if (access == MediaAccess.None) emptyList() else repository.loadImages()
                val systemTrash = if (access == MediaAccess.None) {
                    emptyList()
                } else {
                    repository.loadTrashedImages()
                }
                val trashItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    systemTrash.map(MediaImage::toTrashItem)
                } else {
                    localTrashRepository.loadEntries().map { it.toTrashItem() }
                }
                _uiState.value = GalleryUiState(
                    access = access,
                    isLoading = false,
                    images = images,
                    trashedImages = systemTrash,
                    albums = repository.buildAlbums(images),
                    trashItems = trashItems,
                    isTrashBusy = previous.isTrashBusy,
                    operationMessage = previous.operationMessage,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (security: SecurityException) {
                _uiState.value = GalleryUiState(
                    access = MediaAccess.None,
                    isLoading = false,
                    isTrashBusy = previous.isTrashBusy,
                    operationMessage = previous.operationMessage,
                    errorMessage = "Android отозвал доступ к фотографиям и видео.",
                )
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Не удалось прочитать медиатеку.",
                )
            }
        }
    }

    fun restoreLocalTrash(ids: Collection<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ids.isEmpty()) return
        runLocalTrashOperation(
            operation = { localTrashRepository.restore(ids) },
            successWord = "Восстановлено",
        )
    }

    fun deleteLocalTrashPermanently(ids: Collection<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ids.isEmpty()) return
        runLocalTrashOperation(
            operation = { localTrashRepository.deletePermanently(ids) },
            successWord = "Удалено навсегда",
        )
    }

    fun consumeOperationMessage() {
        _uiState.update { state -> state.copy(operationMessage = null) }
    }

    private fun runLocalTrashOperation(
        operation: suspend () -> LocalTrashOperationResult,
        successWord: String,
    ) {
        trashJob?.cancel()
        trashJob = viewModelScope.launch {
            _uiState.update { state -> state.copy(isTrashBusy = true) }
            val message = runCatching(operation).fold(
                onSuccess = { result -> operationMessage(successWord, result) },
                onFailure = { error -> error.message ?: "Операция с корзиной не выполнена" },
            )
            _uiState.update { state ->
                state.copy(isTrashBusy = false, operationMessage = message)
            }
            refresh()
        }
    }

    private fun operationMessage(
        successWord: String,
        result: LocalTrashOperationResult,
    ): String = when {
        result.failedCount == 0 -> "$successWord: ${formatFileCount(result.successCount)}"
        result.successCount == 0 -> "Не удалось обработать ${formatFileCount(result.failedCount)}"
        else -> "$successWord: ${formatFileCount(result.successCount)}, ошибок: ${result.failedCount}"
    }

    private fun formatFileCount(count: Int): String = when {
        count % 10 == 1 && count % 100 != 11 -> "$count файл"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
        else -> "$count файлов"
    }
}
