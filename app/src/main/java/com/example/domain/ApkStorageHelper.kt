package com.example.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ExtractedApkResult(
    val fileName: String,
    val localFile: File,
    val downloadsUri: String?,
    val sizeBytes: Long,
    val wasExtractedFromZip: Boolean
)

object ApkStorageHelper {

    suspend fun processDownloadedPayloadAndSave(
        context: Context,
        rawBytes: ByteArray,
        preferredBaseName: String,
        onLog: (String) -> Unit
    ): ExtractedApkResult = withContext(Dispatchers.IO) {
        val safeBase = preferredBaseName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "app" }

        val apksDir = File(context.filesDir, "apks").apply { mkdirs() }
        var wasZip = false
        val apkBytesPair: Pair<String, ByteArray> = if (isZipHeader(rawBytes)) {
            wasZip = true
            onLog("Yuklangan fayl ZIP arxivi ekanligi aniqlandi — APK ichidan ajratib olinmoqda...")
            extractFirstApkFromZipBytes(rawBytes, safeBase)
                ?: throw IllegalStateException("Arxiv ichidan .apk fayli topilmadi.")
        } else {
            "${safeBase}-debug.apk" to rawBytes
        }

        val finalName = if (apkBytesPair.first.endsWith(".apk", ignoreCase = true)) {
            apkBytesPair.first
        } else {
            "${safeBase}-debug.apk"
        }
        val uniqueDiskName = "${System.currentTimeMillis()}_$finalName"
        val localFile = File(apksDir, uniqueDiskName)
        localFile.writeBytes(apkBytesPair.second)

        val downloadsUri = saveToPublicDownloads(context, finalName, apkBytesPair.second)

        ExtractedApkResult(
            fileName = finalName,
            localFile = localFile,
            downloadsUri = downloadsUri,
            sizeBytes = localFile.length(),
            wasExtractedFromZip = wasZip
        )
    }

    suspend fun saveIconBytes(
        context: Context,
        iconBytes: ByteArray?,
        prefix: String
    ): String? = withContext(Dispatchers.IO) {
        if (iconBytes == null || iconBytes.isEmpty()) return@withContext null
        val iconsDir = File(context.filesDir, "icons").apply { mkdirs() }
        val safePrefix = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "icon" }
        val iconFile = File(iconsDir, "${safePrefix}_${System.currentTimeMillis()}.png")
        iconFile.writeBytes(iconBytes)
        iconFile.absolutePath
    }

    suspend fun saveToPublicDownloads(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/APKForge")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun copyLocalApkToUri(
        context: Context,
        localFilePath: String,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) return@runCatching false
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            } != null
        }.getOrDefault(false)
    }

    fun buildShareApkIntent(context: Context, localFilePath: String, displayName: String): Intent? {
        val file = File(localFilePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildOpenApkIntent(context: Context, localFilePath: String): Intent? {
        val file = File(localFilePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isZipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
    }

    internal fun extractFirstApkFromZipBytes(
        zipBytes: ByteArray,
        fallbackBase: String,
        depth: Int = 0
    ): Pair<String, ByteArray>? {
        if (depth > 3) return null
        var bestEntry: Pair<String, ByteArray>? = null
        val nestedZips = mutableListOf<ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && entryName.isNotBlank()) {
                    val data = readAllBytes(zis)
                    if (entryName.endsWith(".apk", ignoreCase = true)) {
                        val cleanName = if (entryName.equals("app-debug.apk", ignoreCase = true)) {
                            "${fallbackBase}-debug.apk"
                        } else {
                            entryName
                        }
                        if (bestEntry == null || entryName.contains("debug", ignoreCase = true)) {
                            bestEntry = cleanName to data
                        }
                    } else if (entryName.endsWith(".zip", ignoreCase = true) || isZipHeader(data)) {
                        nestedZips.add(data)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (bestEntry != null) return bestEntry
        for (nested in nestedZips) {
            val found = extractFirstApkFromZipBytes(nested, fallbackBase, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var read = input.read(buf)
        while (read != -1) {
            out.write(buf, 0, read)
            read = input.read(buf)
        }
        return out.toByteArray()
    }
}
