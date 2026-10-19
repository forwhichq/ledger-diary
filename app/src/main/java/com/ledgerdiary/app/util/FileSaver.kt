package com.ledgerdiary.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * 把 App 里生成的文件交给系统「下载」目录或别的 App。
 *
 * 为什么不用 ActivityResultContracts.CreateDocument 当主路径：拉起系统文件选择器会让本 App
 * 退到后台，回调时 Composable 的 rememberCoroutineScope 可能已经被取消，写入协程还没跑就死了，
 * 结果系统建出一个 0 字节的空文件（2026-10-19 实测到过）。所以主路径改成直接写 MediaStore，
 * 不需要权限、不需要离开 App、还能在写完后核对字节数。
 */
object FileSaver {

    /** 保存结果。displayPath 是给人看的路径，bytes 是实际写入的字节数。 */
    data class Saved(val displayPath: String, val uri: Uri, val bytes: Long)

    private const val BUF = 64 * 1024

    /**
     * 直接写进系统「下载」目录。
     * Android 10+ 走 MediaStore（无需任何权限）；更低版本退到 App 自己的外部目录
     * （/sdcard/Android/data/<包名>/files/Download），同样不需要权限，只是路径深一点。
     */
    fun saveToDownloads(ctx: Context, file: File, mime: String, subDir: String = "记账日记"): Saved {
        if (!file.isFile) throw IllegalStateException("要保存的文件不存在：" + file.name)
        val total = file.length()
        if (total <= 0L) throw IllegalStateException("导出的文件是空的，先别保存")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + subDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("系统拒绝了写入「下载」目录")
            try {
                val written = resolver.openOutputStream(uri, "wt")?.use { out ->
                    file.inputStream().use { it.copyTo(out, BUF) }
                } ?: throw IllegalStateException("系统没有返回可写的文件流")
                if (written != total) throw IllegalStateException("写入不完整（" + written + "/" + total + " 字节）")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return Saved(Environment.DIRECTORY_DOWNLOADS + "/" + subDir + "/" + file.name, uri, written)
            } catch (t: Throwable) {
                // 写失败就把半成品删掉，别在「下载」里留一个坏文件
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }

        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), subDir)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("建不了目录：" + dir.absolutePath)
        val out = File(dir, file.name)
        val written = file.inputStream().use { ins -> out.outputStream().use { ins.copyTo(it, BUF) } }
        if (written != total) throw IllegalStateException("写入不完整（" + written + "/" + total + " 字节）")
        return Saved(out.absolutePath, Uri.fromFile(out), written)
    }

    /**
     * 把文件写到用户在选择器里挑的位置。同步执行（导出文件只有几百 KB），
     * 这样即使 Composable 已经被重组/销毁，写入也不会被协程取消掉。
     * 写完再打开读一遍核对字节数，避免又出现「提示成功、文件却是 0 字节」。
     */
    fun saveToUri(ctx: Context, file: File, uri: Uri): Long {
        val total = file.length()
        val resolver = ctx.contentResolver
        val written = resolver.openOutputStream(uri, "wt")?.use { out ->
            file.inputStream().use { it.copyTo(out, BUF) }
        } ?: throw IllegalStateException("系统没有返回可写的文件流")
        if (written != total) throw IllegalStateException("写入不完整（" + written + "/" + total + " 字节）")

        // 复核：从刚写的 Uri 读回来数一遍字节
        var readBack = 0L
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(BUF)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                readBack += n
            }
        } ?: throw IllegalStateException("写完读不回来，可能没保存成功")
        if (readBack != total) throw IllegalStateException("保存后校验失败（" + readBack + "/" + total + " 字节）")
        return written
    }

    /** 调起系统分享面板，把文件发给微信/网盘/邮件等。 */
    fun share(ctx: Context, file: File, mime: String) {
        if (!file.isFile) throw IllegalStateException("要分享的文件不存在")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享 " + file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(chooser)
    }

    /** 给 snackbar 用的人话大小，例如 119 KB。 */
    fun sizeText(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.CHINA, bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> (bytes / 1024).toString() + " KB"
        else -> bytes.toString() + " B"
    }
}
