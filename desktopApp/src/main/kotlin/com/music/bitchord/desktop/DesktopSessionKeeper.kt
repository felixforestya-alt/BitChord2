package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the signed-in YouTube session alive the way an open browser tab does.
 *
 * Google's session cookies carry a short-lived timestamp half (`__Secure-1PSIDTS` /
 * `__Secure-3PSIDTS`) that a browser renews in the background through `RotateCookies`. A session
 * nobody renews is, some time later, answered as signed out — which on the desktop meant every
 * launch after the first started as a guest. This renews it on start and then on a timer, and
 * writes every renewed header back to where the account's credential is stored.
 */
internal object DesktopSessionKeeper {

    private val client = desktopHttpClient {
        expectSuccess = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loop: Job? = null

    /**
     * The renewed header for [cookie], or [cookie] itself when Google had nothing new to say.
     * Never throws: a failed renewal leaves the session exactly as it was.
     */
    suspend fun rotate(cookie: String): String {
        var current = cookie
        for (endpoint in ROTATE_ENDPOINTS) {
            current = runCatching {
                val response = client.post(endpoint.url) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Origin, endpoint.origin)
                    header(HttpHeaders.Cookie, current)
                    contentType(ContentType.Application.Json)
                    setBody(ROTATE_BODY)
                }
                val updates = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                if (!response.status.isSuccess()) {
                    DesktopTrackLog.log("session: ${endpoint.label} answered ${response.status.value}")
                }
                DesktopCookieJar.merge(current, updates)
            }.getOrElse {
                DesktopTrackLog.log("session: could not reach ${endpoint.label}: ${it.message}")
                current
            }
        }
        val renewed = DesktopCookieJar.changedNames(cookie, current)
        if (renewed.isNotEmpty()) DesktopTrackLog.log("session: renewed ${renewed.joinToString()}")
        return current
    }

    /** Starts the background renewal for the session in force; a second call is a no-op. */
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                runCatching { refreshActive() }
                    .onFailure { DesktopTrackLog.log("session: background renewal failed: ${it.message}") }
            }
        }
    }

    /** Renews the active account's cookie and stores the result. */
    private suspend fun refreshActive() {
        val session = DesktopYouTubeAuth.session ?: return
        val account = DesktopAccounts.active() ?: return
        val renewed = rotate(session.cookie)
        if (renewed == session.cookie) return
        DesktopAccounts.storeRenewedCookie(account.accountId, renewed)
        DesktopYouTubeAuth.renewCookie(renewed)
    }

    private class Endpoint(val label: String, val url: String, val origin: String)

    /**
     * YouTube's own renewal first — its cookies are the ones music.youtube.com reads — then
     * Google's, whose `SIDCC` family the same header also carries.
     */
    private val ROTATE_ENDPOINTS = listOf(
        Endpoint("accounts.youtube.com", "https://accounts.youtube.com/RotateCookies", "https://accounts.youtube.com"),
        Endpoint("accounts.google.com", "https://accounts.google.com/RotateCookies", "https://accounts.google.com"),
    )

    /** The exact payload Google's own RotateCookiesPage posts. */
    private const val ROTATE_BODY = """[000,"-0000000000000000000"]"""

    /** Comfortably inside the ten-minute cadence a browser tab renews on. */
    private const val INTERVAL_MS = 9 * 60 * 1_000L

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
}
