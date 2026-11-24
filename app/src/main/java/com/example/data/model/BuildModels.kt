package com.example.data.model

enum class BuildPhase(val stepIndex: Int, val totalSteps: Int) {
    IDLE(0, 7),
    INSPECTING_ZIP(1, 7),
    CONNECTING_GITHUB(2, 7),
    CREATING_REPO(3, 7),
    UPLOADING_FILES(4, 7),
    TRIGGERING_BUILD(5, 7),
    BUILDING_ON_GITHUB(6, 7),
    DOWNLOADING_ARTIFACT(7, 7),
    EXTRACTING_APK(7, 7),
    READY(7, 7),
    FAILED(0, 7);

    val isRunning: Boolean
        get() = this != IDLE && this != READY && this != FAILED
}

enum class LogLevel(val tag: String) {
    INFO("INFO"),
    GIT("GIT"),
    BUILD("BUILD"),
    SUCCESS("OK"),
    WARN("WARN"),
    ERROR("ERR")
}

data class TerminalLogLine(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class ZipFileEntry(
    val relativePath: String,
    val bytes: ByteArray
)

data class ZipProjectSummary(
    val sourceFileName: String,
    val sourceUriString: String,
    val totalSizeBytes: Long,
    val detectedAppName: String,
    val detectedPackageName: String,
    val gradleVersion: String?,
    val hasWrapperJar: Boolean,
    val hasExistingWorkflow: Boolean,
    val fileCount: Int,
    val strippedRootFolder: String?,
    val extractedIconBytes: ByteArray?,
    val entries: List<ZipFileEntry>
)

data class BuildSessionState(
    val phase: BuildPhase = BuildPhase.IDLE,
    val animatedStatusTitle: String = "Tayyor — ZIP fayl tanlang",
    val animatedStatusDetail: String = "GitHub ma'lumotlaringizni kiriting va loyiha ZIP faylini yuklang.",
    val progress: Float = 0f,
    val uploadDone: Int = 0,
    val uploadTotal: Int = 0,
    val activeWorkflowUrl: String? = null,
    val activeRunId: Long? = null,
    val latestReadyApkId: Long? = null,
    val latestReadyApkName: String? = null,
    val latestReadyApkPath: String? = null,
    val latestReadyDownloadsUri: String? = null,
    val latestReadySizeBytes: Long = 0L,
    val errorMessage: String? = null
)
