package com.dlrgallery.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** User-facing ordering for media collections. */
enum class MediaSortOrder(val label: String) {
    Newest("Сначала новые"),
    Oldest("Сначала старые"),
    Name("По имени"),
    Size("По размеру"),
}

enum class MediaTypeFilter(val label: String) {
    All("Все"),
    Photos("Фото"),
    Videos("Видео"),
    Jpeg("JPEG"),
    Png("PNG"),
    Webp("WebP"),
    Other("Другие фото"),
}

enum class MediaDateFilter(val label: String) {
    All("За всё время"),
    Today("Сегодня"),
    LastSevenDays("Последние 7 дней"),
    LastThirtyDays("Последние 30 дней"),
}

data class MediaFilterState(
    val type: MediaTypeFilter = MediaTypeFilter.All,
    val date: MediaDateFilter = MediaDateFilter.All,
    val largeFilesOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = type != MediaTypeFilter.All ||
            date != MediaDateFilter.All ||
            largeFilesOnly
}

fun filterAndSortImages(
    images: List<MediaImage>,
    query: String,
    sortOrder: MediaSortOrder,
    filters: MediaFilterState,
    nowMillis: Long = System.currentTimeMillis(),
): List<MediaImage> {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val minimumDate = when (filters.date) {
        MediaDateFilter.All -> Long.MIN_VALUE
        MediaDateFilter.Today -> LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        MediaDateFilter.LastSevenDays -> nowMillis - 7L * 24L * 60L * 60L * 1_000L
        MediaDateFilter.LastThirtyDays -> nowMillis - 30L * 24L * 60L * 60L * 1_000L
    }

    val filtered = images.asSequence()
        .filter { image ->
            normalizedQuery.isEmpty() ||
                image.displayName.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                image.bucketName.lowercase(Locale.getDefault()).contains(normalizedQuery)
        }
        .filter { image -> image.dateTakenMillis >= minimumDate }
        .filter { image -> !filters.largeFilesOnly || image.sizeBytes >= LARGE_FILE_BYTES }
        .filter { image -> matchesType(image, filters.type) }
        .toList()

    return when (sortOrder) {
        MediaSortOrder.Newest -> filtered.sortedWith(
            compareByDescending<MediaImage> { it.dateTakenMillis }
                .thenByDescending { it.id },
        )
        MediaSortOrder.Oldest -> filtered.sortedWith(
            compareBy<MediaImage> { it.dateTakenMillis }
                .thenBy { it.id },
        )
        MediaSortOrder.Name -> filtered.sortedWith(
            compareBy<MediaImage> { it.displayName.lowercase(Locale.getDefault()) }
                .thenByDescending { it.dateTakenMillis },
        )
        MediaSortOrder.Size -> filtered.sortedWith(
            compareByDescending<MediaImage> { it.sizeBytes }
                .thenByDescending { it.dateTakenMillis },
        )
    }
}

private fun matchesType(image: MediaImage, filter: MediaTypeFilter): Boolean {
    if (filter == MediaTypeFilter.All) return true

    val mime = image.mimeType.lowercase(Locale.ROOT)
    val name = image.displayName.lowercase(Locale.ROOT)
    val isVideo = image.isVideo || mime.startsWith("video/")
    val isJpeg = !isVideo && (mime == "image/jpeg" || name.endsWith(".jpg") || name.endsWith(".jpeg"))
    val isPng = !isVideo && (mime == "image/png" || name.endsWith(".png"))
    val isWebp = !isVideo && (mime == "image/webp" || name.endsWith(".webp"))

    return when (filter) {
        MediaTypeFilter.All -> true
        MediaTypeFilter.Photos -> !isVideo
        MediaTypeFilter.Videos -> isVideo
        MediaTypeFilter.Jpeg -> isJpeg
        MediaTypeFilter.Png -> isPng
        MediaTypeFilter.Webp -> isWebp
        MediaTypeFilter.Other -> !isVideo && !isJpeg && !isPng && !isWebp
    }
}

private const val LARGE_FILE_BYTES = 10L * 1024L * 1024L
