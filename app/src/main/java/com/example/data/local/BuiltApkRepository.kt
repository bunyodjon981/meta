package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.io.File

class BuiltApkRepository(private val dao: BuiltApkDao) {
    val allBuiltApks: Flow<List<BuiltApkEntity>> = dao.getAllBuiltApks()

    suspend fun insert(apk: BuiltApkEntity): Long = dao.insertBuiltApk(apk)

    suspend fun getById(id: Long): BuiltApkEntity? = dao.getBuiltApkById(id)

    suspend fun updateDownloadsUri(id: Long, uri: String) = dao.updateDownloadsUri(id, uri)

    suspend fun delete(apk: BuiltApkEntity) {
        runCatching {
            File(apk.localFilePath).takeIf { it.exists() }?.delete()
            apk.iconPath?.let { path -> File(path).takeIf { it.exists() }?.delete() }
        }
        dao.deleteBuiltApkById(apk.id)
    }
}
