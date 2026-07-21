package com.dlrgallery.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first

suspend fun exportEditedCopy(
    context: Context,
    image: MediaImage,
    adjustments: PhotoAdjustments,
): Uri {
    val jpegQuality = AppSettingsRepository(context)
        .settings
        .first()
        .exportQuality
        .jpegQuality

    return exportEditedCopy(
        context = context,
        image = image,
        adjustments = adjustments,
        jpegQuality = jpegQuality,
    )
}
