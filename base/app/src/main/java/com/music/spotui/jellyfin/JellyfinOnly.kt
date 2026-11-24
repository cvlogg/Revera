package com.music.spotui.jellyfin

import android.content.Context

/**
 * Revera as a Jellyfin player only: the app opens on the Jellyfin sign-in instead of Spotify's,
 * and Home, Search, Your Library and the artist and album pages are the Jellyfin server's
 * (see [com.music.spotui.jellyfin.ui]). Nothing plays from Spotify: no Spotify account is
 * signed in and no page offers its catalogue.
 *
 * The Spotify code stays in the build, unreached, so upstream changes still merge; [ON] false
 * brings the earlier app back.
 */
object JellyfinOnly {
    const val ON = true

    /** At launch: no music account (no Spotify sign-in, no listen sync), the onboarding marked done, Jellyfin on. */
    fun prepare(context: Context) {
        if (!ON) return
        com.music.spotui.data.preferences.AccountProviderPref.set(context, app.myspoti.api.account.AccountProviders.NONE)
        com.music.spotui.ui.screens.Setup.setAccountSeen(context)
        if (!JellyfinPref.on) JellyfinPref.set(context, true)
        // The Classic look: its bottom bar and pages are the ones the Jellyfin screens are drawn for.
        val look = com.music.spotui.data.preferences.LookPref
        if (look.look != com.music.spotui.data.preferences.Look.CLASSIC) look.set(context, com.music.spotui.data.preferences.Look.CLASSIC)
        look.setOffered(context)
    }
}
