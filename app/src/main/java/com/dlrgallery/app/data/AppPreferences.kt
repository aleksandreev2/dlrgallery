package com.dlrgallery.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The legacy file name is intentionally kept so existing favorite IDs survive the 0.5 update.
 * Settings and favorites share this single Preferences DataStore.
 */
internal val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_photos",
)
