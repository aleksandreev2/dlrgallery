package com.dlrgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        buildList {
            addAll(loadImageCollection())
            addAll(loadVideoCollection())
        }.sortedWith(
            compareByDescending<MediaImage> { it.dateTakenMillis }
                .thenByDescending { it.id },
        )
    }

    private fun loadImageCollection(): List<MediaImage> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )

        return queryCollection(
            collection = collection,
            projection = projection,
            isVideo = false,
            durationColumnName = null,
        )
    }

    private fun loadVideoCollection(): List<MediaImage> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
        )

        return queryCollection(
            collection = collection,
            projection = projection,
            isVideo = true,
            durationColumnName = MediaStore.Video.Media.DURATION,
        )
    }

    private fun queryCollection(
        collection: Uri,
        projection: Array<String>,
        isVideo: Boolean,
        durationColumnName: String?,
    ): List<MediaImage> {
        val result = mutableListOf<MediaImage>()
        val cursor = try {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )
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

            while (mediaCursor.moveToNext()) {
                val mediaStoreId = mediaCursor.getLong(idColumn)
                val stableId = if (isVideo) -mediaStoreId - 1L else mediaStoreId
                val dateTaken = mediaCursor.getLong(dateTakenColumn)
                    .takeIf { it > 0L }
                    ?: mediaCursor.getLong(dateAddedColumn) * 1_000L
                val bucketName = mediaCursor.getString(bucketNameColumn)
                    ?.takeIf(String::isNotBlank)
                    ?: "Без альбома"

                result += MediaImage(
                    id = stableId,
                    mediaStoreId = mediaStoreId,
                    uri = ContentUris.withAppendedId(collection, mediaStoreId),
                    displayName = mediaCursor.getString(nameColumn).orEmpty(),
                    width = mediaCursor.getInt(widthColumn),
                    height = mediaCursor.getInt(heightColumn),
                    sizeBytes = mediaCursor.getLong(sizeColumn),
                    dateTakenMillis = dateTaken,
                    bucketId = mediaCursor.getLong(bucketIdColumn),
                    bucketName = bucketName,
                    mimeType = mediaCursor.getString(mimeTypeColumn).orEmpty(),
                    isVideo = isVideo,
                    durationMillis = durationColumn
                        ?.takeIf { it >= 0 }
                        ?.let(mediaCursor::getLong)
                        ?: 0L,
                )
            }
        }
        return result
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
            )
        }
        .sortedByDescending(GalleryAlbum::latestImageMillis)
}
