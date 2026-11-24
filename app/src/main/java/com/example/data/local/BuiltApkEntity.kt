package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "built_apks")
data class BuiltApkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val repoName: String,
    val ownerUsername: String,
    val packageName: String,
    val fileName: String,
    val localFilePath: String,
    val downloadsUri: String?,
    val iconPath: String?,
    val fileSizeBytes: Long,
    val runId: Long,
    val repoHtmlUrl: String,
    val buildDurationSec: Int,
    val createdAt: Long = System.currentTimeMillis()
)
