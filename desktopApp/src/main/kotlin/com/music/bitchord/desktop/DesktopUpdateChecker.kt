package com.music.bitchord.desktop

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Updates from this fork's GitHub releases, start to finish: is there a newer one, fetch it,
 * check it is the file the release says it is, unpack it beside the app, and swap it in once the
 * app is out of the way.
 *
 * The swap cannot happen from inside: the running app holds its own jars and runtime open. So it
 * is left to a small script started as the app exits, which waits for it to be gone, copies the
 * old build aside, mirrors the new one over it — removing files the new build no longer has —
 * and, if any of that fails, puts the old one back. How it went is written to a file the next
 * launch reads, so a failed update is said out loud rather than discovered.
 *
 * Only a portable build that can write to its own folder updates itself. Anything else — a run
 * from Gradle, an installed copy under Program Files — is pointed at the release page instead.
 */
internal object DesktopUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The Windows archive, or null when the release has none. */
        val downloadUrl: String?,
        val notes: String?,
        val assetName: String? = null,
        val assetSize: Long = 0L,
        /** The archive's SHA-256 as GitHub records it, lower-case hex; null when not given. */
        val sha256: String? = null,
    )

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val info: UpdateInfo) : UpdateState
        /** [progress] in 0..1, or -1 while the size is unknown. */
        data class Downloading(val info: UpdateInfo, val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState
        data class Preparing(val info: UpdateInfo) : UpdateState
        /** Unpacked and checked; goes in when the app next exits, or now with [restartNow]. */
        data class Ready(val info: UpdateInfo) : UpdateState
        data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
    }

    const val REPO_OWNER = "felixforestya-alt"
    const val REPO_NAME = "BitChord2"
    const val RELEASES_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    val currentVersion: String =
        System.getProperty("bitchord.version")?.takeIf { it.isNotBlank() }?.removePrefix("v") ?: "dev"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    /** Where downloads are unpacked and the swap script and its report are kept. */
    private val workDir: File by lazy { DesktopMediaCache.directory.parent.resolve("updates").toFile() }
    private val stagedDir: File get() = File(workDir, "staged")
    private val backupDir: File get() = File(workDir, "backup")
    private val resultFile: File get() = File(workDir, "result.txt")

    /** Set by the window: ends the app the ordinary way, so it saves what it saves on the way out. */
    @Volatile
    var exitApp: (() -> Unit)? = null

    @Volatile
    private var installOnExit = false

    @Volatile
    private var relaunchAfterInstall = false

    private var hookInstalled = false

    /**
     * The packaged build's folder — the one with `BitChord.exe`, `app` and `runtime` — when this
     * is one; null when run from Gradle or anywhere else that is not a portable build.
     */
    val installDir: File? by lazy {
        if (!DesktopPlatform.isWindows) return@lazy null
        val launcher = runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
            ?.let(::File)
            ?.takeIf { it.isFile && it.name.equals("BitChord.exe", ignoreCase = true) }
            ?: return@lazy null
        launcher.parentFile?.takeIf { isAppFolder(it) }
    }

    /** Whether this copy can put an update in place itself. */
    val canSelfUpdate: Boolean by lazy {
        val dir = installDir ?: return@lazy false
        runCatching {
            val probe = File(File(dir, "app"), ".update-probe")
            probe.writeText("")
            probe.delete()
        }.isSuccess
    }

    /**
     * Asks GitHub for the latest release. Quiet about failures when [userAsked] is false — a
     * launch without the network is not worth an error.
     */
    suspend fun check(userAsked: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val busy = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Preparing || it is UpdateState.Ready }
        if (busy) return@withContext (_state.value as? UpdateState.Ready)?.info
        _state.value = UpdateState.Checking
        val info = runCatching { fetchLatest() }.getOrElse {
            DesktopTrackLog.log("update check failed: ${it.message}")
            _state.value = if (userAsked) {
                UpdateState.Error(DesktopStrings["d_update_check_failed", "Could not reach GitHub to check for updates"])
            } else {
                UpdateState.Idle
            }
            return@withContext null
        }
        if (info == null || !isNewer(info.version, currentVersion)) {
            _state.value = UpdateState.UpToDate
            return@withContext null
        }
        _state.value = UpdateState.Available(info)
        info
    }

    private fun fetchLatest(): UpdateInfo? {
        val request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_URL))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BitChord/$currentVersion")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "GitHub answered ${response.statusCode()}" }
        val release = json.parseToJsonElement(response.body()) as? JsonObject ?: return null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val asset = release["assets"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it["state"]?.jsonPrimitive?.contentOrNull == "uploaded" }
            .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".zip", ignoreCase = true) }
        return UpdateInfo(
            version = tag.trim().removePrefix("v").removePrefix("V"),
            releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_URL,
            downloadUrl = asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
            notes = release["body"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            assetName = asset?.get("name")?.jsonPrimitive?.contentOrNull,
            assetSize = asset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            sha256 = asset?.get("digest")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
                ?.lowercase(),
        )
    }

    /**
     * Downloads [info]'s archive, checks it, and unpacks it ready to go in. One at a time; a
     * second call while one runs just waits for it.
     */
    suspend fun download(info: UpdateInfo) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if ((_state.value as? UpdateState.Ready)?.info == info) return@withContext
            val url = info.downloadUrl
            if (url == null || !canSelfUpdate) {
                _state.value = UpdateState.Error(
                    DesktopStrings["d_update_manual", "This copy cannot update itself — download the new version from the release page"],
                    info,
                )
                return@withContext
            }
            val archive = File(workDir, "download.zip")
            try {
                workDir.mkdirs()
                fetchArchive(info, url, archive)
                _state.value = UpdateState.Preparing(info)
                stagedDir.deleteRecursively()
                unzip(archive, stagedDir)
                val payload = stagedDir.walkTopDown().maxDepth(3)
                    .firstOrNull { it.isDirectory && isAppFolder(it) }
                    ?: error(DesktopStrings["d_update_bad_archive", "The download is not a BitChord build"])
                // Kept where the script will look for it, whatever the archive's folder was called.
                File(workDir, "payload.txt").writeText(payload.absolutePath)
                archive.delete()
                _state.value = UpdateState.Ready(info)
                // Asked for, and checked: it goes in at the next exit unless restarted for sooner.
                installWhenAppExits()
                DesktopTrackLog.log("update ${info.version} ready at $payload")
            } catch (cancelled: CancellationException) {
                archive.delete()
                _state.value = UpdateState.Available(info)
                throw cancelled
            } catch (failure: Throwable) {
                archive.delete()
                stagedDir.deleteRecursively()
                DesktopTrackLog.log("update download failed: ${failure.message}")
                _state.value = UpdateState.Error(
                    failure.message ?: DesktopStrings["d_update_download_failed", "The update could not be downloaded"],
                    info,
                )
            }
        }
    }

    private suspend fun fetchArchive(info: UpdateInfo, url: String, into: File) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "BitChord/$currentVersion")
            .timeout(Duration.ofMinutes(30))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            error("HTTP ${response.statusCode()}")
        }
        val total = response.headers().firstValueAsLong("Content-Length").orElse(info.assetSize)
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var lastShown = 0L
        _state.value = UpdateState.Downloading(info, if (total > 0) 0f else -1f, 0L, total)
        response.body().use { input ->
            into.outputStream().buffered().use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    done += read
                    // A few times a second, not once per read: every new state is a frame of UI.
                    val now = System.nanoTime()
                    if (now - lastShown > PROGRESS_INTERVAL_NS) {
                        lastShown = now
                        val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                        _state.value = UpdateState.Downloading(info, progress, done, total)
                    }
                }
            }
        }
        if (info.assetSize > 0 && done != info.assetSize) {
            error(DesktopStrings["d_update_incomplete", "The download was cut short"])
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && actual != info.sha256) {
            error(DesktopStrings["d_update_checksum", "The download does not match the release — not installed"])
        }
    }

    internal fun unzip(archive: File, into: File) {
        into.mkdirs()
        val root = into.canonicalFile
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Archives made by Windows' own zipper name folders with backslashes.
                val name = entry.name.replace('\\', '/')
                val target = File(root, name).canonicalFile
                // Nothing outside the folder it is unpacked into, whatever the archive says.
                if (!target.path.startsWith(root.path + File.separator)) continue
                if (entry.isDirectory || name.endsWith("/")) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { zip.copyTo(it) }
                }
            }
        }
    }

    /** The update goes in when the app next exits, without opening it again. */
    fun installWhenAppExits() {
        if (_state.value !is UpdateState.Ready) return
        installOnExit = true
        ensureExitHook()
    }

    /** Ends the app now, puts the update in, and opens it again. */
    fun restartNow() {
        if (_state.value !is UpdateState.Ready) return
        installOnExit = true
        relaunchAfterInstall = true
        ensureExitHook()
        val exit = exitApp
        if (exit != null) exit() else kotlin.system.exitProcess(0)
    }

    private fun ensureExitHook() {
        if (hookInstalled) return
        hookInstalled = true
        Runtime.getRuntime().addShutdownHook(Thread({
            if (installOnExit && _state.value is UpdateState.Ready) runCatching { startInstaller(relaunchAfterInstall) }
        }, "bitchord-update-installer"))
    }

    private fun startInstaller(relaunch: Boolean) {
        val dir = installDir ?: return
        val payload = File(File(workDir, "payload.txt").readText().trim())
        if (!isAppFolder(payload)) return
        val version = (_state.value as? UpdateState.Ready)?.info?.version.orEmpty()
        val script = File(workDir, "install.ps1")
        script.writeText(INSTALL_SCRIPT)
        resultFile.delete()
        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-File", script.absolutePath,
            "-ProcId", ProcessHandle.current().pid().toString(),
            "-Src", payload.absolutePath,
            "-Dst", dir.absolutePath,
            "-Backup", backupDir.absolutePath,
            "-Relaunch", if (relaunch) "1" else "0",
            "-Result", resultFile.absolutePath,
            "-Version", version,
        )
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    /**
     * What the last update did, once, at the next launch: the version it put in, or why it could
     * not. Null when there was none to report.
     */
    fun takeLastResult(): Result<String>? {
        val file = resultFile
        if (!file.exists()) return null
        val text = runCatching { file.readText().trim().removePrefix("﻿") }.getOrDefault("")
        file.delete()
        runCatching { stagedDir.deleteRecursively() }
        return when {
            text.startsWith("ok") -> Result.success(text.removePrefix("ok").trim())
            else -> Result.failure(IllegalStateException(text.removePrefix("failed").trim()))
        }
    }

    private fun isAppFolder(dir: File): Boolean =
        File(dir, "BitChord.exe").isFile && File(dir, "app/BitChord.cfg").isFile && File(dir, "runtime").isDirectory

    private class Parsed(val parts: List<Int>, val preRelease: Boolean)

    private fun parse(raw: String): Parsed {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val dash = clean.indexOf('-')
        val base = if (dash >= 0) clean.substring(0, dash) else clean
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric comparison, with a `-betaN` build counted as older than the plain release it leads up to. */
    internal fun isNewer(latest: String, current: String): Boolean {
        // A build from Gradle has no version to compare; it is never offered one.
        if (current == "dev") return false
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.preRelease && !l.preRelease
    }

    private const val PROGRESS_INTERVAL_NS = 150_000_000L

    /**
     * The swap, in PowerShell: wait for the app to exit, keep the old build aside, mirror the new
     * one in, and put the old one back if anything fails. Plain ASCII, so the console's codepage
     * does not matter; the paths come in as arguments.
     */
    internal val INSTALL_SCRIPT = """
        param([int]${'$'}ProcId, [string]${'$'}Src, [string]${'$'}Dst, [string]${'$'}Backup, [int]${'$'}Relaunch, [string]${'$'}Result, [string]${'$'}Version)
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}log = ${'$'}Result + '.log'
        function Log([string]${'$'}m) { Add-Content -LiteralPath ${'$'}log -Value ('[' + (Get-Date -Format s) + '] ' + ${'$'}m) }
        function Mirror([string]${'$'}from, [string]${'$'}to) {
            & robocopy ${'$'}from ${'$'}to /MIR /R:30 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
            if (${'$'}LASTEXITCODE -ge 8) { throw ('robocopy ' + ${'$'}from + ' failed with ' + ${'$'}LASTEXITCODE) }
        }
        function CopyTop([string]${'$'}from, [string]${'$'}to) {
            New-Item -ItemType Directory -Force -Path ${'$'}to | Out-Null
            Get-ChildItem -LiteralPath ${'$'}from -File | Copy-Item -Destination ${'$'}to -Force
        }
        Log ('installing ' + ${'$'}Version + ' into ' + ${'$'}Dst)
        try { Wait-Process -Id ${'$'}ProcId -Timeout 120 -ErrorAction SilentlyContinue } catch {}
        Start-Sleep -Milliseconds 800
        ${'$'}ok = ${'$'}false
        try {
            foreach (${'$'}d in 'app', 'runtime') { Mirror (Join-Path ${'$'}Dst ${'$'}d) (Join-Path ${'$'}Backup ${'$'}d) }
            CopyTop ${'$'}Dst ${'$'}Backup
            foreach (${'$'}d in 'app', 'runtime') { Mirror (Join-Path ${'$'}Src ${'$'}d) (Join-Path ${'$'}Dst ${'$'}d) }
            CopyTop ${'$'}Src ${'$'}Dst
            ${'$'}ok = ${'$'}true
            Set-Content -LiteralPath ${'$'}Result -Value ('ok ' + ${'$'}Version)
            Log 'done'
        } catch {
            ${'$'}why = [string]${'$'}_
            Log ('failed: ' + ${'$'}why)
            try {
                foreach (${'$'}d in 'app', 'runtime') { Mirror (Join-Path ${'$'}Backup ${'$'}d) (Join-Path ${'$'}Dst ${'$'}d) }
                CopyTop ${'$'}Backup ${'$'}Dst
                Log 'old build restored'
            } catch { Log ('restore failed: ' + [string]${'$'}_) }
            Set-Content -LiteralPath ${'$'}Result -Value ('failed ' + ${'$'}why)
        }
        if (${'$'}Relaunch -eq 1) { Start-Process -FilePath (Join-Path ${'$'}Dst 'BitChord.exe') }
        if (${'$'}ok) { Remove-Item -LiteralPath ${'$'}Src -Recurse -Force -ErrorAction SilentlyContinue }
    """.trimIndent()
}
