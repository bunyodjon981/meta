package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ApkForgeApplication
import com.example.data.local.BuiltApkEntity
import com.example.data.model.BuildSessionState
import com.example.data.model.LogLevel
import com.example.data.model.TerminalLogLine
import com.example.data.model.ZipProjectSummary
import com.example.domain.ApkStorageHelper
import com.example.domain.ZipProjectInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CreateApkUiState(
    val githubUsername: String = "",
    val githubToken: String = "",
    val repoName: String = "",
    val isPrivateRepo: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val selectedZipUri: Uri? = null,
    val zipSummary: ZipProjectSummary? = null,
    val isInspectingZip: Boolean = false,
    val zipInspectError: String? = null
)

class ApkForgeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ApkForgeApplication
    private val apkRepository = app.apkRepository
    private val prefsRepository = app.preferencesRepository
    private val coordinator = app.buildCoordinator

    private val _uiState = MutableStateFlow(CreateApkUiState())
    val uiState: StateFlow<CreateApkUiState> = _uiState.asStateFlow()

    val buildSessionState: StateFlow<BuildSessionState> = coordinator.sessionState
    val terminalLogs: StateFlow<List<TerminalLogLine>> = coordinator.logs

    val builtApks: StateFlow<List<BuiltApkEntity>> = apkRepository.allBuiltApks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = prefsRepository.settingsFlow.first()
            _uiState.update {
                it.copy(
                    githubUsername = initial.githubUsername,
                    githubToken = initial.githubToken,
                    repoName = initial.lastRepoName,
                    isPrivateRepo = initial.isPrivateRepo,
                    notificationsEnabled = initial.notificationsEnabled
                )
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(githubUsername = value) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(githubToken = value) }
    }

    fun onRepoNameChange(value: String) {
        _uiState.update { it.copy(repoName = value) }
    }

    fun onPrivateRepoChange(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivateRepo = isPrivate) }
    }

    fun onNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch {
            prefsRepository.setNotificationsEnabled(enabled)
        }
        coordinator.appendLog(
            LogLevel.INFO,
            if (enabled) "Bildirishnomalar yoqildi." else "Bildirishnomalar o'chirildi (fonda jimgina ishlaydi)."
        )
    }

    fun onZipSelected(uri: Uri) {
        _uiState.update {
            it.copy(
                selectedZipUri = uri,
                isInspectingZip = true,
                zipInspectError = null,
                zipSummary = null
            )
        }
        coordinator.appendLog(LogLevel.INFO, "Tanlangan ZIP fayl ochilib tahlil qilinmoqda...")

        viewModelScope.launch {
            try {
                val summary = ZipProjectInspector.inspectZip(
                    context = app,
                    uri = uri,
                    fallbackRepoName = _uiState.value.repoName
                )
                val suggestedRepo = _uiState.value.repoName.ifBlank {
                    summary.detectedAppName
                        .lowercase()
                        .replace(Regex("\\s+"), "-")
                        .replace(Regex("[^a-z0-9-]"), "")
                        .trim('-')
                        .ifBlank { "android-app" }
                }
                _uiState.update {
                    it.copy(
                        isInspectingZip = false,
                        zipSummary = summary,
                        repoName = suggestedRepo,
                        zipInspectError = null
                    )
                }
                coordinator.appendLog(
                    LogLevel.SUCCESS,
                    "ZIP tayyor: '${summary.sourceFileName}' (${summary.fileCount} fayl, Ilova: ${summary.detectedAppName})"
                )
            } catch (e: Exception) {
                val err = e.message ?: "ZIP faylini o'qishda xatolik."
                _uiState.update {
                    it.copy(
                        isInspectingZip = false,
                        zipSummary = null,
                        zipInspectError = err
                    )
                }
                coordinator.appendLog(LogLevel.ERROR, err)
            }
        }
    }

    fun onStartBuild() {
        val state = _uiState.value
        val uri = state.selectedZipUri
        if (uri == null) {
            _toastMessage.value = "Avval loyiha ZIP faylini tanlang."
            coordinator.appendLog(LogLevel.WARN, "ZIP fayl tanlanmagan.")
            return
        }
        coordinator.startBuildPipeline(
            usernameInput = state.githubUsername,
            tokenInput = state.githubToken,
            repoNameInput = state.repoName,
            isPrivateRepo = state.isPrivateRepo,
            zipUri = uri,
            cachedSummary = state.zipSummary
        )
    }

    fun onCancelBuild() {
        coordinator.cancelBuild()
    }

    fun onClearLogs() {
        coordinator.clearLogs()
    }

    fun exportApkToUserUri(localFilePath: String, targetUri: Uri, apkId: Long?) {
        viewModelScope.launch {
            val ok = ApkStorageHelper.copyLocalApkToUri(app, localFilePath, targetUri)
            if (ok) {
                if (apkId != null) {
                    apkRepository.updateDownloadsUri(apkId, targetUri.toString())
                }
                _toastMessage.value = "APK telefoningizga muvaffaqiyatli yuklab olindi!"
                coordinator.appendLog(LogLevel.SUCCESS, "APK foydalanuvchi tanlagan papkaga saqlandi.")
            } else {
                _toastMessage.value = "APK ni saqlashda xatolik yuz berdi."
            }
        }
    }

    fun saveApkToPublicDownloads(apk: BuiltApkEntity) {
        viewModelScope.launch {
            val file = File(apk.localFilePath)
            if (!file.exists()) {
                _toastMessage.value = "APK fayli topilmadi."
                return@launch
            }
            val bytes = file.readBytes()
            val savedUri = ApkStorageHelper.saveToPublicDownloads(app, apk.fileName, bytes)
            if (savedUri != null) {
                apkRepository.updateDownloadsUri(apk.id, savedUri)
                _toastMessage.value = "Download/APKForge/${apk.fileName} ga saqlandi!"
            } else {
                _toastMessage.value = "Fayl tayyor — 'Saqlash' oynasidan joy tanlang."
            }
        }
    }

    fun deleteBuiltApk(apk: BuiltApkEntity) {
        viewModelScope.launch {
            apkRepository.delete(apk)
            _toastMessage.value = "${apk.appName} ro'yxatdan o'chirildi."
        }
    }

    fun showMessage(message: String) {
        _toastMessage.value = message
    }

    fun consumeToast() {
        _toastMessage.value = null
    }
}
