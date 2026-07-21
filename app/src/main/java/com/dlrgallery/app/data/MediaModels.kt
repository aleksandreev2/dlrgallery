package com.dlrgallery.app.data

import android.net.Uri

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateTakenMillis: Long,
    val bucketId: Long,
    val bucketName: String,
    val mimeType: String,
    val mediaStoreId: Long = id,
    val isVideo: Boolean = false,
    val durationMillis: Long = 0L,
)

data class GalleryAlbum(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val imageCount: Int,
    val latestImageMillis: Long,
    val coverMediaStoreId: Long = 0L,
    val coverIsVideo: Boolean = false,
)
