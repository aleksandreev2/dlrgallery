package com.dlrgallery.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlrgallery.app.data.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FavoritesRepository(application)

    val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptySet(),
    )

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            repository.toggle(imageId)
        }
    }

    fun removeMissingImages(validIds: Set<Long>) {
        viewModelScope.launch {
            repository.retainOnly(validIds)
        }
    }
}
