package com.music.spotui.jellyfin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * A playlist's description, kept on this phone: the server stores one only through a request
 * reserved to administrators. (Its picture, chosen on the phone too, is [ArtistPictures]'s, by id.)
 */
object PlaylistNotes {
    private const val PREFS = "jellyfin_playlist_notes"

    /** Bumped at each change: pages read the descriptions again. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun description(c: Context, id: String): String {
        @Suppress("UNUSED_VARIABLE") val v = version
        return prefs(c).getString(id, "").orEmpty()
    }

    fun setDescription(c: Context, id: String, text: String) {
        prefs(c).edit().apply { if (text.isBlank()) remove(id) else putString(id, text.trim()) }.apply()
        version++
    }

    fun forget(c: Context, id: String) { prefs(c).edit().remove(id).apply(); version++ }
}
