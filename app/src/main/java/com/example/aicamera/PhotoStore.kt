package com.example.aicamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

object PhotoStore {

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun newRawFile(ctx: Context): File =
        File(dir(ctx), "IMG_${System.currentTimeMillis()}.jpg")

    /** IMG_123.jpg -> IMG_123_ai.jpg */
    fun aiFileFor(raw: File): File =
        File(raw.parentFile, raw.nameWithoutExtension + "_ai.jpg")

    /** Что показывать: обработанную версию, если она есть, иначе оригинал */
    fun displayFile(raw: File): File = aiFileFor(raw).takeIf { it.exists() } ?: raw

    fun isPending(raw: File): Boolean = !aiFileFor(raw).exists()

    /** Только оригиналы (без _ai), новые сверху */
    fun listRaw(ctx: Context): List<File> =
        dir(ctx)
            .listFiles { f -> f.name.matches(Regex("IMG_\\d+\\.jpg")) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    /** Копия результата в системную галерею: Pictures/AI Camera */
    fun saveToGallery(ctx: Context, file: File) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    }

    /**
     * Декодирует JPEG с учётом EXIF-поворота и уменьшает до maxSide.
     * Без этого фото с камеры могут отображаться боком.
     */
    fun decodeUpright(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val orientation = ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postRotate(angle)
            postScale(scale, scale)
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}
