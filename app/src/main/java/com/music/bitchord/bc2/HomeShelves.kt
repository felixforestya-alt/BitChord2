package com.music.bitchord.bc2

import com.music.bitchord.data.model.HomeShelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which of the feed's shelves the listener has taken off Home, and the order the rest come in —
 * BitChord²'s Home settings, carried over from the desktop. Kept by the shelf's title, which is all
 * a shelf has that stays the same from one load of the feed to the next.
 */
internal object HomeShelves {

    private val _hidden = MutableStateFlow(
        Bc2Prefs.string(KEY_HIDDEN).split('\n').map(String::trim).filter(String::isNotEmpty).toSet(),
    )

    /** The hidden titles, as they were shown. */
    val hidden: StateFlow<Set<String>> = _hidden

    private val _order = MutableStateFlow(
        Bc2Prefs.string(KEY_ORDER).split('\n').map(String::trim).filter(String::isNotEmpty),
    )

    /** The listener's order of shelves, by title; shelves not in it follow in the feed's own order. */
    val order: StateFlow<List<String>> = _order

    fun isHidden(title: String): Boolean = keyOf(title) in _hidden.value.mapTo(HashSet(), ::keyOf)

    fun setHidden(title: String, hide: Boolean) {
        val rest = _hidden.value.filterNot { keyOf(it) == keyOf(title) }.toSet()
        saveHidden(if (hide) rest + title.trim() else rest)
    }

    fun showAll() = saveHidden(emptySet())

    /** [titles] in the listener's order. */
    fun ordered(titles: List<String>): List<String> {
        val rank = _order.value.withIndex().associate { (i, t) -> keyOf(t) to i }
        return titles.withIndex().sortedBy { (i, t) -> rank[keyOf(t)] ?: (ORDERED_LAST + i) }.map { it.value }
    }

    /** Home's shelves in exactly [titles]' order. */
    fun setOrder(titles: List<String>) {
        _order.value = titles
        Bc2Prefs.putString(KEY_ORDER, titles.joinToString("\n"))
    }

    fun resetOrder() = setOrder(emptyList())

    /** [shelves] in the listener's order, without the hidden ones. */
    fun visible(shelves: List<HomeShelf>): List<HomeShelf> {
        val keys = _hidden.value.mapTo(HashSet(), ::keyOf)
        val rank = _order.value.withIndex().associate { (i, t) -> keyOf(t) to i }
        return shelves.withIndex()
            .filterNot { keyOf(it.value.title) in keys }
            .sortedBy { (i, shelf) -> rank[keyOf(shelf.title)] ?: (ORDERED_LAST + i) }
            .map { it.value }
    }

    private fun saveHidden(value: Set<String>) {
        _hidden.value = value
        Bc2Prefs.putString(KEY_HIDDEN, value.joinToString("\n"))
    }

    private fun keyOf(title: String) = title.trim().lowercase()

    private const val KEY_HIDDEN = "home_hidden_shelves"
    private const val KEY_ORDER = "home_shelf_order"
    private const val ORDERED_LAST = 10_000
}
