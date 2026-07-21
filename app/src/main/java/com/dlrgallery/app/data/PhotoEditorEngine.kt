package com.dlrgallery.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

enum class CropPreset(
    val label: String,
    val aspectRatio: Float?,
) {
    Original("Оригинал", null),
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    SixteenNine("16:9", 16f / 9f),
}

data class PhotoAdjustments(
    val cropPreset: CropPreset = CropPreset.Original,
    val rotationQuarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
)

suspend fun loadEditorPreview(
    context: Context,
    image: MediaImage,
): Bitmap = withContext(Dispatchers.IO) {
    decodeBitmap(context, image.uri, maxSide = 1_600)
}

suspend fun exportEditedCopy(
    context: Context,
    image: MediaImage,
    adjustments: PhotoAdjustments,
): Uri = withContext(Dispatchers.IO) {
    val source = decodeBitmap(context, image.uri, maxSide = 4_096)
    var transformed: Bitmap? = null
    var cropped: Bitmap? = null
    var filtered: Bitmap? = null

    try {
        transformed = rotateAndFlip(source, adjustments)
        cropped = cropToPreset(transformed, adjustments.cropPreset)
        filtered = applyColorAdjustments(cropped, adjustments)
        saveBitmapCopy(context, filtered)
    } finally {
        if (filtered != null && filtered !== cropped && !filtered.isRecycled) filtered.recycle()
        if (cropped != null && cropped !== transformed && !cropped.isRecycled) cropped.recycle()
        if (transformed != null && transformed !== source && !transformed.isRecycled) transformed.recycle()
        if (!source.isRecycled) source.recycle()
    }
}

fun editorColorMatrixValues(adjustments: PhotoAdjustments): FloatArray =
    createColorMatrix(adjustments).array.copyOf()

private fun decodeBitmap(
    context: Context,
    uri: Uri,
    maxSide: Int,
): Bitmap {
    val resolver = context.contentResolver

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val largest = max(width, height)
            if (largest > maxSide) {
                val ratio = maxSide.toFloat() / largest.toFloat()
                decoder.setTargetSize(
                    (width * ratio).roundToInt().coerceAtLeast(1),
                    (height * ratio).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IOException("Не удалось открыть изображение")

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSide) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IOException("Не удалось декодировать изображение")
}

private fun rotateAndFlip(
    source: Bitmap,
    adjustments: PhotoAdjustments,
): Bitmap {
    val rotation = (adjustments.rotationQuarterTurns.mod(4)) * 90f
    if (rotation == 0f && !adjustments.flipHorizontal) return source

    val matrix = Matrix().apply {
        if (adjustments.flipHorizontal) {
            postScale(-1f, 1f)
        }
        if (rotation != 0f) {
            postRotate(rotation)
        }
    }
    return Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        matrix,
        true,
    )
}

private fun cropToPreset(
    source: Bitmap,
    preset: CropPreset,
): Bitmap {
    val targetRatio = preset.aspectRatio ?: return source
    val currentRatio = source.width.toFloat() / source.height.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    if (currentRatio > targetRatio) {
        cropHeight = source.height
        cropWidth = (cropHeight * targetRatio).roundToInt().coerceAtMost(source.width)
    } else {
        cropWidth = source.width
        cropHeight = (cropWidth / targetRatio).roundToInt().coerceAtMost(source.height)
    }

    if (cropWidth == source.width && cropHeight == source.height) return source
    val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
    val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
}

private fun applyColorAdjustments(
    source: Bitmap,
    adjustments: PhotoAdjustments,
): Bitmap {
    if (
        adjustments.brightness == 0f &&
        adjustments.contrast == 1f &&
        adjustments.saturation == 1f &&
        adjustments.warmth == 0f
    ) {
        return source
    }

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(createColorMatrix(adjustments))
    }
    Canvas(output).drawBitmap(source, 0f, 0f, paint)
    return output
}

private fun createColorMatrix(adjustments: PhotoAdjustments): ColorMatrix {
    val result = ColorMatrix().apply {
        setSaturation(adjustments.saturation.coerceIn(0f, 2f))
    }

    val contrast = adjustments.contrast.coerceIn(0.5f, 1.5f)
    val contrastOffset = 128f * (1f - contrast)
    result.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, contrastOffset,
                0f, contrast, 0f, 0f, contrastOffset,
                0f, 0f, contrast, 0f, contrastOffset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    val warmth = adjustments.warmth.coerceIn(-1f, 1f)
    val redScale = 1f + warmth * 0.18f
    val blueScale = 1f - warmth * 0.18f
    result.postConcat(
        ColorMatrix(
            floatArrayOf(
                redScale, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, blueScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    val brightnessOffset = adjustments.brightness.coerceIn(-1f, 1f) * 110f
    result.postConcat(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    return result
}

private fun saveBitmapCopy(
    context: Context,
    bitmap: Bitmap,
): Uri {
    val resolver = context.contentResolver
    val fileName = "DLR_${System.currentTimeMillis()}.jpg"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.WIDTH, bitmap.width)
        put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DLR Gallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "DLR Gallery",
            )
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Не удалось создать папку DLR Gallery")
            }
            put(MediaStore.Images.Media.DATA, File(directory, fileName).absolutePath)
        }
    }

    val uri = resolver.insert(collection, values)
        ?: throw IOException("Android не создал файл для сохранения")

    try {
        resolver.openOutputStream(uri, "w")?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IOException("Не удалось записать JPEG")
            }
        } ?: throw IOException("Не удалось открыть файл для записи")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
        return uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}
