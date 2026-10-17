package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Checks for updates on GitHub, downloads the portable release or installer,
 * unpacks it, and seamlessly applies the update in-place.
 */
internal object DesktopUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The installer or zip archive for this platform, or null when the release has none. */
        val downloadUrl: String?,
        val notes: String?,
        val assetName: String? = null,
        val assetSize: Long = 0L,
    )

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class UpToDate(val version: String) : UpdateState
        data class Available(val info: UpdateInfo) : UpdateState
        data class Downloading(
            val info: UpdateInfo,
            val progress: Float, // 0.0f .. 1.0f, or -1f if indeterminate
            val downloadedBytes: Long,
            val totalBytes: Long,
        ) : UpdateState
        data class Extracting(val info: UpdateInfo) : UpdateState
        data class ReadyToRestart(
            val info: UpdateInfo,
            val isDevMode: Boolean,
            val targetDir: File,
        ) : UpdateState
        data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
    }

    const val REPO_OWNER = "felixforestya-alt"
    const val REPO_NAME = "BitChord2"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RELEASES_ATOM_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases.atom"
    private const val EXPANDED_ASSETS_BASE_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/expanded_assets/"

    val currentVersion: String =
        System.getProperty("bitchord.version")?.takeIf { it.isNotBlank() } ?: "1.0.1"

    /**
     * Whether update checks are disabled for custom dev builds.
     * Set to false so in-app update checks work for all users.
     */
    const val customBuild = false

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _latestUpdate = MutableStateFlow<UpdateInfo?>(null)
    val latestUpdate: StateFlow<UpdateInfo?> = _latestUpdate.asStateFlow()

    private var activeDownloadJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    private val javaClient: JavaHttpClient by lazy {
        JavaHttpClient.newBuilder()
            .followRedirects(JavaHttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    suspend fun check(): UpdateInfo? = checkForUpdates(force = false)

    suspend fun checkForUpdates(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val currentState = _state.value
        if (!force && (currentState is UpdateState.Checking || currentState is UpdateState.Downloading || currentState is UpdateState.Extracting)) {
            return@withContext _latestUpdate.value
        }
        _state.value = UpdateState.Checking

        val result = runCatching { fetchLatestReleaseViaApi() }
            .getOrNull()
            ?: runCatching { fetchLatestReleaseViaFallback() }
                .getOrNull()

        if (result == null) {
            _state.value = UpdateState.Error("Не удалось получить информацию о релизах с GitHub")
            return@withContext null
        }

        val latest = result.version.trim().removePrefix("v").removePrefix("V")
        if (!isNewer(latest, currentVersion)) {
            _state.value = UpdateState.UpToDate(currentVersion)
            _latestUpdate.value = null
            return@withContext null
        }

        _latestUpdate.value = result
        _state.value = UpdateState.Available(result)
        result
    }

    private suspend fun fetchLatestReleaseViaApi(): UpdateInfo? {
        val response = http.get(LATEST_RELEASE_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null
        val release = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val latest = tag.trim().removePrefix("v").removePrefix("V")
        val asset = installerAssetFromApi(release)
        return UpdateInfo(
            version = latest,
            releaseUrl = url,
            downloadUrl = asset?.first,
            notes = release["body"]?.jsonPrimitive?.contentOrNull,
            assetName = asset?.second,
            assetSize = asset?.third ?: 0L,
        )
    }

    private fun installerAssetFromApi(release: JsonObject): Triple<String, String, Long>? {
        val assets = release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        val hit = pickAsset(assets.map { asset ->
            val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val downloadUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val size = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val state = asset["state"]?.jsonPrimitive?.contentOrNull.orEmpty()
            AssetCandidate(name, downloadUrl, size, state == "uploaded")
        }) ?: return null
        return Triple(hit.url, hit.name, hit.size)
    }

    private fun fetchLatestReleaseViaFallback(): UpdateInfo? {
        val atomReq = HttpRequest.newBuilder()
            .uri(URI.create(RELEASES_ATOM_URL))
            .header("User-Agent", "BitChord/$currentVersion")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val atomXml = javaClient.send(atomReq, HttpResponse.BodyHandlers.ofString()).body() ?: return null
        val tagMatcher = Pattern.compile("releases/tag/([^\"/?#]+)").matcher(atomXml)
        if (!tagMatcher.find()) return null
        val rawTag = tagMatcher.group(1)
        val tag = rawTag.trim().removePrefix("v").removePrefix("V")

        var notes: String? = null
        val contentMatcher = Pattern.compile("<content type=\"html\">(.*?)</content>", Pattern.DOTALL).matcher(atomXml)
        if (contentMatcher.find()) {
            val html = contentMatcher.group(1)
            notes = Pattern.compile("<[^>]*>").matcher(html).replaceAll("").trim()
        }

        val releaseUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$rawTag"

        val assetsReq = HttpRequest.newBuilder()
            .uri(URI.create("$EXPANDED_ASSETS_BASE_URL$rawTag"))
            .header("User-Agent", "BitChord/$currentVersion")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val assetsHtml = javaClient.send(assetsReq, HttpResponse.BodyHandlers.ofString()).body().orEmpty()
        val assetMatcher = Pattern.compile("href=\"(/[^\"]+/releases/download/[^\"]+)\"").matcher(assetsHtml)
        val candidates = mutableListOf<AssetCandidate>()
        while (assetMatcher.find()) {
            val path = assetMatcher.group(1)
            val name = path.substringAfterLast('/')
            val fullUrl = "https://github.com$path"
            candidates.add(AssetCandidate(name, fullUrl, 0L, true))
        }

        val chosen = pickAsset(candidates)
        return UpdateInfo(
            version = tag,
            releaseUrl = releaseUrl,
            downloadUrl = chosen?.url,
            notes = notes,
            assetName = chosen?.name,
            assetSize = chosen?.size ?: 0L,
        )
    }

    private data class AssetCandidate(
        val name: String,
        val url: String,
        val size: Long,
        val uploaded: Boolean,
    )

    private fun pickAsset(candidates: List<AssetCandidate>): AssetCandidate? {
        val valid = candidates.filter { it.uploaded && it.url.isNotBlank() }
        if (DesktopPlatform.isWindows) {
            // Priority 1: Portable zip archive (user requested automatic download & unpack)
            val zip = valid.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
            if (zip != null) return zip
            // Priority 2: Installer executable
            return valid.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
        } else if (DesktopPlatform.isLinux) {
            return valid.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
                ?: valid.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
                ?: valid.firstOrNull { it.name.endsWith(".rpm", ignoreCase = true) }
                ?: valid.firstOrNull { it.name.endsWith(".tar.gz", ignoreCase = true) }
        } else if (DesktopPlatform.isMac) {
            return valid.firstOrNull { it.name.endsWith(".dmg", ignoreCase = true) }
                ?: valid.firstOrNull { it.name.endsWith(".pkg", ignoreCase = true) }
                ?: valid.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
        }
        return valid.firstOrNull()
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        val info = _latestUpdate.value
        _state.value = if (info != null) UpdateState.Available(info) else UpdateState.Idle
    }

    suspend fun downloadAndApply(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val downloadUrl = info.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            _state.value = UpdateState.Error("Ссылка на загрузку не найдена в релизе", info)
            return@withContext
        }

        val tempDir = File(System.getProperty("java.io.tmpdir"), "BitChord_update")
        tempDir.mkdirs()

        val fileName = info.assetName
            ?: downloadUrl.substringAfterLast('/').substringBefore('?')
            .takeIf { it.isNotBlank() }
            ?: (if (downloadUrl.endsWith(".exe", ignoreCase = true)) "update.exe" else "update.zip")
        val destFile = File(tempDir, fileName)

        _state.value = UpdateState.Downloading(info, 0f, 0L, info.assetSize)

        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "BitChord/$currentVersion")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build()

            val response = javaClient.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                _state.value = UpdateState.Error("Ошибка сервера при загрузке: HTTP ${response.statusCode()}", info)
                return@withContext
            }

            val totalBytes = response.headers().firstValueAsLong("Content-Length")
                .orElse(if (info.assetSize > 0) info.assetSize else 0L)

            var downloadedBytes = 0L
            val buffer = ByteArray(64 * 1024)

            FileOutputStream(destFile).use { fos ->
                response.body().use { input ->
                    var bytesRead = 0
                    while (coroutineContext.isActive && input.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        _state.value = UpdateState.Downloading(info, progress, downloadedBytes, totalBytes)
                    }
                }
            }

            if (!coroutineContext.isActive) {
                destFile.delete()
                _state.value = UpdateState.Available(info)
                return@withContext
            }

            if (destFile.name.endsWith(".zip", ignoreCase = true)) {
                _state.value = UpdateState.Extracting(info)
                val extractDir = File(tempDir, "extracted")
                if (extractDir.exists()) extractDir.deleteRecursively()
                extractDir.mkdirs()

                unzip(destFile, extractDir)

                val exeInZip = extractDir.walkTopDown().firstOrNull {
                    it.isFile && it.name.equals("BitChord.exe", ignoreCase = true)
                }
                val payloadDir = exeInZip?.parentFile ?: extractDir
                val installDir = findCurrentInstallDir()
                val isDevMode = installDir == null || !File(installDir, "BitChord.exe").exists()

                _state.value = UpdateState.ReadyToRestart(
                    info = info,
                    isDevMode = isDevMode,
                    targetDir = if (isDevMode) payloadDir else installDir,
                )
            } else {
                val installDir = findCurrentInstallDir()
                val isDevMode = installDir == null
                _state.value = UpdateState.ReadyToRestart(
                    info = info,
                    isDevMode = isDevMode,
                    targetDir = destFile.parentFile,
                )
            }
        } catch (_: CancellationException) {
            destFile.delete()
            _state.value = UpdateState.Available(info)
        } catch (t: Throwable) {
            destFile.delete()
            _state.value = UpdateState.Error("Ошибка при загрузке: ${t.localizedMessage ?: t.message}", info)
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry?
            val canonicalDestDirPath = destDir.canonicalPath
            while (zis.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: break
                val targetFile = File(destDir, currentEntry.name)
                if (!targetFile.canonicalPath.startsWith(canonicalDestDirPath + File.separator) &&
                    targetFile.canonicalPath != canonicalDestDirPath
                ) {
                    continue
                }
                if (currentEntry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
            }
        }
    }

    fun findCurrentInstallDir(): File? {
        // 1. ProcessHandle command path (when launched via BitChord.exe)
        try {
            val cmd = ProcessHandle.current().info().command().orElse(null)
            if (!cmd.isNullOrBlank()) {
                val f = File(cmd)
                if (f.isFile && f.name.contains("BitChord", ignoreCase = true)) {
                    return f.parentFile
                }
            }
        } catch (_: Throwable) {}

        // 2. CodeSource location (when inside packaged desktopApp.jar in app/ directory)
        try {
            val cs = DesktopUpdateChecker::class.java.protectionDomain?.codeSource?.location
            if (cs != null) {
                val f = File(cs.toURI())
                if (f.isFile && f.parentFile?.name == "app") {
                    val root = f.parentFile?.parentFile
                    if (root != null && File(root, "BitChord.exe").exists()) {
                        return root
                    }
                }
                if (f.isDirectory && File(f, "BitChord.exe").exists()) {
                    return f
                }
            }
        } catch (_: Throwable) {}

        // 3. User directory if BitChord.exe exists there
        try {
            val userDir = File(System.getProperty("user.dir"))
            if (File(userDir, "BitChord.exe").exists()) {
                return userDir
            }
        } catch (_: Throwable) {}

        return null
    }

    fun applyAndRestart() {
        val current = _state.value as? UpdateState.ReadyToRestart ?: return
        if (current.isDevMode) {
            try {
                ProcessBuilder("explorer.exe", current.targetDir.absolutePath).start()
            } catch (_: Throwable) {
                DesktopExternalLinks.open(current.targetDir.toURI().toString())
            }
            return
        }

        if (DesktopPlatform.isWindows) {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "BitChord_update")
            val extractDir = File(tempDir, "extracted")
            val exeInZip = extractDir.walkTopDown().firstOrNull {
                it.isFile && it.name.equals("BitChord.exe", ignoreCase = true)
            }
            val payloadDir = exeInZip?.parentFile
            val installDir = current.targetDir
            val targetExe = File(installDir, "BitChord.exe")

            if (payloadDir != null && targetExe.exists()) {
                val batchFile = File(tempDir, "apply_update.cmd")
                batchFile.writeText(
                    """
                    @echo off
                    chcp 65001 >nul
                    set "PID=%~1"
                    set "SRC=%~2"
                    set "DST=%~3"
                    set "EXE=%~4"
                    set "LOG=%TEMP%\bitchord_update.log"
                    echo [%date% %time%] Waiting for process %PID% to terminate... > "%LOG%"

                    :wait_proc
                    tasklist /fi "PID eq %PID%" 2>nul | findstr /i "%PID%" >nul
                    if not errorlevel 1 (
                        timeout /t 1 /nobreak >nul
                        goto wait_proc
                    )

                    echo [%date% %time%] Process ended. Copying files from %SRC% to %DST%... >> "%LOG%"
                    timeout /t 1 /nobreak >nul

                    robocopy "%SRC%" "%DST%" /E /IS /IT /NP /R:5 /W:1 >> "%LOG%" 2>&1
                    if errorlevel 8 (
                        echo [%date% %time%] Robocopy error %errorlevel%, falling back to xcopy... >> "%LOG%"
                        xcopy "%SRC%\*" "%DST%\" /E /Y /Q /H /R >> "%LOG%" 2>&1
                    )

                    echo [%date% %time%] Restarting %EXE%... >> "%LOG%"
                    start "" "%EXE%"

                    timeout /t 3 /nobreak >nul
                    rmdir /s /q "%SRC%" >nul 2>&1
                    echo [%date% %time%] Update complete. >> "%LOG%"
                    exit
                    """.trimIndent(),
                    Charsets.UTF_8,
                )

                ProcessBuilder(
                    "cmd.exe", "/c", "start", "\"BitChord Updater\"", "/min",
                    batchFile.absolutePath,
                    ProcessHandle.current().pid().toString(),
                    payloadDir.absolutePath,
                    installDir.absolutePath,
                    targetExe.absolutePath,
                ).start()

                kotlin.system.exitProcess(0)
            } else {
                val exeInstaller = tempDir.listFiles()?.firstOrNull {
                    it.name.endsWith(".exe", ignoreCase = true) && !it.name.equals("apply_update.cmd", ignoreCase = true)
                }
                if (exeInstaller != null) {
                    ProcessBuilder(exeInstaller.absolutePath).start()
                    kotlin.system.exitProcess(0)
                }
            }
        }
    }

    private class Parsed(val parts: List<Int>, val preRelease: Boolean)

    private fun parse(raw: String): Parsed {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val dash = clean.indexOf('-')
        val base = if (dash >= 0) clean.substring(0, dash) else clean
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric comparison, with a `-betaN` build counted as older than the plain release it leads up to. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.preRelease && !l.preRelease
    }
}
