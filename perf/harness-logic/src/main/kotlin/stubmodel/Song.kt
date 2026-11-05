package com.music.bitchord.data.model

/** The fields of :shared's Song the matcher reads; the rest of it needs the serialization plugin. */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
)

fun Song.durationMillis(): Long = durationText.durationMillis()

fun String?.durationMillis(): Long {
    val parts = this?.trim()?.takeIf { it.isNotEmpty() }?.split(":") ?: return 0L
    val numbers = parts.map { it.trim().toLongOrNull() ?: return 0L }
    val seconds = when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        3 -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        else -> return 0L
    }
    return (seconds * 1_000).coerceAtLeast(0L)
}
