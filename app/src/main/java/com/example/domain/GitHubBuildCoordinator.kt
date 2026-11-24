package com.example.domain

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.local.BuiltApkEntity
import com.example.data.local.BuiltApkRepository
import com.example.data.model.BuildPhase
import com.example.data.model.BuildSessionState
import com.example.data.model.LogLevel
import com.example.data.model.TerminalLogLine
import com.example.data.model.ZipProjectSummary
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CreateBlobRequest
import com.example.data.remote.CreateCommitRequest
import com.example.data.remote.CreateRepoRequest
import com.example.data.remote.CreateTreeRequest
import com.example.data.remote.GitHubApiService
import com.example.data.remote.GitHubClientFactory
import com.example.data.remote.GitTreeEntryInput
import com.example.data.remote.PutFileContentRequest
import com.example.data.remote.UpdateRefRequest
import com.example.data.remote.WorkflowDispatchRequest
import com.example.service.ApkBuildForegroundService
import com.example.service.BuildNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class GitHubBuildCoordinator(
    private val appContext: Context,
    private val apkRepository: BuiltApkRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeBuildJob: Job? = null
    private val logIdCounter = AtomicLong(1L)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _sessionState = MutableStateFlow(BuildSessionState())
    val sessionState: StateFlow<BuildSessionState> = _sessionState.asStateFlow()

    private val _logs = MutableStateFlow<List<TerminalLogLine>>(
        listOf(
            TerminalLogLine(
                id = 0L,
                timestamp = timeFormatter.format(Date()),
                level = LogLevel.INFO,
                message = "APK Forge terminali tayyor. GitHub ma'lumotlari va loyiha ZIP faylini tanlang."
            )
        )
    )
    val logs: StateFlow<List<TerminalLogLine>> = _logs.asStateFlow()

    private var notificationsEnabledCache: Boolean = true

    init {
        scope.launch {
            preferencesRepository.settingsFlow.collect { settings ->
                notificationsEnabledCache = settings.notificationsEnabled
                if (!settings.notificationsEnabled) {
                    ApkBuildForegroundService.stop(appContext)
                    BuildNotificationHelper.cancelAll(appContext)
                } else if (_sessionState.value.phase.isRunning) {
                    val state = _sessionState.value
                    ApkBuildForegroundService.startOrUpdate(
                        context = appContext,
                        statusText = state.animatedStatusTitle,
                        progressPercent = (state.progress * 100).toInt(),
                        indeterminate = state.progress <= 0.02f
                    )
                }
            }
        }
    }

    fun clearLogs() {
        _logs.value = listOf(
            TerminalLogLine(
                id = logIdCounter.getAndIncrement(),
                timestamp = timeFormatter.format(Date()),
                level = LogLevel.INFO,
                message = "Terminal tozalandi."
            )
        )
    }

    fun resetReadyBanner() {
        _sessionState.update {
            if (!it.phase.isRunning) BuildSessionState() else it
        }
    }

    fun cancelBuild() {
        activeBuildJob?.cancel()
        activeBuildJob = null
        ApkBuildForegroundService.stop(appContext)
        BuildNotificationHelper.cancelAll(appContext)
        appendLog(LogLevel.WARN, "Jarayon foydalanuvchi tomonidan to'xtatildi.")
        _sessionState.update {
            it.copy(
                phase = BuildPhase.IDLE,
                animatedStatusTitle = "Jarayon bekor qilindi",
                animatedStatusDetail = "Qaytadan boshlash uchun tugmani bosing.",
                progress = 0f
            )
        }
    }

    fun startBuildPipeline(
        usernameInput: String,
        tokenInput: String,
        repoNameInput: String,
        isPrivateRepo: Boolean,
        zipUri: Uri,
        cachedSummary: ZipProjectSummary?
    ) {
        if (_sessionState.value.phase.isRunning) return

        val cleanUser = usernameInput.trim()
        val cleanToken = tokenInput.trim()
        val cleanRepo = sanitizeRepoName(repoNameInput.trim())

        if (cleanUser.isBlank() || cleanToken.isBlank() || cleanRepo.isBlank()) {
            appendLog(LogLevel.ERROR, "GitHub Username, Token va Repository nomini to'liq kiriting.")
            _sessionState.update {
                it.copy(
                    phase = BuildPhase.FAILED,
                    animatedStatusTitle = "Ma'lumotlar to'liq emas",
                    animatedStatusDetail = "Username, Token va Repository nomini kiriting.",
                    errorMessage = "Maydonlar to'liq to'ldirilmagan."
                )
            }
            return
        }

        activeBuildJob = scope.launch {
            val startedAtMs = System.currentTimeMillis()
            val notificationsEnabled = preferencesRepository.settingsFlow.first().notificationsEnabled
            notificationsEnabledCache = notificationsEnabled

            if (notificationsEnabled) {
                ApkBuildForegroundService.startOrUpdate(
                    context = appContext,
                    statusText = "Loyiha ZIP fayli ochilmoqda...",
                    progressPercent = 5,
                    indeterminate = true
                )
            }

            try {
                preferencesRepository.saveCredentials(cleanUser, cleanToken, cleanRepo, isPrivateRepo)

                // STEP 1: Inspect & unpack ZIP
                updatePhase(
                    phase = BuildPhase.INSPECTING_ZIP,
                    title = "ZIP fayl ochilmoqda...",
                    detail = "Loyiha tuzilmasi, Gradle va ilova nomi tahlil qilinmoqda.",
                    progress = 0.08f
                )
                appendLog(LogLevel.INFO, "ZIP arxivi ochilmoqda va tekshirilmoqda...")

                val summary = cachedSummary ?: ZipProjectInspector.inspectZip(
                    context = appContext,
                    uri = zipUri,
                    fallbackRepoName = cleanRepo
                )

                appendLog(
                    LogLevel.SUCCESS,
                    "ZIP ochildi: ${summary.fileCount} ta fayl | Ilova: '${summary.detectedAppName}' (${summary.detectedPackageName})"
                )
                if (summary.strippedRootFolder != null) {
                    appendLog(LogLevel.INFO, "Asosiy ichki papka normallashtirildi: '${summary.strippedRootFolder}/' -> root")
                }
                if (!summary.hasExistingWorkflow) {
                    appendLog(
                        LogLevel.BUILD,
                        "GitHub Actions APK workflow topilmadi -> .github/workflows/android-apk.yml avtomatik qo'shildi (Gradle ${summary.gradleVersion ?: "8.11.1"})."
                    )
                } else {
                    appendLog(LogLevel.INFO, "Mavjud .github/workflows/ fayli aniqlandi.")
                }

                // STEP 2: Connect to GitHub
                updatePhase(
                    phase = BuildPhase.CONNECTING_GITHUB,
                    title = "GitHub'ga ulanilmoqda...",
                    detail = "@$cleanUser hisobi va token tekshirilmoqda.",
                    progress = 0.16f
                )
                appendLog(LogLevel.GIT, "GitHub API bilan ulanish o'rnatilmoqda...")
                val api = GitHubClientFactory.createService(cleanToken)
                val userResp = api.getAuthenticatedUser()
                if (!userResp.isSuccessful || userResp.body() == null) {
                    val code = userResp.code()
                    val err = userResp.errorBody()?.string().orEmpty()
                    throw IllegalStateException("GitHub autentifikatsiya xatosi ($code). Tokenni tekshiring. $err")
                }
                val authedUser = userResp.body()!!
                val owner = authedUser.login
                if (!owner.equals(cleanUser, ignoreCase = true)) {
                    appendLog(
                        LogLevel.WARN,
                        "Kiritilgan username '$cleanUser', ammo token '@$owner' hisobiga tegishli. '@$owner' ishlatiladi."
                    )
                } else {
                    appendLog(LogLevel.SUCCESS, "GitHub'ga muvaffaqiyatli ulandi: @$owner")
                }

                // STEP 3: Create or open repository
                updatePhase(
                    phase = BuildPhase.CREATING_REPO,
                    title = "Repository yaratilmoqda...",
                    detail = "$owner/$cleanRepo tayyorlanmoqda.",
                    progress = 0.24f
                )
                appendLog(LogLevel.GIT, "Repository tekshirilmoqda: $owner/$cleanRepo")
                val repoInfo = ensureRepository(api, owner, cleanRepo, isPrivateRepo)
                val defaultBranch = repoInfo.defaultBranch.ifBlank { "main" }
                appendLog(LogLevel.SUCCESS, "Repository tayyor: ${repoInfo.htmlUrl} (branch: $defaultBranch)")

                // Wait for initial commit ref (or seed README.md if empty)
                val headCommitSha = ensureInitialHeadCommit(api, owner, cleanRepo, defaultBranch)
                val headCommitResp = api.getGitCommit(owner, cleanRepo, headCommitSha)
                val baseTreeSha = headCommitResp.body()?.tree?.sha

                // STEP 4: Upload all files in parallel batches
                val totalFiles = summary.entries.size
                updatePhase(
                    phase = BuildPhase.UPLOADING_FILES,
                    title = "Fayllar GitHub'ga yuklanmoqda (0/$totalFiles)",
                    detail = "Loyiha fayllari Git blob sifatida jo'natilmoqda...",
                    progress = 0.28f
                )
                appendLog(LogLevel.GIT, "Jami $totalFiles ta fayl GitHub'ga yuklanmoqda...")

                val uploadedCount = AtomicInteger(0)
                val semaphore = Semaphore(5)
                val treeEntries = coroutineScope {
                    summary.entries.map { entry ->
                        async {
                            semaphore.withPermit {
                                val b64 = Base64.encodeToString(entry.bytes, Base64.NO_WRAP)
                                val blobResp = api.createBlob(
                                    owner = owner,
                                    repo = cleanRepo,
                                    request = CreateBlobRequest(content = b64, encoding = "base64")
                                )
                                if (!blobResp.isSuccessful || blobResp.body() == null) {
                                    throw IllegalStateException(
                                        "Fayl yuklanmadi (${entry.relativePath}): HTTP ${blobResp.code()}"
                                    )
                                }
                                val done = uploadedCount.incrementAndGet()
                                val uploadFrac = 0.28f + (done.toFloat() / totalFiles.coerceAtLeast(1)) * 0.24f
                                if (done == 1 || done % 5 == 0 || done == totalFiles) {
                                    appendLog(LogLevel.GIT, "Yuklandi [$done/$totalFiles]: ${entry.relativePath}")
                                    updatePhase(
                                        phase = BuildPhase.UPLOADING_FILES,
                                        title = "Fayllar yuklanmoqda ($done/$totalFiles)",
                                        detail = entry.relativePath,
                                        progress = uploadFrac
                                    )
                                }
                                val fileMode = if (entry.relativePath == "gradlew" || entry.relativePath.endsWith(".sh")) {
                                    "100755"
                                } else {
                                    "100644"
                                }
                                GitTreeEntryInput(
                                    path = entry.relativePath,
                                    mode = fileMode,
                                    type = "blob",
                                    sha = blobResp.body()!!.sha
                                )
                            }
                        }
                    }.awaitAll()
                }

                appendLog(LogLevel.GIT, "Git daraxti (tree) va commit yaratilmoqda...")
                val treeResp = api.createTree(
                    owner = owner,
                    repo = cleanRepo,
                    request = CreateTreeRequest(baseTree = baseTreeSha, tree = treeEntries)
                )
                if (!treeResp.isSuccessful || treeResp.body() == null) {
                    throw IllegalStateException("Git tree yaratib bo'lmadi: HTTP ${treeResp.code()}")
                }
                val newTreeSha = treeResp.body()!!.sha

                val commitResp = api.createCommit(
                    owner = owner,
                    repo = cleanRepo,
                    request = CreateCommitRequest(
                        message = "Upload Android project via APK Forge",
                        tree = newTreeSha,
                        parents = listOf(headCommitSha)
                    )
                )
                if (!commitResp.isSuccessful || commitResp.body() == null) {
                    throw IllegalStateException("Git commit yaratib bo'lmadi: HTTP ${commitResp.code()}")
                }
                val newCommitSha = commitResp.body()!!.sha

                val updateRefResp = api.updateBranchRef(
                    owner = owner,
                    repo = cleanRepo,
                    branch = defaultBranch,
                    request = UpdateRefRequest(sha = newCommitSha, force = true)
                )
                if (!updateRefResp.isSuccessful) {
                    throw IllegalStateException("Branch yangilanmadi: HTTP ${updateRefResp.code()}")
                }
                appendLog(LogLevel.SUCCESS, "Barcha fayllar push qilindi! Commit: ${newCommitSha.take(7)}")

                // STEP 5: Wait for GitHub Actions workflow run
                updatePhase(
                    phase = BuildPhase.TRIGGERING_BUILD,
                    title = "GitHub Actions ishga tushmoqda...",
                    detail = "APK yig'ish navbati kutilmoqda.",
                    progress = 0.56f
                )
                appendLog(LogLevel.BUILD, "GitHub Actions workflow ishga tushishi kutilmoqda...")

                val runItem = waitForWorkflowRun(api, owner, cleanRepo, newCommitSha, defaultBranch)
                appendLog(
                    LogLevel.BUILD,
                    "Build boshlandi (Run #${runItem.id}): ${runItem.htmlUrl.orEmpty()}"
                )
                _sessionState.update {
                    it.copy(activeRunId = runItem.id, activeWorkflowUrl = runItem.htmlUrl)
                }

                // STEP 6: Monitor live build steps until completion
                updatePhase(
                    phase = BuildPhase.BUILDING_ON_GITHUB,
                    title = "GitHub APK yigʻmoqda...",
                    detail = "Gradle assembleDebug bajarilmoqda.",
                    progress = 0.62f
                )

                val completedRun = monitorWorkflowRunUntilDone(api, owner, cleanRepo, runItem.id)
                if (completedRun.conclusion != "success") {
                    throw IllegalStateException(
                        "GitHub Actions build muvaffaqiyatsiz tugadi (natija: ${completedRun.conclusion ?: "unknown"}). Repo Actions bo'limidan batafsil logni ko'ring."
                    )
                }
                appendLog(LogLevel.SUCCESS, "GitHub Actions APK ni muvaffaqiyatli yig'di!")

                // STEP 7: Download Artifact ZIP & auto-extract APK
                updatePhase(
                    phase = BuildPhase.DOWNLOADING_ARTIFACT,
                    title = "Tayyor APK yuklab olinmoqda...",
                    detail = "GitHub artifact fayli olinmoqda...",
                    progress = 0.86f
                )

                val rawBytes = downloadBuiltArtifactBytes(
                    api = api,
                    token = cleanToken,
                    owner = owner,
                    repo = cleanRepo,
                    runId = runItem.id
                )
                appendLog(LogLevel.INFO, "Yuklab olindi: ${formatBytes(rawBytes.size.toLong())}")

                updatePhase(
                    phase = BuildPhase.EXTRACTING_APK,
                    title = "APK ajratib olinmoqda...",
                    detail = "ZIP ichidan tayyor .apk fayli chiqarilib saqlanmoqda.",
                    progress = 0.94f
                )

                val extracted = ApkStorageHelper.processDownloadedPayloadAndSave(
                    context = appContext,
                    rawBytes = rawBytes,
                    preferredBaseName = summary.detectedAppName
                ) { msg ->
                    appendLog(LogLevel.INFO, msg)
                }

                val savedIconPath = ApkStorageHelper.saveIconBytes(
                    context = appContext,
                    iconBytes = summary.extractedIconBytes,
                    prefix = cleanRepo
                )

                val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(1)
                val insertedId = apkRepository.insert(
                    BuiltApkEntity(
                        appName = summary.detectedAppName,
                        repoName = cleanRepo,
                        ownerUsername = owner,
                        packageName = summary.detectedPackageName,
                        fileName = extracted.fileName,
                        localFilePath = extracted.localFile.absolutePath,
                        downloadsUri = extracted.downloadsUri,
                        iconPath = savedIconPath,
                        fileSizeBytes = extracted.sizeBytes,
                        runId = runItem.id,
                        repoHtmlUrl = repoInfo.htmlUrl,
                        buildDurationSec = durationSec
                    )
                )

                appendLog(
                    LogLevel.SUCCESS,
                    "APK tayyor! '${extracted.fileName}' (${formatBytes(extracted.sizeBytes)}) saqlandi va 'Mening APKlarim' ga qo'shildi."
                )

                ApkBuildForegroundService.stop(appContext)
                if (notificationsEnabledCache) {
                    BuildNotificationHelper.showResultNotification(
                        context = appContext,
                        isSuccess = true,
                        appName = summary.detectedAppName,
                        detailText = "${extracted.fileName} (${formatBytes(extracted.sizeBytes)}) yuklab olindi va tayyor!"
                    )
                }

                _sessionState.update {
                    it.copy(
                        phase = BuildPhase.READY,
                        animatedStatusTitle = "APK tayyor! Yuklab oling",
                        animatedStatusDetail = "${extracted.fileName} (${formatBytes(extracted.sizeBytes)}) avtomatik yuklab olindi.",
                        progress = 1f,
                        latestReadyApkId = insertedId,
                        latestReadyApkName = extracted.fileName,
                        latestReadyApkPath = extracted.localFile.absolutePath,
                        latestReadyDownloadsUri = extracted.downloadsUri,
                        latestReadySizeBytes = extracted.sizeBytes,
                        errorMessage = null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val msg = e.message ?: "Noma'lum xatolik yuz berdi."
                appendLog(LogLevel.ERROR, "Xatolik: $msg")
                ApkBuildForegroundService.stop(appContext)
                if (notificationsEnabledCache) {
                    BuildNotificationHelper.showResultNotification(
                        context = appContext,
                        isSuccess = false,
                        appName = cleanRepo,
                        detailText = msg
                    )
                }
                _sessionState.update {
                    it.copy(
                        phase = BuildPhase.FAILED,
                        animatedStatusTitle = "Jarayon to'xtadi (xatolik)",
                        animatedStatusDetail = msg,
                        progress = 0f,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    private suspend fun ensureRepository(
        api: GitHubApiService,
        owner: String,
        repoName: String,
        isPrivate: Boolean
    ): com.example.data.remote.GitHubRepoResponse {
        val existing = api.getRepository(owner, repoName)
        if (existing.isSuccessful && existing.body() != null) {
            appendLog(LogLevel.WARN, "'$owner/$repoName' repositorysi allaqachon mavjud — yangilanadi.")
            return existing.body()!!
        }

        val createResp = api.createRepository(
            CreateRepoRequest(
                name = repoName,
                description = "Built automatically via APK Forge",
                private = isPrivate,
                autoInit = true
            )
        )
        if (!createResp.isSuccessful || createResp.body() == null) {
            val err = createResp.errorBody()?.string().orEmpty()
            throw IllegalStateException("Repository yaratib bo'lmadi (HTTP ${createResp.code()}): $err")
        }
        return createResp.body()!!
    }

    private suspend fun ensureInitialHeadCommit(
        api: GitHubApiService,
        owner: String,
        repo: String,
        branch: String
    ): String {
        repeat(8) { attempt ->
            val refResp = api.getBranchRef(owner, repo, branch)
            if (refResp.isSuccessful && refResp.body() != null) {
                return refResp.body()!!.refObject.sha
            }
            if (attempt == 3) {
                // Fallback if repository has no initial commit yet
                appendLog(LogLevel.GIT, "Bo'sh repositoryga boshlang'ich README.md qo'shilmoqda...")
                val readmeB64 = Base64.encodeToString("# $repo\n".toByteArray(), Base64.NO_WRAP)
                api.putFileContent(
                    owner = owner,
                    repo = repo,
                    path = "README.md",
                    request = PutFileContentRequest(
                        message = "Initial commit",
                        content = readmeB64,
                        branch = branch
                    )
                )
            }
            delay(800)
        }
        throw IllegalStateException("Repository branch ($branch) tayyor bo'lmadi.")
    }

    private suspend fun waitForWorkflowRun(
        api: GitHubApiService,
        owner: String,
        repo: String,
        commitSha: String,
        branch: String
    ): com.example.data.remote.WorkflowRunItem {
        var dispatchedManually = false
        repeat(30) { attempt ->
            val runsResp = api.getWorkflowRuns(owner, repo, perPage = 10)
            if (runsResp.isSuccessful && runsResp.body() != null) {
                val runs = runsResp.body()!!.workflowRuns
                val matched = runs.firstOrNull { it.headSha == commitSha } ?: runs.firstOrNull()
                if (matched != null) {
                    return matched
                }
            }
            if (attempt == 4 && !dispatchedManually) {
                dispatchedManually = true
                appendLog(LogLevel.BUILD, "Workflow qo'lda trigger qilinmoqda...")
                runCatching {
                    api.dispatchWorkflow(
                        owner = owner,
                        repo = repo,
                        workflowId = "android-apk.yml",
                        request = WorkflowDispatchRequest(ref = branch)
                    )
                }
            }
            delay(3000)
        }
        throw IllegalStateException("GitHub Actions workflow ishga tushmadi. Token 'workflow' ruxsatiga ega ekanligini tekshiring.")
    }

    private suspend fun monitorWorkflowRunUntilDone(
        api: GitHubApiService,
        owner: String,
        repo: String,
        runId: Long
    ): com.example.data.remote.WorkflowRunItem {
        val reportedSteps = mutableSetOf<String>()
        var lastRunStatus = ""
        repeat(180) { tick -> // up to ~12 minutes
            val runResp = api.getWorkflowRun(owner, repo, runId)
            val run = runResp.body()
            if (run != null) {
                val status = run.status.orEmpty()
                if (status != lastRunStatus) {
                    lastRunStatus = status
                    appendLog(LogLevel.BUILD, "GitHub Actions holati: $status")
                }

                val jobsResp = api.getWorkflowJobs(owner, repo, runId)
                val jobs = jobsResp.body()?.jobs.orEmpty()
                var totalSteps = 0
                var completedSteps = 0

                for (job in jobs) {
                    for (step in job.steps) {
                        totalSteps++
                        if (step.status == "completed") completedSteps++
                        val key = "${job.id}:${step.number}:${step.status}:${step.conclusion}"
                        if (reportedSteps.add(key)) {
                            when (step.status) {
                                "in_progress" -> {
                                    appendLog(LogLevel.BUILD, "[Bosqich ${step.number}] ${step.name} — bajarilmoqda...")
                                    updatePhase(
                                        phase = BuildPhase.BUILDING_ON_GITHUB,
                                        title = "APK yigʻilmoqda: ${step.name}",
                                        detail = "GitHub Actions (${completedSteps}/${totalSteps.coerceAtLeast(1)} bosqich)",
                                        progress = 0.62f + (completedSteps.toFloat() / totalSteps.coerceAtLeast(1)) * 0.22f
                                    )
                                }
                                "completed" -> {
                                    val level = if (step.conclusion == "failure") LogLevel.ERROR else LogLevel.SUCCESS
                                    appendLog(level, "[Bosqich ${step.number}] ${step.name} -> ${step.conclusion ?: "done"}")
                                }
                            }
                        }
                    }
                }

                if (status == "completed") {
                    return run
                }
            }
            delay(if (tick < 6) 3000L else 4500L)
        }
        throw IllegalStateException("GitHub Actions build kutish vaqti tugadi.")
    }

    private suspend fun downloadBuiltArtifactBytes(
        api: GitHubApiService,
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): ByteArray = withContext(Dispatchers.IO) {
        val artifactsResp = api.getRunArtifacts(owner, repo, runId)
        val artifacts = artifactsResp.body()?.artifacts.orEmpty().filter { !it.expired }
        if (artifacts.isNotEmpty()) {
            val target = artifacts.firstOrNull {
                it.name.contains("apk", ignoreCase = true)
            } ?: artifacts.first()

            appendLog(
                LogLevel.INFO,
                "Artifact topildi: '${target.name}' (${formatBytes(target.sizeInBytes)}). Yuklab olinmoqda..."
            )
            val noRedirectClient = GitHubClientFactory.createNoRedirectAuthClient(token)
            val initReq = Request.Builder().url(target.archiveDownloadUrl).build()
            val redirectUrl = noRedirectClient.newCall(initReq).execute().use { resp ->
                if (resp.code in 300..399) {
                    resp.header("Location")
                } else if (resp.isSuccessful) {
                    return@withContext resp.body?.bytes()
                        ?: throw IllegalStateException("Artifact javobi bo'sh.")
                } else {
                    throw IllegalStateException("Artifact yuklab olish xatosi: HTTP ${resp.code}")
                }
            } ?: throw IllegalStateException("GitHub artifact redirect manzili olinmadi.")

            val plainClient = GitHubClientFactory.createPlainDownloadClient()
            val dlReq = Request.Builder().url(redirectUrl).build()
            plainClient.newCall(dlReq).execute().use { dlResp ->
                if (!dlResp.isSuccessful || dlResp.body == null) {
                    throw IllegalStateException("Artifact oqimi yuklanmadi: HTTP ${dlResp.code}")
                }
                val body = dlResp.body!!
                val totalLen = body.contentLength()
                val out = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var read = input.read(buf)
                    var downloaded = 0L
                    var lastLoggedPct = -10
                    while (read != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (totalLen > 0) {
                            val pct = ((downloaded * 100) / totalLen).toInt()
                            if (pct >= lastLoggedPct + 25) {
                                lastLoggedPct = pct
                                appendLog(LogLevel.INFO, "Yuklab olinmoqda: $pct% (${formatBytes(downloaded)})")
                            }
                        }
                        read = input.read(buf)
                    }
                }
                return@withContext out.toByteArray()
            }
        }

        // Fallback: check GitHub Releases for direct .apk asset
        val releases = api.getReleases(owner, repo).body().orEmpty()
        val apkAsset = releases.flatMap { it.assets }.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true)
        }
        if (apkAsset != null) {
            appendLog(LogLevel.INFO, "Release APK topildi: ${apkAsset.name}. Yuklab olinmoqda...")
            val plainClient = GitHubClientFactory.createPlainDownloadClient()
            plainClient.newCall(Request.Builder().url(apkAsset.browserDownloadUrl).build()).execute().use { resp ->
                if (resp.isSuccessful && resp.body != null) {
                    return@withContext resp.body!!.bytes()
                }
            }
        }

        throw IllegalStateException("Build muvaffaqiyatli tugadi, lekin yuklab olish uchun APK artifact topilmadi.")
    }

    private fun updatePhase(
        phase: BuildPhase,
        title: String,
        detail: String,
        progress: Float
    ) {
        _sessionState.update {
            it.copy(
                phase = phase,
                animatedStatusTitle = title,
                animatedStatusDetail = detail,
                progress = progress.coerceIn(0f, 1f),
                errorMessage = null
            )
        }
        if (notificationsEnabledCache && phase.isRunning) {
            val pct = (progress * 100).toInt().coerceIn(0, 100)
            BuildNotificationHelper.updateProgressNotification(
                context = appContext,
                statusText = title,
                progressPercent = pct,
                indeterminate = pct <= 5
            )
        }
    }

    fun appendLog(level: LogLevel, message: String) {
        val line = TerminalLogLine(
            id = logIdCounter.getAndIncrement(),
            timestamp = timeFormatter.format(Date()),
            level = level,
            message = message
        )
        _logs.update { current -> (current + line).takeLast(400) }
    }

    private fun sanitizeRepoName(input: String): String {
        return input
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .trim('-', '.')
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.2f MB", mb)
        }
    }
}
