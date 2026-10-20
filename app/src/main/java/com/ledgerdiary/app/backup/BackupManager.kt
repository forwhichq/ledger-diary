package com.ledgerdiary.app.backup

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.ledgerdiary.app.data.*
import com.ledgerdiary.app.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份包结构：
 *   data.json         —— 全部文字数据
 *   photos/xxx.jpg    —— 日记照片原文件
 *   avatar.jpg        —— 头像
 *   meta.json         —— 是否加密等元信息
 *
 * 加密方式：AES-256/GCM，密钥由 PBKDF2(密码, salt, 100000 次) 派生。
 * 注意：加密只针对 data.json 和照片内容，不加密文件名结构。
 */
class BackupManager(private val context: Context, private val repo: Repository) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class ExportResult(val file: File, val encrypted: Boolean, val bytes: Long)
    data class ImportResult(val transactions: Int, val diaryEntries: Int, val photos: Int, val overwritten: Boolean)
    data class BackupInfo(val exportedAt: Long, val deviceLabel: String, val transactions: Int, val diaryEntries: Int, val photos: Int)

    suspend fun exportZip(password: String? = null): ExportResult = withContext(Dispatchers.IO) {
        val tx = repo.listAllTransactions()
        val diaryDao = AppDatabase.get(context).diaryDao()
        val catDao = AppDatabase.get(context).categoryDao()
        val entries = diaryDao.allEntries()
        val segments = diaryDao.allSegments()
        val photos = diaryDao.allPhotos()
        val profile = repo.getProfile()

        val backup = BackupFile(
            appVersion = com.ledgerdiary.app.BuildConfig.VERSION_NAME,
            deviceId = profile?.deviceId ?: "",
            profile = profile?.let {
                ProfileDto(it.nickname, it.avatarFile, it.deviceId, it.deviceLabel)
            },
            transactions = tx.map {
                TransactionDto(it.id, it.direction, it.amountCents, it.categoryName, it.note, it.date, it.createdAt, it.updatedAt, it.accountName)
            },
            categories = catDao.listAll().map {
                CategoryDto(it.id, it.name, it.icon, it.colorHex, it.sortOrder, it.usageCount)
            },
            diaryEntries = entries.map {
                DiaryEntryDto(
                    id = it.id,
                    date = it.date,
                    mood = it.mood,
                    tags = it.tags,
                    weather = it.weather,
                    location = it.location,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            diarySegments = segments.map {
                DiarySegmentDto(it.id, it.entryId, it.text, it.createdAt, it.updatedAt, it.sortOrder)
            },
            diaryPhotos = photos.map {
                DiaryPhotoDto(it.id, it.entryId, it.fileName, it.width, it.height, it.createdAt)
            }
        )

        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val out = File(dir, "记账日记-备份-$stamp.zip")

        ZipOutputStream(FileOutputStream(out).buffered()).use { zos ->
            val meta = mutableMapOf<String, Any>(
                "format" to BackupFile.FORMAT,
                "version" to 1,
                "exportedAt" to backup.exportedAt,
                "encrypted" to (password != null)
            )
            val payload: ByteArray = if (password != null) {
                val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                meta["salt"] = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
                val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                meta["iv"] = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"),
                    javax.crypto.spec.GCMParameterSpec(128, iv))
                cipher.doFinal(gson.toJson(backup).toByteArray(Charsets.UTF_8))
            } else {
                gson.toJson(backup).toByteArray(Charsets.UTF_8)
            }

            zos.putNextEntry(ZipEntry("meta.json"))
            zos.write(gson.toJson(meta).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(payload)
            zos.closeEntry()

            // 照片
            photos.forEach { p ->
                val f = PhotoStore.fileOf(context, p.fileName)
                if (f.exists()) {
                    zos.putNextEntry(ZipEntry("photos/${p.fileName}"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            profile?.avatarFile?.let { name ->
                val f = File(context.filesDir, name)
                if (f.exists()) {
                    zos.putNextEntry(ZipEntry("avatar.jpg"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        ExportResult(out, password != null, out.length())
    }

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val tx = repo.listAllTransactions()
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val out = File(dir, "记账日记-账单-$stamp.csv")
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")  // BOM，让 Excel 正确识别 UTF-8 中文
            w.write("日期,时间,收支,金额,分类,备注\n")
            tx.forEach { t ->
                val dirCn = if (t.direction == "INCOME") "收入" else "支出"
                w.write("${t.date},${com.ledgerdiary.app.util.DateUtils.timeOf(t.createdAt)},$dirCn,${com.ledgerdiary.app.util.Money.centsToString(t.amountCents)},${escape(t.categoryName)},${escape(t.note)}\n")
            }
        }
        out
    }

    private fun escape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** 读备份包，不解密也能看基本信息（给导入前预览用） */
    suspend fun peek(uri: Uri, password: String?): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            var metaJson: String? = null
            // 注意：data.json 可能是 AES/GCM 密文，必须先按「字节」保存。
            // 早期版本把它 toString(Charsets.UTF_8) 再 toByteArray() 转回去，
            // 非法 UTF-8 序列会被替换成 U+FFFD，密文被破坏，导致密码正确也报「密码不对」。
            var dataBytes: ByteArray? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var e: ZipEntry? = zis.nextEntry
                    while (e != null) {
                        if (e.name == "meta.json") metaJson = zis.readBytes().toString(Charsets.UTF_8)
                        if (e.name == "data.json") dataBytes = zis.readBytes()
                        e = zis.nextEntry
                    }
                }
            }
            if (dataBytes == null || metaJson == null) return@withContext null
            val meta = gson.fromJson(metaJson, Map::class.java) as Map<*, *>
            val encrypted = meta["encrypted"] as? Boolean ?: false
            val text: String = if (encrypted) {
                if (password == null) return@withContext null
                val salt = android.util.Base64.decode(meta["salt"] as String, android.util.Base64.NO_WRAP)
                val iv = android.util.Base64.decode(meta["iv"] as String, android.util.Base64.NO_WRAP)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                    javax.crypto.spec.GCMParameterSpec(128, iv))
                String(cipher.doFinal(dataBytes!!), Charsets.UTF_8)
            } else String(dataBytes!!, Charsets.UTF_8)
            val b = gson.fromJson(text, BackupFile::class.java)
            BackupInfo(b.exportedAt, b.profile?.deviceLabel ?: "", b.transactions.size, b.diaryEntries.size, b.diaryPhotos.size)
        } catch (e: Exception) { null }
    }

    suspend fun importZip(uri: Uri, password: String?, overwrite: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            var metaJson: String? = null
            // 同 peek：密文必须按字节读，不能先转成 String。
            var dataBytes: ByteArray? = null
            val photoBytes = mutableMapOf<String, ByteArray>()
            var avatarBytes: ByteArray? = null

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var e: ZipEntry? = zis.nextEntry
                    while (e != null) {
                        when {
                            e.name == "meta.json" -> metaJson = zis.readBytes().toString(Charsets.UTF_8)
                            e.name == "data.json" -> dataBytes = zis.readBytes()
                            e.name == "avatar.jpg" -> avatarBytes = zis.readBytes()
                            e.name.startsWith("photos/") ->
                                photoBytes[e.name.removePrefix("photos/")] = zis.readBytes()
                        }
                        e = zis.nextEntry
                    }
                }
            }
            require(dataBytes != null && metaJson != null) { "备份包格式不正确" }
            val meta = gson.fromJson(metaJson, Map::class.java) as Map<*, *>
            val encrypted = meta["encrypted"] as? Boolean ?: false
            val text: String = if (encrypted) {
                require(password != null) { "这个备份设置了密码" }
                val salt = android.util.Base64.decode(meta["salt"] as String, android.util.Base64.NO_WRAP)
                val iv = android.util.Base64.decode(meta["iv"] as String, android.util.Base64.NO_WRAP)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                    javax.crypto.spec.GCMParameterSpec(128, iv))
                String(cipher.doFinal(dataBytes!!), Charsets.UTF_8)
            } else String(dataBytes!!, Charsets.UTF_8)

            val b = gson.fromJson(text, BackupFile::class.java)
            val db = AppDatabase.get(context)

            if (overwrite) {
                // 覆盖前先把当前数据另存一份，选错了还能退回来
                runCatching { exportZip(null) }
                db.transactionDao().clear()
                db.categoryDao().clear()
                db.diaryDao().clearAll()
                PhotoStore.photosDir(context).listFiles()?.forEach { it.delete() }
            }

            db.transactionDao().upsertAll(b.transactions.map {
                TransactionEntity(it.id, it.direction, it.amountCents, it.categoryName, it.note,
                    it.date, it.createdAt, it.updatedAt, it.accountName)
            })
            db.categoryDao().upsertAll(b.categories.map {
                CategoryEntity(it.id, it.name, it.icon, it.colorHex, it.sortOrder, it.usageCount)
            })
            // 用命名参数：以后 DiaryEntryEntity 加字段时，位置参数会静默错位，命名参数会编译报错
            db.diaryDao().upsertEntries(b.diaryEntries.map {
                DiaryEntryEntity(
                    id = it.id,
                    date = it.date,
                    mood = it.mood,
                    tags = it.tags,
                    weather = it.weather,
                    location = it.location,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            })
            db.diaryDao().upsertSegments(b.diarySegments.map {
                DiarySegmentEntity(it.id, it.entryId, it.text, it.createdAt, it.updatedAt, it.sortOrder)
            })

            photoBytes.forEach { (name, bytes) ->
                File(PhotoStore.photosDir(context), name).writeBytes(bytes)
            }
            db.diaryDao().upsertPhotos(b.diaryPhotos.map {
                DiaryPhotoEntity(
                    id = it.id,
                    entryId = it.entryId,
                    fileName = it.fileName,
                    width = it.width,
                    height = it.height,
                    createdAt = it.createdAt
                )
            })

            avatarBytes?.let { File(context.filesDir, "avatar.jpg").writeBytes(it) }
            b.profile?.let {
                db.profileDao().upsert(ProfileEntity(
                    nickname = it.nickname,
                    avatarFile = if (avatarBytes != null) "avatar.jpg" else it.avatarFile,
                    deviceId = it.deviceId,
                    deviceLabel = it.deviceLabel
                ))
            }

            ImportResult(b.transactions.size, b.diaryEntries.size, b.diaryPhotos.size, overwrite)
        }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
