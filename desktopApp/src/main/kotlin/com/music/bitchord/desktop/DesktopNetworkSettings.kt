package com.music.bitchord.desktop

import com.music.bitchord.data.net.DpiBypass
import com.music.bitchord.data.net.SecureDns
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app works its own way round a network that throttles or misdirects YouTube, and which
 * DNS server it looks names up with.
 */
internal object DesktopNetworkSettings {

    private val persistence by lazy { DesktopPersistence() }

    private val _bypass = MutableStateFlow(persistence.boolean(KEY_BYPASS, inRussia()))

    /**
     * On by default where YouTube is known to be slowed — Russia, judged by the system's region
     * or time zone — and off elsewhere, where there is nothing to get round.
     */
    val bypass: StateFlow<Boolean> = _bypass

    fun setBypass(value: Boolean) {
        persistence.saveBoolean(KEY_BYPASS, value)
        _bypass.value = value
        DpiBypass.enabled = value
    }

    private val _dns = MutableStateFlow(
        runCatching { SecureDns.Provider.valueOf(persistence.string(KEY_DNS, SecureDns.Provider.SYSTEM.name)) }
            .getOrDefault(SecureDns.Provider.SYSTEM),
    )

    /** The DNS server host names are looked up with — the system's unless one is picked. */
    val dns: StateFlow<SecureDns.Provider> = _dns

    fun setDns(value: SecureDns.Provider) {
        persistence.saveString(KEY_DNS, value.name)
        _dns.value = value
        SecureDns.provider = value
    }

    private val _customDns = MutableStateFlow(persistence.string(KEY_CUSTOM_DNS, ""))

    /** The server for [SecureDns.Provider.CUSTOM]. */
    val customDns: StateFlow<String> = _customDns

    fun setCustomDns(value: String) {
        persistence.saveString(KEY_CUSTOM_DNS, value)
        _customDns.value = value
        SecureDns.customUrl = value
    }

    /** Hands the saved choices to the network code; at launch, before the first request. */
    fun apply() {
        DpiBypass.enabled = _bypass.value
        SecureDns.customUrl = _customDns.value
        SecureDns.provider = _dns.value
    }

    private fun inRussia(): Boolean {
        if (Locale.getDefault().country.equals("RU", ignoreCase = true)) return true
        val zone = runCatching { ZoneId.systemDefault().id }.getOrDefault("")
        return zone in RUSSIAN_ZONES
    }

    private val RUSSIAN_ZONES = setOf(
        "Europe/Moscow", "Europe/Kaliningrad", "Europe/Samara", "Europe/Volgograd", "Europe/Saratov",
        "Europe/Ulyanovsk", "Europe/Astrakhan", "Europe/Kirov", "Asia/Yekaterinburg", "Asia/Omsk",
        "Asia/Novosibirsk", "Asia/Barnaul", "Asia/Tomsk", "Asia/Novokuznetsk", "Asia/Krasnoyarsk",
        "Asia/Irkutsk", "Asia/Chita", "Asia/Yakutsk", "Asia/Khandyga", "Asia/Vladivostok",
        "Asia/Ust-Nera", "Asia/Magadan", "Asia/Sakhalin", "Asia/Srednekolymsk", "Asia/Kamchatka",
        "Asia/Anadyr", "W-SU",
    )

    private const val KEY_BYPASS = "net_dpi_bypass"
    private const val KEY_DNS = "net_dns_provider"
    private const val KEY_CUSTOM_DNS = "net_dns_custom_url"
}
