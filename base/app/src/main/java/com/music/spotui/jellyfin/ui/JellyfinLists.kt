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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Icon
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
        kind == "liked" -> {
            val name = stringResource(R.string.jellyfin_liked_songs)
            TrackListPage(nav, name, load(kind) { JellyfinApi.favoriteTracks() }, com.music.spotui.jellyfin.JellyfinPlayback.From.liked(name),
                cover = { LikedArt(240.dp) })
        }
        kind == "songs" -> {
            val page = load(kind) { JellyfinApi.randomTracks(lib) }
            val cover = page.value?.firstOrNull()?.let { trackCover(it, 600) }.orEmpty()
            val name = stringResource(R.string.jellyfin_songs)
            TrackListPage(nav, name, page, com.music.spotui.jellyfin.JellyfinPlayback.From.list(kind, name), cover = { Cover(cover, 240.dp) }, coverUrl = cover)
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
        PlaylistRow(p, stringResource(R.string.jellyfin_songs_count, p.songCount)) { JellyfinRoutes.playlist(nav, p.id, p.name) }
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

/**
 * The badge's page, as the official profile: the picture and the name (both the phone's own, see
 * LocalProfile) with Edit, then the server signed in to, its music library and Sign out.
 */
@Composable
private fun AccountPage(nav: NavController) {
    val context = LocalContext.current
    val libs = load("libs") { JellyfinApi.libraries() }
    var editing by remember { mutableStateOf(false) }
    val picture = com.music.spotui.jellyfin.LocalProfile.picture
    val tint = if (picture != null) coverTint(picture) else tileColor(1)
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint, AppBackground)))
                .statusBarsPadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(64.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(110.dp) { editing = true }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(com.music.spotui.jellyfin.LocalProfile.name.ifBlank { stringResource(R.string.jellyfin_title) }, color = AppText,
                            fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(stringResource(R.string.jellyfin_signed_in_as, JellyfinPref.userName), color = AppMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Row(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                    Pill(stringResource(R.string.jellyfin_edit), outlined = true) { editing = true }
                }
            }
            SectionTitle(stringResource(R.string.jellyfin_server_title))
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
    if (editing) EditProfileDialog { editing = false }
}

/** "Edit profile": the picture (Change photo) and the name; the name is kept with Save. */
@Composable
private fun EditProfileDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(com.music.spotui.jellyfin.LocalProfile.name) }
    var changing by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, stringResource(R.string.jellyfin_cancel), tint = AppText)
                }
                Text(stringResource(R.string.jellyfin_edit_profile), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.jellyfin_save), color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable(0.95f) { com.music.spotui.jellyfin.LocalProfile.setName(context, name); onDismiss() }.padding(12.dp))
            }
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(150.dp) { changing = true }
                Text(stringResource(R.string.jellyfin_change_photo), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp).pressable(0.95f) { changing = true }.padding(6.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text(stringResource(R.string.jellyfin_name), color = AppMuted, fontSize = 13.sp)
                androidx.compose.foundation.text.BasicTextField(name, { name = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(com.music.spotui.ui.theme.AppAccent),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(AppMuted.copy(alpha = 0.5f)))
                Text(stringResource(R.string.jellyfin_profile_note), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
    if (changing) ChangePicture(com.music.spotui.jellyfin.LocalProfile.pictureId, stringResource(R.string.jellyfin_change_photo),
        random = null) { changing = false }
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
    var creating by remember { mutableStateOf(false) }
    if (creating) NewPlaylistDialog(onDismiss = { creating = false }) { id, name -> creating = false; JellyfinRoutes.playlist(nav, id, name) }
    val shelf = load(chip, refresh, lib, PlaylistChanges.version) {
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
                        letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    // Create, as the official app's +: a new playlist, opened once named.
                    Box(Modifier.size(44.dp).pressable(0.9f) { creating = true }, contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Add, stringResource(R.string.jellyfin_create_playlist),
                            tint = AppText, modifier = Modifier.size(30.dp))
                    }
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
                    // Pinned artists first (after Liked Songs), newest pin first, as the official library.
                    val pins = com.music.spotui.jellyfin.LibraryPins.all()
                    val pinnedArtists = s.artists.filter { "artist:" + it.id in pins }.sortedBy { pins.indexOf("artist:" + it.id) }
                    val unpinned = s.artists.filterNot { "artist:" + it.id in pins }
                    items(pinnedArtists.size, key = { "pin" + pinnedArtists[it].id }) { i ->
                        LibraryArtistRow(pinnedArtists[i], nav, pinned = true)
                    }
                    items(s.playlists.size, key = { "p" + s.playlists[it].id }) { i ->
                        val p = s.playlists[i]
                        PlaylistRow(p, listOf(stringResource(R.string.jellyfin_playlist), com.music.spotui.jellyfin.LocalProfile.name).filter { it.isNotBlank() }.joinToString(" • ")) {
                            JellyfinRoutes.playlist(nav, p.id, p.name)
                        }
                    }
                    items(unpinned.size, key = { "r" + unpinned[it].id }) { i ->
                        LibraryArtistRow(unpinned[i], nav, pinned = false)
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

/** An artist of Your Library: a tap opens them, holding opens their menu (Follow, Pin). */
@Composable
private fun LibraryArtistRow(a: JellyfinArtist, nav: NavController, pinned: Boolean) {
    var menu by remember { mutableStateOf(false) }
    if (menu) ArtistMenu(a) { menu = false }
    ListRow(artistImage(a, 160), a.name, stringResource(R.string.jellyfin_artist), round = true, pinned = pinned,
        onLongClick = { menu = true }) { JellyfinRoutes.artist(nav, a.id) }
}
