package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class ApkBuildForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "APK yigʻilmoqda..."
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val indeterminate = intent?.getBooleanExtra(EXTRA_INDETERMINATE, true) ?: true

                val notification = BuildNotificationHelper.buildProgressNotification(
                    context = this,
                    statusText = statusText,
                    progressPercent = progress,
                    indeterminate = indeterminate
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        BuildNotificationHelper.NOTIFICATION_ID_PROGRESS,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(BuildNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_BUILD_SERVICE"
        private const val ACTION_STOP = "com.example.action.STOP_BUILD_SERVICE"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_INDETERMINATE = "extra_indeterminate"

        fun startOrUpdate(
            context: Context,
            statusText: String,
            progressPercent: Int,
            indeterminate: Boolean
        ) {
            if (!BuildNotificationHelper.hasNotificationPermission(context)) return
            val intent = Intent(context, ApkBuildForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_STATUS_TEXT, statusText)
                putExtra(EXTRA_PROGRESS, progressPercent)
                putExtra(EXTRA_INDETERMINATE, indeterminate)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ApkBuildForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
