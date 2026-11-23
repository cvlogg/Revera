package com.music.spotui.jellyfin

import android.net.Uri
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.di.LocalPlayer

/**
 * Jellyfin songs as [LocalTrack]s, played by [LocalPlayer] like Local Files: one ExoPlayer
 * playlist, so the next song is buffered while the one before plays and starts on its first
 * sample (gapless), and the mini player, notification, lock screen, queue, crossfade and AutoMix
 * work as they do for the phone's files.
 */
object JellyfinPlayback {
    /** Ids under -2^33: below MediaStore's (>= 0), the downloads' (-1 to -2^31) and Live Radio's (-2^32 …). */
    fun stableId(trackId: String): Long = -(1L shl 33) - (trackId.hashCode().toLong() and 0x7fffffffL)

    /** The synthetic song id LocalPlayer/SongPlayer show [t] under, to match against Graph.currentSongState. */
    fun songIdFor(t: JellyfinTrack): Int = ("local:" + stableId(t.id)).hashCode() and 0x7fffffff

    fun localTrack(t: JellyfinTrack) = LocalTrack(
        id = stableId(t.id),
        uri = Uri.parse(JellyfinApi.streamUrl(t.id)),
        title = t.title, artist = t.artist, album = t.album, durationMs = t.durationMs,
        artUri = JellyfinApi.imageUrl(t.imageItemId, t.imageTag, 800).takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: Uri.parse("android.resource://${com.music.spotui.di.Graph.app.packageName}/drawable/local_files_cover"),
        folder = "jellyfin/" + t.albumId,
        trackNo = t.trackNo,
    )

    fun play(list: List<JellyfinTrack>, index: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        LocalPlayer.play(list.map(::localTrack), index.coerceIn(0, list.lastIndex), shuffle = shuffle)
    }

    fun enqueue(t: JellyfinTrack, next: Boolean) = LocalPlayer.enqueue(localTrack(t), next)

    /** Whether what LocalPlayer plays now is a Jellyfin song. */
    fun playing(): Boolean = LocalPlayer.active.value && (LocalPlayer.current()?.folder?.startsWith("jellyfin/") == true)

    /** Signed out or turned off: a Jellyfin song playing stops (its address holds the token that just went). */
    fun stopIfPlaying() { if (playing()) LocalPlayer.stop() }
}
