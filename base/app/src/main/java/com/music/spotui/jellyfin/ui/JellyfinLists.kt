package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinArtist
import com.music.spotui.jellyfin.JellyfinPlaylist
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.navigation.TabReselect
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The "See all" pages and the browse tiles' pages, by kind (see JellyfinRoutes.list). */
@Composable
fun JellyfinListScreen(nav: NavController, kind: String) {
    val lib = JellyfinPref.libraryId
    when {
        kind == "liked" -> TrackListPage(nav, stringResource(R.string.jellyfin_liked_songs), load(kind) { JellyfinApi.favoriteTracks() },
            cover = { LikedArt(240.dp) })
        kind == "songs" -> {
            val page = load(kind) { JellyfinApi.randomTracks(lib) }
            val cover = page.value?.firstOrNull()?.let { trackCover(it, 600) }.orEmpty()
            TrackListPage(nav, stringResource(R.string.jellyfin_songs), page, cover = { Cover(cover, 240.dp) }, coverUrl = cover)
        }
        kind == "artists" -> ArtistListPage(nav)
        kind == "playlists" -> PlaylistListPage(nav)
        kind == "genres" -> GenreListPage(nav)
        kind == "account" -> AccountPage(nav)
        kind.startsWith("decade") -> {
            val d = kind.removePrefix("decade").toIntOrNull() ?: 2000
            AlbumGridPage(nav, stringResource(R.string.jellyfin_decade, d), tileColor(d / 10),
                load(kind) { JellyfinApi.albumsBy("SortName", 300, years = (d..d + 9).joinToString(",")) })
        }
        else -> {
            val (title, color) = when (kind) {
                "recent" -> R.string.jellyfin_recently_played to tileColor(6)
                "added" -> R.string.jellyfin_recently_added to tileColor(5)
                "most" -> R.string.jellyfin_most_played to tileColor(7)
                "favorites" -> R.string.jellyfin_liked_albums to tileColor(9)
                "albums" -> R.string.jellyfin_albums to tileColor(0)
                "random" -> R.string.jellyfin_made_for_you to tileColor(4)
                else -> R.string.jellyfin_rediscover to tileColor(10)
            }
            AlbumGridPage(nav, stringResource(title), color, load(kind) {
                when (kind) {
                    "recent" -> JellyfinApi.recentlyPlayedAlbums(60)
                    "added" -> JellyfinApi.albumsBy("DateCreated", 100)
                    "most" -> JellyfinApi.albumsBy("PlayCount", 100, played = true)
                    "favorites" -> JellyfinApi.albumsBy("SortName", 500, favorites = true)
                    "albums" -> JellyfinApi.albums(lib).first
                    else -> JellyfinApi.albumsBy("Random", 60)
                }
            })
        }
    }
}

@Composable
private fun ArtistListPage(nav: NavController) {
    val page = load("artists") { JellyfinApi.artists(limit = 1000) }
    SimpleListPage(nav, stringResource(R.string.jellyfin_artists), tileColor(1), page.value, page.failed) { a: JellyfinArtist ->
        ListRow(artistImage(a, 160), a.name, stringResource(R.string.jellyfin_artist), round = true) { JellyfinRoutes.artist(nav, a.id) }
    }
}

@Composable
private fun PlaylistListPage(nav: NavController) {
    val page = load("playlists") { JellyfinApi.playlists() }
    SimpleListPage(nav, stringResource(R.string.jellyfin_playlists), tileColor(3), page.value, page.failed) { p: JellyfinPlaylist ->
        ListRow(JellyfinApi.imageUrl(p.id, p.imageTag, 160), p.name, stringResource(R.string.jellyfin_songs_count, p.songCount)) {
            JellyfinRoutes.playlist(nav, p.id, p.name)
        }
    }
}

@Composable
private fun <T> SimpleListPage(nav: NavController, title: String, color: Color, items: List<T>?, failed: Boolean, row: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item { ColorHeader(title, color) }
            when {
                items == null && failed -> item { Problem() }
                items == null -> item { Loading() }
                items.isEmpty() -> item { Problem(stringResource(R.string.jellyfin_nothing_here)) }
                else -> items(items.size) { row(items[it]) }
            }
        }
        BackButton(nav)
    }
}

/** Every genre of the library as a tile, with a cover from one of its albums where one was found. */
@Composable
private fun GenreListPage(nav: NavController) {
    val page = load("genres") {
        coroutineScope {
            val all = async { JellyfinApi.genres(500) }
            val sample = async { runCatching { JellyfinApi.albumsWithGenres(400) }.getOrDefault(emptyList()) }
            val covers = genreCards(sample.await()).associateBy { it.id }
            all.await().map { g -> GenreCard(g.id, g.name, covers[g.id]?.cover ?: JellyfinApi.imageUrl(g.imageItemId, g.imageTag), covers[g.id]?.albums ?: 0) }
        }
    }
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item { ColorHeader(stringResource(R.string.jellyfin_genres), tileColor(2)) }
            val g = page.value
            when {
                g == null && page.failed -> item { Problem() }
                g == null -> item { Loading() }
                else -> item {
                    TileGrid(g.size, tall = true) { i, m ->
                        BrowseTile(g[i].name, tileColor(g[i].name), g[i].cover, m) { JellyfinRoutes.genre(nav, g[i].id, g[i].name) }
                    }
                }
            }
        }
        BackButton(nav)
    }
}

