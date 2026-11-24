package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinArtist
import com.music.spotui.jellyfin.JellyfinPlayback
import com.music.spotui.jellyfin.JellyfinTrack
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The colour a cover gives, for a page's gradient (grey until it is read). */
@Composable
internal fun coverTint(url: String): Color {
    val context = LocalContext.current
    var tint by remember(url) { mutableStateOf(com.music.spotui.di.Palette.cachedFirst(url) ?: Color(0xFF404040)) }
    LaunchedEffect(url) { if (url.isNotBlank()) com.music.spotui.di.Palette().extractFirstColorFromImageUrl(context, url) { tint = it } }
    return tint
}

/** A list page's top: the cover on its colour, the name, the lines under it, then the actions row. */
@Composable
internal fun CollectionHeader(
    cover: @Composable () -> Unit, tint: Color, title: String, lines: @Composable () -> Unit,
    actions: @Composable () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, AppBackground)))
            .statusBarsPadding().padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { cover() }
        Text(title, color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
            lineHeight = 28.sp, modifier = Modifier.padding(top = 18.dp))
        lines()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(Modifier.weight(1f))
            ShuffleButton(onShuffle)
            Spacer(Modifier.width(20.dp))
            PlayCircle(onClick = onPlay)
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = AppMuted, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(0.9f, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
    }
}

private class AlbumPage(val album: JellyfinAlbum, val tracks: List<JellyfinTrack>, val artist: JellyfinArtist?)

