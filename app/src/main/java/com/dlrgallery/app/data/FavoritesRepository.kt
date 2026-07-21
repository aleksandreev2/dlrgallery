package com.dlrgallery.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class FavoritesRepository(context: Context) {
    private val dataStore = context.applicationContext.appPreferencesDataStore

    val favoriteIds: Flow<Set<Long>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            preferences[FAVORITE_IDS]
                .orEmpty()
                .mapNotNull(String::toLongOrNull)
                .toSet()
        }

    suspend fun toggle(imageId: Long) {
        dataStore.edit { preferences ->
            val current = preferences[FAVORITE_IDS].orEmpty().toMutableSet()
            val value = imageId.toString()
            if (!current.add(value)) {
                current.remove(value)
            }
            preferences[FAVORITE_IDS] = current
        }
    }

    suspend fun retainOnly(validIds: Set<Long>) {
        dataStore.edit { preferences ->
            val current = preferences[FAVORITE_IDS].orEmpty()
            val cleaned = current.filterTo(mutableSetOf()) { value ->
                value.toLongOrNull() in validIds
            }
            if (cleaned != current) {
                preferences[FAVORITE_IDS] = cleaned
            }
        }
    }

    private companion object {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_image_ids")
    }
}
