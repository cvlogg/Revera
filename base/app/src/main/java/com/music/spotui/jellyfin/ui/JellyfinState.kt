package com.music.spotui.jellyfin.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinAuthException
import com.music.spotui.jellyfin.JellyfinLibrary
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.jellyfin.JellyfinTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** What the Jellyfin screen shows: the sign-in, the libraries, the albums, an album open, a search. */
@Stable
internal class JellyfinState(private val scope: CoroutineScope) {
    enum class Problem { NONE, UNREACHABLE, WRONG_LOGIN, NO_LIBRARY }

    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf(Problem.NONE)
        private set

    var libraries by mutableStateOf<List<JellyfinLibrary>?>(null)
        private set
    var albums by mutableStateOf<List<JellyfinAlbum>>(emptyList())
        private set
    private var albumsTotal = Int.MAX_VALUE
    val moreAlbums: Boolean get() = albums.size < albumsTotal

    var album by mutableStateOf<JellyfinAlbum?>(null)
        private set
    var albumTracks by mutableStateOf<List<JellyfinTrack>?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var foundTracks by mutableStateOf<List<JellyfinTrack>?>(null)
        private set
    var foundAlbums by mutableStateOf<List<JellyfinAlbum>?>(null)
        private set

    private var searchJob: Job? = null
    private var pageJob: Job? = null

    /** Runs [block], turning a failure into [problem] (a refused token signs out). */
    private suspend fun guarded(context: Context, block: suspend () -> Unit) {
        try {
            block()
            problem = Problem.NONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyfinAuthException) {
            if (JellyfinPref.signedIn) JellyfinPref.signOut(context)
            problem = Problem.WRONG_LOGIN
        } catch (e: IOException) {
            problem = Problem.UNREACHABLE
        } catch (e: RuntimeException) {
            // An answer that is not the JSON expected (a proxy page, another service on that port).
            problem = Problem.UNREACHABLE
        }
    }

    fun signIn(context: Context, server: String, user: String, password: String) {
        if (busy) return
        scope.launch {
            busy = true
            guarded(context) {
                val (s, id, name, token) = JellyfinApi.signIn(server, user, password)
                JellyfinPref.signedIn(context, s, id, name, token)
                loadLibraries(context)
            }
            busy = false
        }
    }

    fun signOut(context: Context) {
        JellyfinPref.signOut(context)
        libraries = null; albums = emptyList(); albumsTotal = Int.MAX_VALUE
        album = null; albumTracks = null; query = ""; foundTracks = null; foundAlbums = null
        problem = Problem.NONE
    }

    /** At the screen's start, signed in: the libraries, then the albums of the one picked. */
    fun start(context: Context) {
        if (!JellyfinPref.signedIn || libraries != null) return
        scope.launch {
            busy = true
            guarded(context) { loadLibraries(context) }
            busy = false
        }
    }

    private suspend fun loadLibraries(context: Context) {
        val libs = JellyfinApi.libraries()
        libraries = libs
        if (libs.isEmpty()) { problem = Problem.NO_LIBRARY; return }
        // The one picked before, else the only one (or the first: the chips let another be picked).
        if (libs.none { it.id == JellyfinPref.libraryId }) JellyfinPref.pickLibrary(context, libs.first().id)
        albums = emptyList(); albumsTotal = Int.MAX_VALUE
        loadAlbumsPage()
    }

    fun pickLibrary(context: Context, id: String) {
        if (id == JellyfinPref.libraryId) return
        JellyfinPref.pickLibrary(context, id)
        albums = emptyList(); albumsTotal = Int.MAX_VALUE
        pageJob?.cancel()
        pageJob = scope.launch { busy = true; guarded(context) { loadAlbumsPage() }; busy = false }
        if (query.isNotBlank()) search(context, query)
    }

    private suspend fun loadAlbumsPage() {
        val (page, total) = JellyfinApi.albums(JellyfinPref.libraryId, start = albums.size)
        albums = albums + page
        albumsTotal = if (page.isEmpty()) albums.size else total
    }

    /** The end of the album list was reached: the next page. */
    fun more(context: Context) {
        if (!moreAlbums || pageJob?.isActive == true || busy) return
        pageJob = scope.launch { guarded(context) { loadAlbumsPage() } }
    }

    fun open(context: Context, a: JellyfinAlbum) {
        album = a; albumTracks = null
        scope.launch { guarded(context) { albumTracks = JellyfinApi.albumTracks(a.id) } }
    }

    fun closeAlbum() { album = null; albumTracks = null }

    fun search(context: Context, q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) { foundTracks = null; foundAlbums = null; return }
        searchJob = scope.launch {
            delay(350)
            guarded(context) {
                val lib = JellyfinPref.libraryId
                foundAlbums = JellyfinApi.albums(lib, search = q).first
                foundTracks = JellyfinApi.searchTracks(lib, q)
            }
        }
    }

    fun shuffleLibrary(context: Context) {
        scope.launch {
            guarded(context) {
                val list = JellyfinApi.randomTracks(JellyfinPref.libraryId)
                // Already in a random order: played from the first, no shuffle on top of it.
                com.music.spotui.jellyfin.JellyfinPlayback.play(list, 0)
            }
        }
    }
}
