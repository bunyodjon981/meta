package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.BuiltApkRepository
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.GitHubBuildCoordinator
import com.example.service.BuildNotificationHelper

class ApkForgeApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val apkRepository: BuiltApkRepository by lazy { BuiltApkRepository(database.builtApkDao()) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val buildCoordinator: GitHubBuildCoordinator by lazy {
        GitHubBuildCoordinator(
            appContext = this,
            apkRepository = apkRepository,
            preferencesRepository = preferencesRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        BuildNotificationHelper.ensureChannel(this)
    }
}
