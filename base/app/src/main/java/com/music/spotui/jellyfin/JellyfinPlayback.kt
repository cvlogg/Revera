package com.music.spotui.jellyfin

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.di.LocalPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Jellyfin songs as [LocalTrack]s, played by [LocalPlayer] like Local Files: one ExoPlayer
 * playlist, so the next song is buffered while the one before plays and starts on its first
 * sample (gapless), and the mini player, notification, lock screen, queue, crossfade and AutoMix
 * work as they do for the phone's files.
 *
 * It also tells the server what plays ([Reporter]): its dashboard's "now playing", and the play
 * counts and dates that the Home page's "recently played" and the artist page's "Popular" read.
 */
object JellyfinPlayback {
    /** Ids under -2^33: below MediaStore's (>= 0), the downloads' (-1 to -2^31) and Live Radio's (-2^32 …). */
    fun stableId(trackId: String): Long = -(1L shl 33) - (trackId.hashCode().toLong() and 0x7fffffffL)

    /** The synthetic song id LocalPlayer/SongPlayer show [t] under, to match against Graph.currentSongState. */
    fun songIdFor(t: JellyfinTrack): Int = ("local:" + stableId(t.id)).hashCode() and 0x7fffffff

    /** Every Jellyfin song handed to the player, by its LocalTrack id: the player's artist and album links read it. */
    private val known = ConcurrentHashMap<Long, JellyfinTrack>()

    /** The Jellyfin song [t] is, if it is one. */
    fun of(t: LocalTrack?): JellyfinTrack? = t?.let { known[it.id] }

    /** The Jellyfin song playing now, if one is. */
    fun current(): JellyfinTrack? = if (LocalPlayer.active.value) of(LocalPlayer.current()) else null

    fun localTrack(t: JellyfinTrack): LocalTrack {
        val id = stableId(t.id)
        known[id] = t
        return LocalTrack(
            id = id,
            uri = Uri.parse(JellyfinApi.streamUrl(t.id)),
            title = t.title, artist = t.artist, album = t.album, durationMs = t.durationMs,
            artUri = JellyfinApi.imageUrl(t.imageItemId, t.imageTag, 800).takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: Uri.parse("android.resource://${com.music.spotui.di.Graph.app.packageName}/drawable/local_files_cover"),
            folder = "jellyfin/" + t.albumId,
            trackNo = t.trackNo,
        )
    }

    fun play(list: List<JellyfinTrack>, index: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        LocalPlayer.play(list.map(::localTrack), index.coerceIn(0, list.lastIndex), shuffle = shuffle)
        Reporter.start()
    }

    fun enqueue(t: JellyfinTrack, next: Boolean) { LocalPlayer.enqueue(localTrack(t), next); Reporter.start() }

    /** Whether what LocalPlayer plays now is a Jellyfin song. */
    fun playing(): Boolean = LocalPlayer.active.value && (LocalPlayer.current()?.folder?.startsWith("jellyfin/") == true)

    /** Signed out or turned off: a Jellyfin song playing stops (its address holds the token that just went). */
    fun stopIfPlaying() { if (playing()) LocalPlayer.stop() }

    /**
     * Reads the player every few seconds while Jellyfin songs play, and reports to the server: a
     * song started, its position every half minute, it stopped; past half its length, one play
     * counted. Network failures are ignored (the music plays on; the server misses a count).
     */
    private object Reporter {
        private const val TICK_MS = 5_000L
        private const val PROGRESS_MS = 30_000L
        private val main = Handler(Looper.getMainLooper())
        private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var running = false
        private var item: String? = null
        private var lastPos = 0L
        private var counted = false
        private var lastProgress = 0L

        private fun send(block: () -> Unit) { io.launch { runCatching(block) } }

        fun start() {
            if (running) return
            running = true
            main.post(tick)
        }

        private val tick: Runnable = object : Runnable {
            override fun run() {
                val now = current()
                val pos = LocalPlayer.position()
                if (now?.id != item) {
                    item?.let { old -> val at = lastPos; send { JellyfinApi.reportStopped(old, at) } }
                    item = now?.id; counted = false; lastProgress = System.currentTimeMillis()
                    now?.let { t -> send { JellyfinApi.reportStart(t.id, pos) } }
                } else if (now != null) {
                    if (!counted && now.durationMs > 0 && pos >= now.durationMs / 2) {
                        counted = true
                        send { JellyfinApi.markPlayed(now.id) }
                    }
                    if (System.currentTimeMillis() - lastProgress >= PROGRESS_MS) {
                        lastProgress = System.currentTimeMillis()
                        val paused = !LocalPlayer.isPlaying()
                        send { JellyfinApi.reportProgress(now.id, pos, paused) }
                    }
                }
                lastPos = pos
                if (item == null && !playing()) { running = false; return }
                main.postDelayed(this, TICK_MS)
            }
        }
    }
}
