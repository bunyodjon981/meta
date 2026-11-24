package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.ZipFileEntry
import com.example.data.model.ZipProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object ZipProjectInspector {

    private val ignoredPrefixes = listOf(
        ".git/",
        ".gradle/",
        ".idea/",
        "build/",
        "app/build/",
        "__MACOSX/",
        ".kotlin/"
    )

    private val ignoredExactFiles = setOf(
        ".DS_Store",
        "Thumbs.db",
        "local.properties"
    )

    suspend fun inspectZip(
        context: Context,
        uri: Uri,
        fallbackRepoName: String
    ): ZipProjectSummary = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(context, uri)
        val rawEntries = mutableListOf<ZipFileEntry>()
        var totalSize = 0L

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory && name.isNotBlank() && !name.contains("..")) {
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var read = zis.read(chunk)
                        while (read != -1) {
                            buffer.write(chunk, 0, read)
                            read = zis.read(chunk)
                        }
                        val bytes = buffer.toByteArray()
                        totalSize += bytes.size
                        rawEntries.add(ZipFileEntry(relativePath = name, bytes = bytes))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("ZIP faylini ochib bo'lmadi.")

        if (rawEntries.isEmpty()) {
            throw IllegalArgumentException("Tanlangan ZIP fayli bo'sh yoki noto'g'ri formatda.")
        }

        // Strip redundant top-level folder if all entries share a single root folder
        // (e.g., MyProject/app/build.gradle.kts -> app/build.gradle.kts)
        val firstSegments = rawEntries.mapNotNull { entry ->
            val idx = entry.relativePath.indexOf('/')
            if (idx > 0) entry.relativePath.substring(0, idx) else null
        }.distinct()

        val allHaveSameRootFolder = rawEntries.all { it.relativePath.contains('/') } && firstSegments.size == 1
        val strippedRoot = if (allHaveSameRootFolder) firstSegments.first() else null

        val normalizedEntries = rawEntries.mapNotNull { entry ->
            val path = if (strippedRoot != null) {
                entry.relativePath.removePrefix("$strippedRoot/")
            } else {
                entry.relativePath
            }.trimStart('/')

            if (path.isBlank()) return@mapNotNull null

            val fileOnly = path.substringAfterLast('/')
            val shouldSkip = fileOnly in ignoredExactFiles ||
                ignoredPrefixes.any { prefix ->
                    path.startsWith(prefix) || path.contains("/$prefix")
                }

            if (shouldSkip) null else ZipFileEntry(relativePath = path, bytes = entry.bytes)
        }

        if (normalizedEntries.isEmpty()) {
            throw IllegalArgumentException("ZIP ichida yuklash uchun loyiha fayllari topilmadi.")
        }

        val hasExistingWorkflow = normalizedEntries.any {
            it.relativePath.startsWith(".github/workflows/") &&
                (it.relativePath.endsWith(".yml") || it.relativePath.endsWith(".yaml"))
        }

        val hasWrapperJar = normalizedEntries.any { it.relativePath == "gradle/wrapper/gradle-wrapper.jar" } &&
            normalizedEntries.any { it.relativePath == "gradlew" }

        val gradleVersion = detectGradleVersion(normalizedEntries)
        val appName = detectAppName(normalizedEntries, fallbackRepoName.ifBlank { fileName.removeSuffix(".zip") })
        val packageName = detectPackageName(normalizedEntries)
        val iconBytes = extractProjectIconBytes(normalizedEntries)

        val finalEntries = if (hasExistingWorkflow) {
            normalizedEntries
        } else {
            val workflowYaml = buildAndroidApkWorkflow(
                gradleVersion = gradleVersion ?: "8.11.1",
                preferWrapper = hasWrapperJar
            )
            normalizedEntries + ZipFileEntry(
                relativePath = ".github/workflows/android-apk.yml",
                bytes = workflowYaml.toByteArray(Charsets.UTF_8)
            )
        }

        ZipProjectSummary(
            sourceFileName = fileName,
            sourceUriString = uri.toString(),
            totalSizeBytes = totalSize,
            detectedAppName = appName,
            detectedPackageName = packageName,
            gradleVersion = gradleVersion,
            hasWrapperJar = hasWrapperJar,
            hasExistingWorkflow = hasExistingWorkflow,
            fileCount = finalEntries.size,
            strippedRootFolder = strippedRoot,
            extractedIconBytes = iconBytes,
            entries = finalEntries
        )
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx) ?: "project.zip"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "project.zip"
    }

    private fun detectGradleVersion(entries: List<ZipFileEntry>): String? {
        val wrapperProps = entries.firstOrNull {
            it.relativePath == "gradle/wrapper/gradle-wrapper.properties"
        } ?: return null
        val text = wrapperProps.bytes.toString(Charsets.UTF_8)
        val regex = Regex("""gradle-([0-9]+\.[0-9]+(?:\.[0-9]+)?)-(?:bin|all)\.zip""")
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun detectAppName(entries: List<ZipFileEntry>, fallback: String): String {
        val stringsXml = entries.firstOrNull {
            it.relativePath == "app/src/main/res/values/strings.xml"
        }
        if (stringsXml != null) {
            val text = stringsXml.bytes.toString(Charsets.UTF_8)
            val match = Regex("""<string\s+name="app_name"\s*>([^<]+)</string>""").find(text)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate != "My Application") {
                return candidate
            }
        }

        val metadataJson = entries.firstOrNull { it.relativePath == "metadata.json" }
        if (metadataJson != null) {
            val text = metadataJson.bytes.toString(Charsets.UTF_8)
            val match = Regex(""""name"\s*:\s*"([^"]+)"""").find(text)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank()) return candidate
        }

        val settingsGradle = entries.firstOrNull {
            it.relativePath == "settings.gradle.kts" || it.relativePath == "settings.gradle"
        }
        if (settingsGradle != null) {
            val text = settingsGradle.bytes.toString(Charsets.UTF_8)
            val match = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""").find(text)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate != "My Application") {
                return candidate
            }
        }

        return fallback.ifBlank { "AndroidApp" }
    }

    private fun detectPackageName(entries: List<ZipFileEntry>): String {
        val appGradle = entries.firstOrNull {
            it.relativePath == "app/build.gradle.kts" || it.relativePath == "app/build.gradle"
        } ?: return "com.example.app"
        val text = appGradle.bytes.toString(Charsets.UTF_8)
        val appIdMatch = Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(text)
        if (appIdMatch != null) return appIdMatch.groupValues[1]
        val nsMatch = Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(text)
        return nsMatch?.groupValues?.getOrNull(1) ?: "com.example.app"
    }

    private fun extractProjectIconBytes(entries: List<ZipFileEntry>): ByteArray? {
        val preferredOrder = listOf(
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-hdpi/ic_launcher.png"
        )
        for (path in preferredOrder) {
            entries.firstOrNull { it.relativePath == path }?.let { return it.bytes }
        }
        return entries.firstOrNull { entry ->
            entry.relativePath.startsWith("app/src/main/res/") &&
                (entry.relativePath.endsWith(".png") ||
                    entry.relativePath.endsWith(".jpg") ||
                    entry.relativePath.endsWith(".webp")) &&
                (entry.relativePath.contains("ic_launcher") || entry.relativePath.contains("logo") || entry.relativePath.contains("icon"))
        }?.bytes
    }

    private fun buildAndroidApkWorkflow(gradleVersion: String, preferWrapper: Boolean): String {
        val gradleSetupBlock = if (preferWrapper) {
            """
            - name: Setup Gradle
              uses: gradle/actions/setup-gradle@v4
            """.trimIndent()
        } else {
            """
            - name: Setup Gradle
              uses: gradle/actions/setup-gradle@v4
              with:
                gradle-version: '$gradleVersion'
            """.trimIndent()
        }

        return """
name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build-apk:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      ${gradleSetupBlock.replace("\n", "\n      ")}

      - name: Ensure debug keystore
        run: |
          if [ ! -f "debug.keystore" ]; then
            keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
          fi

      - name: Build Debug APK
        run: |
          if [ -f "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
            chmod +x ./gradlew
            ./gradlew assembleDebug --no-daemon --stacktrace
          else
            gradle assembleDebug --no-daemon --stacktrace
          fi

      - name: Collect APK outputs
        run: |
          mkdir -p apk_output
          find . -type f -name "*.apk" -path "*/build/outputs/apk/*" -exec cp {} apk_output/ \;
          ls -lh apk_output/

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: apk_output/*.apk
          if-no-files-found: error
""".trimIndent() + "\n"
    }
}
