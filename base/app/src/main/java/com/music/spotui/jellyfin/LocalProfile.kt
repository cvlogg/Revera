package com.music.spotui.jellyfin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The user as this phone shows them: a name and a picture of their choosing, kept on the phone
 * for the signed-in Jellyfin user (the server's account is not changed). Without them, the
 * server's user name and its first letter on green.
 */
object LocalProfile {
    private const val PREFS = "jellyfin_local_profile"

    /** Bumped at each change: every badge and name reads them again. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key() = "name_" + JellyfinPref.userId

    /** The id the picture is kept under (see [ArtistPictures]). */
    val pictureId: String get() = "profile-" + JellyfinPref.userId

    /** The name shown: the one chosen on the phone, else the server's user name. */
    val name: String get() {
        @Suppress("UNUSED_VARIABLE") val v = version
        return prefs(com.music.spotui.di.Graph.app).getString(key(), null)?.takeIf { it.isNotBlank() } ?: JellyfinPref.userName
    }

    /** The picture chosen, as an address the image loader reads; null with none. */
    val picture: String? get() {
        @Suppress("UNUSED_VARIABLE") val v = version
        return ArtistPictures.of(pictureId)
    }

    /** A blank name goes back to the server's. */
    fun setName(c: Context, value: String) {
        prefs(c).edit().apply { if (value.isBlank()) remove(key()) else putString(key(), value.trim()) }.apply()
        version++
    }
}
