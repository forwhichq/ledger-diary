package com.ledgerdiary.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 照片一律压到长边 1600px、JPEG 85，存到 filesDir/photos/。
 * 这样 9 张照片约 3MB，不会把手机撑爆，也不会在备份时变成几百 MB。
 */
object PhotoStore {
    private const val MAX_EDGE = 1600
    private const val QUALITY = 85

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun importFromUri(context: Context, uri: Uri): File? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            // 处理 EXIF 旋转，否则竖拍照片会横着显示
            val rotation = try {
                resolver.openInputStream(uri)?.use { input ->
                    when (ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) { 0f }

            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            bmp = scaleDown(bmp, MAX_EDGE)

            val file = File(photosDir(context), UUID.randomUUID().toString() + ".jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            file
        } catch (e: Exception) {
            null
        }
    }

    fun importAvatar(context: Context, uri: Uri): File? {
        return try {
            val resolver = context.contentResolver
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            bmp = scaleDown(bmp, 512)
            val file = File(context.filesDir, "avatar.jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            file
        } catch (e: Exception) { null }
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    fun fileOf(context: Context, name: String): File = File(photosDir(context), name)

    fun delete(context: Context, name: String) {
        try { fileOf(context, name).delete() } catch (e: Exception) { }
    }
}
