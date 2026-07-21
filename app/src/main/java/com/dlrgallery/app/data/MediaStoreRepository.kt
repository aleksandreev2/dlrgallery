package com.dlrgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadImages(): List<MediaImage> = withContext(Dispatchers.IO) {
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

        val images = mutableListOf<MediaImage>()
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.SIZE} > 0",
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                    .takeIf { it > 0L }
                    ?: cursor.getLong(dateAddedColumn) * 1_000L
                val bucketName = cursor.getString(bucketNameColumn)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Без альбома"

                images += MediaImage(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameColumn).orEmpty(),
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    dateTakenMillis = dateTaken,
                    bucketId = cursor.getLong(bucketIdColumn),
                    bucketName = bucketName,
                    mimeType = cursor.getString(mimeTypeColumn).orEmpty(),
                )
            }
        }
        images
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
            )
        }
        .sortedByDescending(GalleryAlbum::latestImageMillis)
}