/** The badge's page: who is signed in, to which server, which music library, and Sign out. */
@Composable
private fun AccountPage(nav: NavController) {
    val context = LocalContext.current
    val libs = load("libs") { JellyfinApi.libraries() }
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        Column(Modifier.fillMaxSize()) {
            ColorHeader(JellyfinPref.userName.ifBlank { stringResource(R.string.jellyfin_title) }, tileColor(1))
            Text(JellyfinPref.server, color = AppMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
            libs.value?.takeIf { it.size > 1 }?.let { all ->
                SectionTitle(stringResource(R.string.jellyfin_music_library))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in all) Chip(l.name, l.id == JellyfinPref.libraryId) { JellyfinPref.pickLibrary(context, l.id) }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.jellyfin_sign_out), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(AppText)
                    .pressable(0.96f) { JellyfinPref.signOut(context) }.padding(horizontal = 28.dp, vertical = 12.dp))
        }
        BackButton(nav)
    }
}

private enum class LibChip { PLAYLISTS, ALBUMS, ARTISTS }

private class Shelf(val playlists: List<JellyfinPlaylist>, val artists: List<JellyfinArtist>, val albums: List<JellyfinAlbum>, val favorites: Boolean)

/**
 * Your Library: Liked Songs, the server's playlists, the artists followed and albums liked
 * (all the albums when nothing is liked yet); the chips narrow it to playlists, albums or artists.
 */
@Composable
fun JellyfinLibraryScreen(nav: NavController) {
    var chip by rememberSaveable { mutableStateOf<LibChip?>(null) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { TabReselect.library.collect { list.animateScrollToItem(0) } }
    val lib = JellyfinPref.libraryId
    val shelf = load(chip, refresh, lib) {
        coroutineScope {
            when (chip) {
                LibChip.PLAYLISTS -> Shelf(JellyfinApi.playlists(), emptyList(), emptyList(), false)
                LibChip.ARTISTS -> Shelf(emptyList(), JellyfinApi.artists(limit = 1000), emptyList(), false)
                LibChip.ALBUMS -> Shelf(emptyList(), emptyList(), JellyfinApi.albums(lib).first, false)
                null -> {
                    val pl = async { runCatching { JellyfinApi.playlists() }.getOrDefault(emptyList()) }
                    val ar = async { runCatching { JellyfinApi.artists(favorites = true, limit = 500) }.getOrDefault(emptyList()) }
                    val al = async { JellyfinApi.albumsBy("SortName", 500, favorites = true) }
                    val liked = al.await()
                    val artists = ar.await()
                    if (liked.isEmpty() && artists.isEmpty()) Shelf(pl.await(), emptyList(), JellyfinApi.albums(lib).first, false)
                    else Shelf(pl.await(), artists, liked, true)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(34.dp) { JellyfinRoutes.list(nav, "account") }
                    Text(stringResource(R.string.jellyfin_your_library), color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
            item(key = "chips") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((c, label) in listOf(LibChip.PLAYLISTS to R.string.jellyfin_playlists, LibChip.ALBUMS to R.string.jellyfin_albums,
                        LibChip.ARTISTS to R.string.jellyfin_artists)) {
                        Chip(stringResource(label), chip == c) { chip = if (chip == c) null else c }
                    }
                }
            }
            if (chip == null) item(key = "liked") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().pressable(0.98f) { JellyfinRoutes.list(nav, "liked") }.padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(4.dp))) { LikedArt(64.dp) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.jellyfin_liked_songs), color = AppText, fontSize = 16.sp)
                        Text(stringResource(R.string.jellyfin_playlist), color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            val s = shelf.value
            when {
                s == null && shelf.failed -> item { Problem() }
                s == null -> item { Loading() }
                else -> {
                    items(s.playlists.size, key = { "p" + s.playlists[it].id }) { i ->
                        val p = s.playlists[i]
                        ListRow(JellyfinApi.imageUrl(p.id, p.imageTag, 160), p.name,
                            listOf(stringResource(R.string.jellyfin_playlist), stringResource(R.string.jellyfin_songs_count, p.songCount)).joinToString(" • ")) {
                            JellyfinRoutes.playlist(nav, p.id, p.name)
                        }
                    }
                    items(s.artists.size, key = { "r" + s.artists[it].id }) { i ->
                        val a = s.artists[i]
                        ListRow(artistImage(a, 160), a.name, stringResource(R.string.jellyfin_artist), round = true) { JellyfinRoutes.artist(nav, a.id) }
                    }
                    items(s.albums.size, key = { "a" + s.albums[it].id }) { i ->
                        val a = s.albums[i]
                        ListRow(albumCover(a, 160), a.name, listOf(stringResource(R.string.jellyfin_album), a.artist).filter { it.isNotBlank() }.joinToString(" • ")) {
                            JellyfinRoutes.album(nav, a.id)
                        }
                    }
                    if (s.playlists.isEmpty() && s.artists.isEmpty() && s.albums.isEmpty()) item { Problem(stringResource(R.string.jellyfin_nothing_here)) }
                }
            }
        }
    }
}
