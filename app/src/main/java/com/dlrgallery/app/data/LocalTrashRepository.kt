package com.dlrgallery.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID
import kotlin.math.max

class LocalTrashRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val rootDirectory = File(appContext.filesDir, ROOT_DIRECTORY_NAME)
    private val activeDirectory = File(rootDirectory, ACTIVE_DIRECTORY_NAME)
    private val pendingDirectory = File(rootDirectory, PENDING_DIRECTORY_NAME)
    private val legacyExternalRoot = appContext.getExternalFilesDir(null)
        ?.let { File(it, ROOT_DIRECTORY_NAME) }
        ?.takeUnless { it.absolutePath == rootDirectory.absolutePath }

    suspend fun loadEntries(): List<LocalTrashEntry> = withContext(Dispatchers.IO) {
        ensureDirectories()
        recoverInterruptedMoves()
        purgeExpiredInternal()
        activeDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull(::readEntry)
            .sortedByDescending(LocalTrashEntry::dateDeletedMillis)
            .toList()
    }

    suspend fun stage(image: MediaImage): StagedLocalTrashEntry = withContext(Dispatchers.IO) {
        ensureDirectories()
        val requiredBytes = max(image.sizeBytes, MINIMUM_RESERVED_BYTES) + MINIMUM_RESERVED_BYTES
        val availableBytes = StatFs(rootDirectory.absolutePath).availableBytes
        if (availableBytes < requiredBytes) {
            throw IOException("Недостаточно места для безопасного перемещения в корзину")
        }

        val id = UUID.randomUUID().toString()
        val entryDirectory = File(pendingDirectory, id)
        if (!entryDirectory.mkdirs()) {
            throw IOException("Не удалось подготовить локальную корзину")
        }

        try {
            val payloadName = PAYLOAD_BASENAME + fileExtension(image.displayName, image.mimeType)
            val payloadFile = File(entryDirectory, payloadName)
            resolver.openInputStream(image.uri)?.use { input ->
                FileOutputStream(payloadFile).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                    output.fd.sync()
                }
            } ?: throw IOException("Не удалось прочитать ${image.displayName.ifBlank { "файл" }}")

            if (!payloadFile.isFile || payloadFile.length() <= 0L) {
                throw IOException("Не удалось создать резервную копию файла")
            }

            val thumbnailFile = File(entryDirectory, THUMBNAIL_FILE_NAME)
            createThumbnail(image, payloadFile, thumbnailFile)

            val now = System.currentTimeMillis()
            writeMetadata(
                directory = entryDirectory,
                image = image,
                payloadName = payloadName,
                thumbnailName = thumbnailFile.takeIf(File::isFile)?.name.orEmpty(),
                dateDeletedMillis = now,
                dateExpiresMillis = now + RETENTION_MILLIS,
            )
            StagedLocalTrashEntry(id = id, pendingDirectoryPath = entryDirectory.absolutePath)
        } catch (error: Throwable) {
            entryDirectory.deleteRecursively()
            throw error
        }
    }

    suspend fun commit(stage: StagedLocalTrashEntry) = withContext(Dispatchers.IO) {
        ensureDirectories()
        val pending = File(stage.pendingDirectoryPath)
        if (!pending.isDirectory || readEntry(pending) == null) {
            throw IOException("Резервная копия корзины потеряна")
        }
        promoteToActive(pending)
    }

    suspend fun discard(stage: StagedLocalTrashEntry) = withContext(Dispatchers.IO) {
        File(stage.pendingDirectoryPath).deleteRecursively()
    }

    suspend fun restore(ids: Collection<String>): LocalTrashOperationResult = withContext(Dispatchers.IO) {
        ensureDirectories()
        recoverInterruptedMoves()
        var restored = 0
        var failed = 0
        ids.distinct().forEach { id ->
            val directory = File(activeDirectory, id)
            val entry = readEntry(directory)
            if (entry == null) {
                failed += 1
                return@forEach
            }
            runCatching { restoreEntry(entry) }
                .onSuccess {
                    directory.deleteRecursively()
                    restored += 1
                }
                .onFailure { failed += 1 }
        }
        LocalTrashOperationResult(restored, failed)
    }

    suspend fun deletePermanently(ids: Collection<String>): LocalTrashOperationResult = withContext(Dispatchers.IO) {
        ensureDirectories()
        recoverInterruptedMoves()
        var deleted = 0
        var failed = 0
        ids.distinct().forEach { id ->
            val directory = File(activeDirectory, id)
            if (!directory.exists() || directory.deleteRecursively()) {
                deleted += 1
            } else {
                failed += 1
            }
        }
        LocalTrashOperationResult(deleted, failed)
    }

    private fun restoreEntry(entry: LocalTrashEntry) {
        val payload = entry.payloadFile
        if (!payload.isFile) throw IOException("Файл в корзине повреждён")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            restoreWithMediaStore(entry, payload)
        } else {
            restoreOnLegacyAndroid(entry, payload)
        }
    }

    private fun restoreWithMediaStore(entry: LocalTrashEntry, payload: File) {
        val collection = if (entry.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = normalizeRelativePath(entry.originalRelativePath, entry.originalBucketName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.displayName.ifBlank { fallbackName(entry) })
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, entry.dateTakenMillis)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val restoredUri = resolver.insert(collection, values)
            ?: throw IOException("Android не создал восстановленный файл")
        try {
            resolver.openOutputStream(restoredUri, "w")?.use { output ->
                FileInputStream(payload).use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
            } ?: throw IOException("Не удалось записать восстановленный файл")
            resolver.update(
                restoredUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(restoredUri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreOnLegacyAndroid(entry: LocalTrashEntry, payload: File) {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumName = sanitizeDirectoryName(entry.originalBucketName).ifBlank { "DLR Gallery Restored" }
        val destinationDirectory = File(pictures, albumName)
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw IOException("Не удалось создать папку для восстановления")
        }
        val destination = uniqueDestination(destinationDirectory, entry.displayName.ifBlank { fallbackName(entry) })
        FileInputStream(payload).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, COPY_BUFFER_SIZE)
                output.fd.sync()
            }
        }
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(destination.absolutePath),
            arrayOf(entry.mimeType),
            null,
        )
    }

    /**
     * A completely written pending copy is never deleted automatically. MIUI can keep a stale
     * MediaStore row after the original file is gone, so checking that row can destroy the only
     * remaining copy. An interrupted operation may therefore leave a duplicate, but never data loss.
     */
    private fun recoverInterruptedMoves() {
        pendingDirectory.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            if (readEntry(directory) == null) {
                directory.deleteRecursively()
            } else {
                promoteToActive(directory)
            }
        }
    }

    private fun promoteToActive(source: File) {
        val active = File(activeDirectory, source.name)
        if (active.exists()) {
            if (readEntry(active) != null) {
                source.deleteRecursively()
                return
            }
            active.deleteRecursively()
        }
        if (!source.renameTo(active)) {
            if (!source.copyRecursively(active, overwrite = false)) {
                throw IOException("Не удалось завершить перемещение в корзину")
            }
            source.deleteRecursively()
        }
        if (readEntry(active) == null) {
            active.deleteRecursively()
            throw IOException("Запись корзины повреждена после перемещения")
        }
    }

    private fun purgeExpiredInternal() {
        val now = System.currentTimeMillis()
        activeDirectory.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            val entry = readEntry(directory)
            if (entry == null || (entry.dateExpiresMillis > 0L && entry.dateExpiresMillis <= now)) {
                directory.deleteRecursively()
            }
        }
    }

    private fun migrateLegacyExternalStorage() {
        val legacyRoot = legacyExternalRoot ?: return
        if (!legacyRoot.isDirectory) return

        migrateChildren(File(legacyRoot, ACTIVE_DIRECTORY_NAME), activeDirectory)
        migrateChildren(File(legacyRoot, PENDING_DIRECTORY_NAME), pendingDirectory)
        legacyRoot.deleteRecursively()
    }

    private fun migrateChildren(sourceParent: File, destinationParent: File) {
        sourceParent.listFiles().orEmpty().filter(File::isDirectory).forEach { source ->
            val destination = File(destinationParent, source.name)
            if (destination.exists()) {
                if (readEntry(destination) != null) {
                    source.deleteRecursively()
                    return@forEach
                }
                destination.deleteRecursively()
            }
            if (!source.renameTo(destination)) {
                if (source.copyRecursively(destination, overwrite = false)) {
                    source.deleteRecursively()
                }
            }
        }
    }

    private fun writeMetadata(
        directory: File,
        image: MediaImage,
        payloadName: String,
        thumbnailName: String,
        dateDeletedMillis: Long,
        dateExpiresMillis: Long,
    ) {
        val properties = Properties().apply {
            setProperty(KEY_ORIGINAL_URI, image.uri.toString())
            setProperty(KEY_DISPLAY_NAME, image.displayName)
            setProperty(KEY_MIME_TYPE, image.mimeType)
            setProperty(KEY_IS_VIDEO, image.isVideo.toString())
            setProperty(KEY_SIZE_BYTES, image.sizeBytes.toString())
            setProperty(KEY_WIDTH, image.width.toString())
            setProperty(KEY_HEIGHT, image.height.toString())
            setProperty(KEY_DURATION, image.durationMillis.toString())
            setProperty(KEY_DATE_TAKEN, image.dateTakenMillis.toString())
            setProperty(KEY_DATE_DELETED, dateDeletedMillis.toString())
            setProperty(KEY_DATE_EXPIRES, dateExpiresMillis.toString())
            setProperty(KEY_RELATIVE_PATH, image.relativePath)
            setProperty(KEY_BUCKET_NAME, image.bucketName)
            setProperty(KEY_PAYLOAD_NAME, payloadName)
            setProperty(KEY_THUMBNAIL_NAME, thumbnailName)
        }
        FileOutputStream(File(directory, METADATA_FILE_NAME)).use { output ->
            properties.store(output, "DLR Gallery local trash")
            output.fd.sync()
        }
    }

    private fun readEntry(directory: File): LocalTrashEntry? {
        val properties = readProperties(directory) ?: return null
        val payloadName = properties.getProperty(KEY_PAYLOAD_NAME).orEmpty()
        val payload = File(directory, payloadName)
        if (payloadName.isBlank() || !payload.isFile || payload.length() <= 0L) return null
        val thumbnailName = properties.getProperty(KEY_THUMBNAIL_NAME).orEmpty()
        return LocalTrashEntry(
            id = directory.name,
            displayName = properties.getProperty(KEY_DISPLAY_NAME).orEmpty(),
            mimeType = properties.getProperty(KEY_MIME_TYPE).orEmpty(),
            isVideo = properties.getProperty(KEY_IS_VIDEO).toBoolean(),
            sizeBytes = properties.long(KEY_SIZE_BYTES, payload.length()),
            width = properties.int(KEY_WIDTH),
            height = properties.int(KEY_HEIGHT),
            durationMillis = properties.long(KEY_DURATION),
            dateTakenMillis = properties.long(KEY_DATE_TAKEN),
            dateDeletedMillis = properties.long(KEY_DATE_DELETED),
            dateExpiresMillis = properties.long(KEY_DATE_EXPIRES),
            originalRelativePath = properties.getProperty(KEY_RELATIVE_PATH).orEmpty(),
            originalBucketName = properties.getProperty(KEY_BUCKET_NAME).orEmpty(),
            payloadPath = payload.absolutePath,
            thumbnailPath = thumbnailName.takeIf(String::isNotBlank)
                ?.let { File(directory, it).absolutePath }
                .orEmpty(),
        )
    }

    private fun readProperties(directory: File): Properties? = runCatching {
        Properties().apply {
            FileInputStream(File(directory, METADATA_FILE_NAME)).use(::load)
        }
    }.getOrNull()

    private fun createThumbnail(image: MediaImage, payload: File, target: File) {
        val bitmap = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    resolver.loadThumbnail(image.uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
                }
                image.isVideo -> createVideoThumbnail(payload)
                else -> decodeSampledBitmap(payload, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            }
        }.getOrNull() ?: return

        runCatching {
            FileOutputStream(target).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output) }
        }
        bitmap.recycle()
    }

    private fun createVideoThumbnail(payload: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(payload.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE,
                )
            } else {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { bitmap ->
                        Bitmap.createScaledBitmap(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true).also {
                            if (it !== bitmap) bitmap.recycle()
                        }
                    }
            }
        } finally {
            retriever.release()
        }
    }

    private fun decodeSampledBitmap(file: File, requiredWidth: Int, requiredHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > requiredWidth * 2 || bounds.outHeight / sampleSize > requiredHeight * 2) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) },
        )
    }

    private fun ensureDirectories() {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IOException("Не удалось открыть приватное хранилище корзины")
        }
        if (!activeDirectory.exists() && !activeDirectory.mkdirs()) {
            throw IOException("Не удалось открыть содержимое корзины")
        }
        if (!pendingDirectory.exists() && !pendingDirectory.mkdirs()) {
            throw IOException("Не удалось открыть временное хранилище корзины")
        }
        migrateLegacyExternalStorage()
    }

    private fun normalizeRelativePath(relativePath: String, bucketName: String): String {
        val normalized = relativePath.trim().trimStart('/').takeIf(String::isNotBlank)
            ?: "${Environment.DIRECTORY_PICTURES}/${sanitizeDirectoryName(bucketName).ifBlank { "DLR Gallery Restored" }}/"
        return if (normalized.endsWith('/')) normalized else "$normalized/"
    }

    private fun fallbackName(entry: LocalTrashEntry): String = if (entry.isVideo) {
        "DLR_${System.currentTimeMillis()}.mp4"
    } else {
        "DLR_${System.currentTimeMillis()}.jpg"
    }

    private fun fileExtension(displayName: String, mimeType: String): String {
        val extension = displayName.substringAfterLast('.', "").takeIf { it.length in 1..8 }
            ?: when (mimeType.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic", "image/heif" -> "heic"
                "video/webm" -> "webm"
                "video/3gpp" -> "3gp"
                else -> if (mimeType.startsWith("video/")) "mp4" else "jpg"
            }
        return ".$extension"
    }

    private fun sanitizeDirectoryName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(80)

    private fun uniqueDestination(directory: File, requestedName: String): File {
        val safeName = requestedName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "DLR_file" }
        var candidate = File(directory, safeName)
        if (!candidate.exists()) return candidate
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").takeIf { it != safeName }.orEmpty()
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index += 1
        }
        return candidate
    }

    private fun Properties.long(key: String, fallback: Long = 0L): Long =
        getProperty(key)?.toLongOrNull() ?: fallback

    private fun Properties.int(key: String, fallback: Int = 0): Int =
        getProperty(key)?.toIntOrNull() ?: fallback

    companion object {
        private const val ROOT_DIRECTORY_NAME = "dlr_local_trash"
        private const val ACTIVE_DIRECTORY_NAME = "items"
        private const val PENDING_DIRECTORY_NAME = "pending"
        private const val METADATA_FILE_NAME = "metadata.properties"
        private const val PAYLOAD_BASENAME = "payload"
        private const val THUMBNAIL_FILE_NAME = "thumbnail.jpg"
        private const val THUMBNAIL_SIZE = 512
        private const val COPY_BUFFER_SIZE = 256 * 1024
        private const val MINIMUM_RESERVED_BYTES = 8L * 1024L * 1024L
        private const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

        private const val KEY_ORIGINAL_URI = "originalUri"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_IS_VIDEO = "isVideo"
        private const val KEY_SIZE_BYTES = "sizeBytes"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DURATION = "durationMillis"
        private const val KEY_DATE_TAKEN = "dateTakenMillis"
        private const val KEY_DATE_DELETED = "dateDeletedMillis"
        private const val KEY_DATE_EXPIRES = "dateExpiresMillis"
        private const val KEY_RELATIVE_PATH = "relativePath"
        private const val KEY_BUCKET_NAME = "bucketName"
        private const val KEY_PAYLOAD_NAME = "payloadName"
        private const val KEY_THUMBNAIL_NAME = "thumbnailName"
    }
}

data class LocalTrashOperationResult(
    val successCount: Int,
    val failedCount: Int,
)
