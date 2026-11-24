package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuiltApkDao {
    @Query("SELECT * FROM built_apks ORDER BY createdAt DESC")
    fun getAllBuiltApks(): Flow<List<BuiltApkEntity>>

    @Query("SELECT * FROM built_apks WHERE id = :id LIMIT 1")
    suspend fun getBuiltApkById(id: Long): BuiltApkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuiltApk(apk: BuiltApkEntity): Long

    @Query("UPDATE built_apks SET downloadsUri = :downloadsUri WHERE id = :id")
    suspend fun updateDownloadsUri(id: Long, downloadsUri: String)

    @Query("DELETE FROM built_apks WHERE id = :id")
    suspend fun deleteBuiltApkById(id: Long)
}
