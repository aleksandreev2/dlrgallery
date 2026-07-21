package com.dlrgallery.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AppSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.appPreferencesDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[THEME_MODE]
                    .toEnumOrDefault(AppThemeMode.System),
                gridSize = preferences[GRID_SIZE]
                    .toEnumOrDefault(GalleryGridSize.Normal),
                exportQuality = preferences[EXPORT_QUALITY]
                    .toEnumOrDefault(ExportQuality.High),
                saveAsCopy = preferences[SAVE_AS_COPY] ?: true,
            )
        }

    suspend fun setThemeMode(value: AppThemeMode) {
        dataStore.edit { it[THEME_MODE] = value.name }
    }

    suspend fun setGridSize(value: GalleryGridSize) {
        dataStore.edit { it[GRID_SIZE] = value.name }
    }

    suspend fun setExportQuality(value: ExportQuality) {
        dataStore.edit { it[EXPORT_QUALITY] = value.name }
    }

    suspend fun setSaveAsCopy(value: Boolean) {
        dataStore.edit { it[SAVE_AS_COPY] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GRID_SIZE = stringPreferencesKey("gallery_grid_size")
        val EXPORT_QUALITY = stringPreferencesKey("export_quality")
        val SAVE_AS_COPY = booleanPreferencesKey("save_as_copy")
    }
}
