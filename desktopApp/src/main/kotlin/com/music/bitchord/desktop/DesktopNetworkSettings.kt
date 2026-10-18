package com.music.bitchord.desktop

import com.music.bitchord.data.net.DpiBypass
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the app works its own way round a network that throttles or misdirects YouTube. */
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
}
