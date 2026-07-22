package com.dlrgallery.app.data

import android.net.Uri
import java.io.File

/** A private trash entry used on Android 10 and older devices. */
data class LocalTrashEntry(
    val id: String,
    val originalUri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val dateTakenMillis: Long,
    val dateDeletedMillis: Long,
    val dateExpiresMillis: Long,
    val originalRelativePath: String,
    val originalBucketName: String,
    val payloadPath: String,
    val thumbnailPath: String,
) {
    val payloadFile: File
        get() = File(payloadPath)

    val thumbnailFile: File
        get() = File(thumbnailPath)
}

data class StagedLocalTrashEntry(
    val id: String,
    val pendingDirectoryPath: String,
)

data class TrashItem(
    val key: String,
    val displayName: String,
    val previewUri: Uri,
    val isVideo: Boolean,
    val durationMillis: Long,
    val sizeBytes: Long,
    val dateDeletedMillis: Long,
    val dateExpiresMillis: Long,
    val systemMedia: MediaImage? = null,
    val localEntryId: String? = null,
    val localOriginalUri: Uri? = null,
)

fun MediaImage.toTrashItem(): TrashItem = TrashItem(
    key = "system:$id",
    displayName = displayName,
    previewUri = uri,
    isVideo = isVideo,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    dateDeletedMillis = 0L,
    dateExpiresMillis = dateExpiresMillis,
    systemMedia = this,
)

fun LocalTrashEntry.toTrashItem(): TrashItem {
    val preview = thumbnailFile.takeIf(File::isFile) ?: payloadFile
    return TrashItem(
        key = "local:$id",
        displayName = displayName,
        previewUri = Uri.fromFile(preview),
        isVideo = isVideo,
        durationMillis = durationMillis,
        sizeBytes = sizeBytes,
        dateDeletedMillis = dateDeletedMillis,
        dateExpiresMillis = dateExpiresMillis,
        localEntryId = id,
        localOriginalUri = originalUri,
    )
}
