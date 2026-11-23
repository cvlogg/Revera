package com.music.spotui.jellyfin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Jellyfin" (Settings > Library): on adds the pinned Library row under Local Files; the row's
 * screen signs in to the user's own Jellyfin server and plays its music library through
 * [com.music.spotui.di.LocalPlayer], the way Local Files and Live Radio do. Off by default.
 *
 * Its own prefs file, like the radio module's, so the whole `jellyfin` package stays removable.
 * The access token is the server's own (revocable from the server's Dashboard > Devices); the
 * password is never stored.
 */
object JellyfinPref {
    private const val PREF = "jellyfin_prefs"
    private const val KEY_ON = "enabled"
    private const val KEY_SERVER = "server"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_TOKEN = "token"
    private const val KEY_LIBRARY_ID = "library_id"
    private const val KEY_DEVICE_ID = "device_id"

    /** The pinned Library row's sentinel id (parallel to RadioFeaturePref.LIBRARY_ENTRY_ID). */
    const val LIBRARY_ENTRY_ID = "jellyfin"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var on by mutableStateOf(false)
        private set

    /** The server's base address, without a trailing slash (e.g. "http://192.168.1.10:8096"). */
    var server by mutableStateOf("")
        private set
    var userId by mutableStateOf("")
        private set
    var userName by mutableStateOf("")
        private set
    var token by mutableStateOf("")
        private set
    /** The music library ("folder") picked on the server; "" until one is picked. */
    var libraryId by mutableStateOf("")
        private set

    /** This install's id for the server's device list: made once, kept across sign-ins. */
    lateinit var deviceId: String
        private set

    val signedIn: Boolean get() = server.isNotBlank() && token.isNotBlank() && userId.isNotBlank()

    fun init(context: Context) {
        val p = prefs(context)
        on = p.getBoolean(KEY_ON, false)
        server = p.getString(KEY_SERVER, "").orEmpty()
        userId = p.getString(KEY_USER_ID, "").orEmpty()
        userName = p.getString(KEY_USER_NAME, "").orEmpty()
        token = p.getString(KEY_TOKEN, "").orEmpty()
        libraryId = p.getString(KEY_LIBRARY_ID, "").orEmpty()
        deviceId = p.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
            p.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    fun set(context: Context, value: Boolean) {
        on = value
        prefs(context).edit().putBoolean(KEY_ON, value).apply()
        if (!value) JellyfinPlayback.stopIfPlaying()
    }

    fun signedIn(context: Context, server: String, userId: String, userName: String, token: String) {
        this.server = server; this.userId = userId; this.userName = userName; this.token = token
        libraryId = ""
        prefs(context).edit().putString(KEY_SERVER, server).putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName).putString(KEY_TOKEN, token).remove(KEY_LIBRARY_ID).apply()
    }

    fun pickLibrary(context: Context, id: String) {
        libraryId = id
        prefs(context).edit().putString(KEY_LIBRARY_ID, id).apply()
    }

    /** Forgets the session here (the server keeps the device until it is removed there, or signs it out itself). */
    fun signOut(context: Context) {
        JellyfinPlayback.stopIfPlaying()
        // The server address stays, so signing in again only asks for the user name and password.
        userId = ""; userName = ""; token = ""; libraryId = ""
        prefs(context).edit().remove(KEY_USER_ID).remove(KEY_USER_NAME).remove(KEY_TOKEN).remove(KEY_LIBRARY_ID).apply()
    }
}