/** An album: its cover, name, artist (to their page), year, like, queue, shuffle, play, and its songs. */
@Composable
fun JellyfinAlbumScreen(nav: NavController, id: String) {
    val scope = rememberCoroutineScope()
    val app = com.music.spotui.di.Graph.app
    val page = load(id) {
        coroutineScope {
            val album = async { JellyfinApi.album(id) }
            val tracks = async { JellyfinApi.albumTracks(id) }
            val a = album.await() ?: throw java.io.IOException("No album")
            val artist = a.artistId.takeIf { it.isNotBlank() }?.let { runCatching { JellyfinApi.artist(it) }.getOrNull() }
            AlbumPage(a, tracks.await(), artist)
        }
    }
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        val p = page.value
        when {
            p == null && page.failed -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Problem() }
            p == null -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Loading() }
            else -> {
                val a = p.album
                val cover = albumCover(a, 600)
                var liked by remember(a.id) { mutableStateOf(a.favorite) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
                    item(key = "head") {
                        CollectionHeader(
                            cover = { Cover(cover, 240.dp) }, tint = coverTint(cover), title = a.name,
                            lines = {
                                if (a.artist.isNotBlank()) Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 10.dp).pressable(0.97f) { JellyfinRoutes.artist(nav, a.artistId) },
                                ) {
                                    p.artist?.let { Cover(artistImage(it, 100), 24.dp, CircleShape); Spacer(Modifier.width(8.dp)) }
                                    Text(a.artist, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(listOf(stringResource(R.string.jellyfin_album), a.year.takeIf { it > 0 }?.toString().orEmpty())
                                    .filter { it.isNotBlank() }.joinToString(" • "), color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            },
                            actions = {
                                ActionIcon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    stringResource(if (liked) R.string.jellyfin_unlike else R.string.jellyfin_like), if (liked) AppAccent else AppMuted) {
                                    val on = !liked; liked = on
                                    scope.launch { runCatching { JellyfinApi.setFavorite(a.id, on) }.onFailure { liked = !on } }
                                }
                                ActionIcon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.jellyfin_add_to_queue)) {
                                    p.tracks.forEach { JellyfinPlayback.enqueue(it, next = false) }
                                    android.widget.Toast.makeText(app, app.getString(R.string.jellyfin_added_to_queue, a.name), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPlay = { JellyfinPlayback.play(p.tracks, 0) },
                            onShuffle = { if (p.tracks.isNotEmpty()) JellyfinPlayback.play(p.tracks, p.tracks.indices.random(), shuffle = true) },
                        )
                    }
                    items(p.tracks.size, key = { p.tracks[it].id }) { i ->
                        TrackRow(p.tracks[i], nav, showCover = false) { JellyfinPlayback.play(p.tracks, i) }
                    }
                    if (p.tracks.isNotEmpty()) item(key = "foot") {
                        val minutes = p.tracks.sumOf { it.durationMs } / 60_000
                        Text(listOf(LocalContext.current.resources.getQuantityString(R.plurals.jellyfin_songs_plural, p.tracks.size, p.tracks.size),
                            stringResource(R.string.jellyfin_minutes, minutes)).joinToString(" • "),
                            color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        BackButton(nav)
    }
}

/** A playlist of the server: its songs, shuffle and play. */
@Composable
fun JellyfinPlaylistScreen(nav: NavController, id: String, name: String) {
    val page = load(id) { JellyfinApi.playlistTracks(id) }
    // The playlist's own picture needs its tag, which the link does not carry: its first song's cover.
    val cover = page.value?.firstOrNull()?.let { trackCover(it, 600) }.orEmpty()
    TrackListPage(nav, name, page, cover = { Cover(cover, 240.dp) }, coverUrl = cover)
}

/** A page of songs under a header: Liked Songs, a playlist, the most played. */
@Composable
internal fun TrackListPage(nav: NavController, title: String, page: Loaded<List<JellyfinTrack>>, cover: @Composable () -> Unit, coverUrl: String = "") {
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        val tracks = page.value
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item(key = "head") {
                CollectionHeader(
                    cover = cover, tint = if (coverUrl.isNotBlank()) coverTint(coverUrl) else Color(0xFF3B2A6B), title = title,
                    lines = {
                        tracks?.let {
                            Text(LocalContext.current.resources.getQuantityString(R.plurals.jellyfin_songs_plural, it.size, it.size),
                                color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    },
                    actions = {},
                    onPlay = { tracks?.let { JellyfinPlayback.play(it, 0) } },
                    onShuffle = { tracks?.takeIf { it.isNotEmpty() }?.let { JellyfinPlayback.play(it, it.indices.random(), shuffle = true) } },
                )
            }
            when {
                tracks == null && page.failed -> item { Problem() }
                tracks == null -> item { Loading() }
                tracks.isEmpty() -> item { Problem(stringResource(R.string.jellyfin_nothing_here)) }
                else -> items(tracks.size, key = { tracks[it].id + "#" + it }) { i ->
                    TrackRow(tracks[i], nav) { JellyfinPlayback.play(tracks, i) }
                }
            }
        }
        BackButton(nav)
    }
}

/** A genre: its colour and name, a shuffle of its songs, and its albums. */
@Composable
fun JellyfinGenreScreen(nav: NavController, id: String, name: String) {
    val scope = rememberCoroutineScope()
    val page = load(id) { JellyfinApi.albumsBy("SortName", 200, genreId = id) }
    val shuffle: () -> Unit = {
        scope.launch { runCatching { JellyfinApi.randomTracks(com.music.spotui.jellyfin.JellyfinPref.libraryId, genreId = id) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { JellyfinPlayback.play(it, 0) } }
    }
    AlbumGridPage(nav, name, tileColor(name), page, onPlay = shuffle)
}

/** A page of albums two by two under a coloured header (a genre, a decade, a whole list). */
@Composable
internal fun AlbumGridPage(nav: NavController, title: String, color: Color, page: Loaded<List<JellyfinAlbum>>, onPlay: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        val albums = page.value
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item(key = "head") { ColorHeader(title, color, onPlay) }
            when {
                albums == null && page.failed -> item { Problem() }
                albums == null -> item { Loading() }
                albums.isEmpty() -> item { Problem(stringResource(R.string.jellyfin_nothing_here)) }
                else -> items((albums.size + 1) / 2, key = { "row$it" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        for (a in albums.drop(r * 2).take(2)) {
                            Box(Modifier.weight(1f)) {
                                androidx.compose.foundation.layout.BoxWithConstraints {
                                    CoverCard(albumCover(a), a.name, a.artist, maxWidth - 7.dp) { JellyfinRoutes.album(nav, a.id) }
                                }
                            }
                        }
                        if (albums.drop(r * 2).size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        BackButton(nav)
    }
}

/** A coloured header with a big title, and a play button when the page has one. */
@Composable
internal fun ColorHeader(title: String, color: Color, onPlay: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(color, AppBackground))).statusBarsPadding()
        .padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(72.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 16.dp)) {
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp,
                letterSpacing = (-0.6).sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onPlay != null) PlayCircle(onClick = onPlay)
        }
    }
}
