package com.dlrgallery.app.data

enum class AppThemeMode(val label: String) {
    System("Как в системе"),
    Light("Светлая"),
    Dark("Тёмная"),
}

enum class GalleryGridSize(
    val label: String,
    val columns: Int,
) {
    Large("Крупная", 2),
    Normal("Обычная", 3),
    Compact("Компактная", 4),
    VeryCompact("Очень компактная", 5),
}

enum class ExportQuality(
    val label: String,
    val jpegQuality: Int,
) {
    Economy("Экономное", 80),
    High("Высокое", 92),
    Maximum("Максимальное", 97),
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val gridSize: GalleryGridSize = GalleryGridSize.Normal,
    val exportQuality: ExportQuality = ExportQuality.High,
    val saveAsCopy: Boolean = true,
    val mediaSortOrder: MediaSortOrder = MediaSortOrder.Newest,
)
