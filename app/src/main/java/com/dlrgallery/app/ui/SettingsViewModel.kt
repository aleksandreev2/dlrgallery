package com.dlrgallery.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlrgallery.app.data.AppSettings
import com.dlrgallery.app.data.AppSettingsRepository
import com.dlrgallery.app.data.AppThemeMode
import com.dlrgallery.app.data.ExportQuality
import com.dlrgallery.app.data.GalleryGridSize
import com.dlrgallery.app.data.MediaSortOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppSettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(value: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(value) }
    }

    fun setGridSize(value: GalleryGridSize) {
        viewModelScope.launch { repository.setGridSize(value) }
    }

    fun setExportQuality(value: ExportQuality) {
        viewModelScope.launch { repository.setExportQuality(value) }
    }

    fun setSaveAsCopy(value: Boolean) {
        viewModelScope.launch { repository.setSaveAsCopy(value) }
    }

    fun setMediaSortOrder(value: MediaSortOrder) {
        viewModelScope.launch { repository.setMediaSortOrder(value) }
    }
}
