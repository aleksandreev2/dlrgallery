package com.dlrgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        loadMedia(trashedOnly = false)
    }

    suspend fun loadTrashedImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            emptyList()
        } else {
            loadMedia(trashedOnly = true)
        }
    }

    private fun loadMedia(trashedOnly: Boolean): List<MediaImage> = buildList {
        addAll(loadImageCollection(trashedOnly))
        addAll(loadVideoCollection(trashedOnly))
    }.sortedWith(
        if (trashedOnly) {
            compareBy<MediaImage> { it.dateExpiresMillis.takeIf { value -> value > 0L } ?: Long.MAX_VALUE }
                .thenByDescending { it.dateTakenMillis }
        } else {
            compareByDescending<MediaImage> { it.dateTakenMillis }
                .thenByDescending { it.id }
        },
    )

    private fun loadImageCollection(trashedOnly: Boolean): List<MediaImage> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = commonProjection(
            id = MediaStore.Images.Media._ID,
            displayName = MediaStore.Images.Media.DISPLAY_NAME,
            width = MediaStore.Images.Media.WIDTH,
            height = MediaStore.Images.Media.HEIGHT,
            size = MediaStore.Images.Media.SIZE,
            dateTaken = MediaStore.Images.Media.DATE_TAKEN,
            dateAdded = MediaStore.Images.Media.DATE_ADDED,
            bucketId = MediaStore.Images.Media.BUCKET_ID,
            bucketName = MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            mimeType = MediaStore.Images.Media.MIME_TYPE,
        )

        return queryCollection(
            collection = collection,
            projection = projection,
            isVideo = false,
            durationColumnName = null,
            trashedOnly = trashedOnly,
        )
    }

    private fun loadVideoCollection(trashedOnly: Boolean): List<MediaImage> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = commonProjection(
            id = MediaStore.Video.Media._ID,
            displayName = MediaStore.Video.Media.DISPLAY_NAME,
            width = MediaStore.Video.Media.WIDTH,
            height = MediaStore.Video.Media.HEIGHT,
            size = MediaStore.Video.Media.SIZE,
            dateTaken = MediaStore.Video.Media.DATE_TAKEN,
            dateAdded = MediaStore.Video.Media.DATE_ADDED,
            bucketId = MediaStore.Video.Media.BUCKET_ID,
            bucketName = MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            mimeType = MediaStore.Video.Media.MIME_TYPE,
            duration = MediaStore.Video.Media.DURATION,
        )

        return queryCollection(
            collection = collection,
            projection = projection,
            isVideo = true,
            durationColumnName = MediaStore.Video.Media.DURATION,
            trashedOnly = trashedOnly,
        )
    }

    private fun commonProjection(
        id: String,
        displayName: String,
        width: String,
        height: String,
        size: String,
        dateTaken: String,
        dateAdded: String,
        bucketId: String,
        bucketName: String,
        mimeType: String,
        duration: String? = null,
    ): Array<String> = buildList {
        add(id)
        add(displayName)
        add(width)
        add(height)
        add(size)
        add(dateTaken)
        add(dateAdded)
        add(bucketId)
        add(bucketName)
        add(mimeType)
        duration?.let(::add)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.MediaColumns.IS_TRASHED)
            add(MediaStore.MediaColumns.DATE_EXPIRES)
        }
    }.toTypedArray()

    private fun queryCollection(
        collection: Uri,
        projection: Array<String>,
        isVideo: Boolean,
        durationColumnName: String?,
        trashedOnly: Boolean,
    ): List<MediaImage> {
        val result = mutableListOf<MediaImage>()
        val cursor = try {
            if (trashedOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.MediaColumns.SIZE} > 0",
                    )
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        "${MediaStore.MediaColumns.DATE_EXPIRES} ASC, " +
                            "${MediaStore.MediaColumns.DATE_TAKEN} DESC, " +
                            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                    )
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.SIZE} > 0",
                    null,
                    "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )
            }
        } catch (_: SecurityException) {
            // Android can grant photos while videos remain denied, or vice versa.
            null
        }

        cursor?.use { mediaCursor ->
            val idColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val widthColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val sizeColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateTakenColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateAddedColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val bucketIdColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val mimeTypeColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val durationColumn = durationColumnName?.let(mediaCursor::getColumnIndex)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaCursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                -1
            }
            val trashedColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaCursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            } else {
                -1
            }
            val expiresColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaCursor.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
            } else {
                -1
            }

            while (mediaCursor.moveToNext()) {
                result += mediaCursor.toMediaImage(
                    collection = collection,
                    isVideo = isVideo,
                    idColumn = idColumn,
                    nameColumn = nameColumn,
                    widthColumn = widthColumn,
                    heightColumn = heightColumn,
                    sizeColumn = sizeColumn,
                    dateTakenColumn = dateTakenColumn,
                    dateAddedColumn = dateAddedColumn,
                    bucketIdColumn = bucketIdColumn,
                    bucketNameColumn = bucketNameColumn,
                    mimeTypeColumn = mimeTypeColumn,
                    durationColumn = durationColumn,
                    relativePathColumn = relativePathColumn,
                    trashedColumn = trashedColumn,
                    expiresColumn = expiresColumn,
                    trashedOnly = trashedOnly,
                )
            }
        }
        return result
    }

    private fun Cursor.toMediaImage(
        collection: Uri,
        isVideo: Boolean,
        idColumn: Int,
        nameColumn: Int,
        widthColumn: Int,
        heightColumn: Int,
        sizeColumn: Int,
        dateTakenColumn: Int,
        dateAddedColumn: Int,
        bucketIdColumn: Int,
        bucketNameColumn: Int,
        mimeTypeColumn: Int,
        durationColumn: Int?,
        relativePathColumn: Int,
        trashedColumn: Int,
        expiresColumn: Int,
        trashedOnly: Boolean,
    ): MediaImage {
        val mediaStoreId = getLong(idColumn)
        val stableId = if (isVideo) -mediaStoreId - 1L else mediaStoreId
        val dateTaken = getLong(dateTakenColumn)
            .takeIf { it > 0L }
            ?: getLong(dateAddedColumn) * 1_000L
        val bucketName = getString(bucketNameColumn)
            ?.takeIf(String::isNotBlank)
            ?: "Без альбома"
        val expiresMillis = expiresColumn
            .takeIf { it >= 0 }
            ?.let(::getLong)
            ?.times(1_000L)
            ?: 0L

        return MediaImage(
            id = stableId,
            mediaStoreId = mediaStoreId,
            uri = ContentUris.withAppendedId(collection, mediaStoreId),
            displayName = getString(nameColumn).orEmpty(),
            width = getInt(widthColumn),
            height = getInt(heightColumn),
            sizeBytes = getLong(sizeColumn),
            dateTakenMillis = dateTaken,
            bucketId = getLong(bucketIdColumn),
            bucketName = bucketName,
            mimeType = getString(mimeTypeColumn).orEmpty(),
            isVideo = isVideo,
            durationMillis = durationColumn
                ?.takeIf { it >= 0 }
                ?.let(::getLong)
                ?: 0L,
            relativePath = relativePathColumn
                .takeIf { it >= 0 }
                ?.let(::getString)
                .orEmpty(),
            isTrashed = trashedColumn
                .takeIf { it >= 0 }
                ?.let { getInt(it) != 0 }
                ?: trashedOnly,
            dateExpiresMillis = expiresMillis,
        )
    }

    fun buildAlbums(images: List<MediaImage>): List<GalleryAlbum> = images
        .groupBy(MediaImage::bucketId)
        .mapNotNull { (bucketId, albumImages) ->
            val cover = albumImages.firstOrNull() ?: return@mapNotNull null
            GalleryAlbum(
                id = bucketId,
                name = cover.bucketName,
                coverUri = cover.uri,
                imageCount = albumImages.size,
                latestImageMillis = cover.dateTakenMillis,
                coverMediaStoreId = cover.mediaStoreId,
                coverIsVideo = cover.isVideo,
                relativePath = cover.relativePath,
            )
        }
        .sortedByDescending(GalleryAlbum::latestImageMillis)
}
