package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.OkHttpClient

/**
 * The app's own OkHttp client under Ktor, for every Ktor client on the desktop. Ktor's CIO engine
 * reads no proxy settings at all: behind a VPN that sets Windows' proxy, the account check after
 * signing in, the session keeper, scrobbling and Listen together all went round the VPN and
 * never reached YouTube. This one goes the way the rest of the app does — Windows' proxy, and the
 * DNS and bypass settings. Redirects are left to Ktor, as they were with CIO.
 */
private val ktorOkHttp: OkHttpClient by lazy {
    Http.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
}

internal fun desktopHttpClient(block: HttpClientConfig<OkHttpConfig>.() -> Unit = {}): HttpClient =
    HttpClient(OkHttp) {
        engine { preconfigured = ktorOkHttp }
        block()
    }
