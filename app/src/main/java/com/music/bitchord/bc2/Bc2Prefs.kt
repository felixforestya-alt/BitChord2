package com.music.bitchord.bc2

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Where BitChord²'s own features keep their settings on the phone — My Jam, Home's shelves — in a
 * file of their own, so nothing of the original app's settings is touched or read.
 */
internal object Bc2Prefs {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("bitchord2", Context.MODE_PRIVATE)
    }

    fun string(key: String, default: String = ""): String = prefs?.getString(key, default) ?: default
    fun boolean(key: String, default: Boolean): Boolean = prefs?.getBoolean(key, default) ?: default
    fun putString(key: String, value: String) { prefs?.edit()?.putString(key, value)?.apply() }
    fun putBoolean(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }
}

/** [ru] in a Russian-speaking phone's language, [en] in any other — BitChord²'s own words. */
internal fun bc2(ru: String, en: String): String = if (Locale.getDefault().language == "ru") ru else en
