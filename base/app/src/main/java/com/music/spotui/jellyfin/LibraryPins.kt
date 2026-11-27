package com.music.spotui.jellyfin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Items pinned to the top of Your Library (an artist for now), as the official "Pin artist": kept
 * on this phone, newest pin first.
 */
object LibraryPins {
    private const val PREFS = "jellyfin_library_pins"
    private const val KEY = "pins"

    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The pinned ids, newest first. */
    fun all(c: Context = com.music.spotui.di.Graph.app): List<String> {
        @Suppress("UNUSED_VARIABLE") val v = version
        return prefs(c).getString(KEY, "").orEmpty().split(',').filter { it.isNotBlank() }
    }

    fun isPinned(id: String): Boolean = id in all()

    fun set(c: Context, id: String, pinned: Boolean) {
        val now = all(c).filter { it != id }
        val next = if (pinned) listOf(id) + now else now
        prefs(c).edit().putString(KEY, next.joinToString(",")).apply()
        version++
    }
}
