package com.dlrgallery.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaOrganizationRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun moveToRelativePath(
        items: List<MediaImage>,
        targetRelativePath: String,
    ): Int = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Перемещение между альбомами требует Android 10 или новее."
        }
        val normalizedPath = normalizeRelativePath(targetRelativePath)
        var moved = 0
        items.distinctBy(MediaImage::uri).forEach { item ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedPath)
            }
            if (contentResolver.update(item.uri, values, null, null) > 0) {
                moved += 1
            }
        }
        moved
    }

    suspend fun moveSingleToRelativePath(
        item: MediaImage,
        targetRelativePath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Перемещение между альбомами требует Android 10 или новее."
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, normalizeRelativePath(targetRelativePath))
        }
        contentResolver.update(item.uri, values, null, null) > 0
    }

    suspend fun copyToRelativePath(
        items: List<MediaImage>,
        targetRelativePath: String,
    ): Int = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Копирование в альбом требует Android 10 или новее."
        }
        val normalizedPath = normalizeRelativePath(targetRelativePath)
        var copied = 0
        items.distinctBy(MediaImage::uri).forEach { item ->
            if (copyItem(item, normalizedPath)) copied += 1
        }
        copied
    }

    private fun copyItem(item: MediaImage, targetRelativePath: String): Boolean {
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTakenMillis)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val targetUri = contentResolver.insert(collection, values) ?: return false

        return try {
            val input = contentResolver.openInputStream(item.uri)
                ?: error("Не удалось открыть исходный файл")
            val output = contentResolver.openOutputStream(targetUri, "w")
                ?: error("Не удалось создать копию")
            input.use { source ->
                output.use { destination ->
                    source.copyTo(destination, bufferSize = COPY_BUFFER_SIZE)
                }
            }
            contentResolver.update(
                targetUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            true
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(targetUri, null, null) }
            throw error
        }
    }
}

fun newAlbumRelativePath(albumName: String): String =
    "${Environment.DIRECTORY_PICTURES}/${sanitizeAlbumName(albumName)}/"

fun renamedAlbumRelativePath(currentRelativePath: String, albumName: String): String {
    val cleanName = sanitizeAlbumName(albumName)
    val current = currentRelativePath.trim().trim('/')
    val parent = current.substringBeforeLast('/', Environment.DIRECTORY_PICTURES)
        .ifBlank { Environment.DIRECTORY_PICTURES }
    return "$parent/$cleanName/"
}

fun normalizeRelativePath(relativePath: String): String {
    val clean = relativePath
        .replace('\\', '/')
        .split('/')
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("/")
    return "${clean.ifBlank { Environment.DIRECTORY_PICTURES }}/"
}

fun sanitizeAlbumName(albumName: String): String {
    val sanitized = albumName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim('.', ' ')
    require(sanitized.isNotBlank()) { "Введите название альбома." }
    return sanitized.take(MAX_ALBUM_NAME_LENGTH)
}

private const val COPY_BUFFER_SIZE = 128 * 1024
private const val MAX_ALBUM_NAME_LENGTH = 80
