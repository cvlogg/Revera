package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.TabReselect
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private class HomeFeed(
    val recent: List<JellyfinAlbum>,
    val added: List<JellyfinAlbum>,
    val random: List<JellyfinAlbum>,
    val mostPlayed: List<JellyfinAlbum>,
    val favorites: List<JellyfinAlbum>,
    val artists: List<JellyfinArtist>,
    val playlists: List<JellyfinPlaylist>,
)

private enum class HomeChip { ALBUMS, ARTISTS, PLAYLISTS }

/** "Good morning / afternoon / evening" by the hour on the phone. */
@Composable
private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return stringResource(when (hour) {
        in 5..11 -> R.string.jellyfin_good_morning
        in 12..17 -> R.string.jellyfin_good_afternoon
        else -> R.string.jellyfin_good_evening
    })
}

/**
 * Home: the greeting and its shortcuts, the filter chips, a grid of what was played last, a pick,
 * then shelves (jump back in, recently added, artists, most played, rediscover, playlists).
 */
@Composable
fun JellyfinHomeScreen(nav: NavController) {
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var chip by rememberSaveable { mutableStateOf<HomeChip?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { TabReselect.home.collect { list.animateScrollToItem(0) } }

    val feed = load(JellyfinPref.libraryId, refresh) {
        coroutineScope {
            val recent = async { runCatching { JellyfinApi.recentlyPlayedAlbums(16) }.getOrDefault(emptyList()) }
            val added = async { JellyfinApi.albumsBy("DateCreated", 16) }
            val random = async { JellyfinApi.albumsBy("Random", 17) }
            val most = async { runCatching { JellyfinApi.albumsBy("PlayCount", 16, played = true) }.getOrDefault(emptyList()) }
            val fav = async { runCatching { JellyfinApi.albumsBy("SortName", 20, favorites = true) }.getOrDefault(emptyList()) }
            val artists = async {
                runCatching { JellyfinApi.artists(favorites = true, limit = 20) }.getOrDefault(emptyList())
                    .ifEmpty { runCatching { JellyfinApi.artists(limit = 20, sort = "Random") }.getOrDefault(emptyList()) }
            }
            val lists = async { runCatching { JellyfinApi.playlists() }.getOrDefault(emptyList()) }
            HomeFeed(recent.await(), added.await(), random.await(), most.await(), fav.await(), artists.await(), lists.await())
        }
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(greeting(), color = AppText, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HeaderIcon(Icons.Outlined.History, stringResource(R.string.jellyfin_recently_played)) { JellyfinRoutes.list(nav, "recent") }
                    HeaderIcon(Icons.Outlined.Settings, stringResource(R.string.jellyfin_settings)) { nav.navigate(Routes.Settings.route) }
                }
            }
            item(key = "chips") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((c, label) in listOf(HomeChip.ALBUMS to R.string.jellyfin_albums, HomeChip.ARTISTS to R.string.jellyfin_artists,
                        HomeChip.PLAYLISTS to R.string.jellyfin_playlists)) {
                        Chip(stringResource(label), chip == c) { chip = if (chip == c) null else c }
                    }
                }
            }
            val f = feed.value
            when {
                f == null && feed.failed -> item { Problem() }
                f == null -> item { Loading() }
                else -> {
                    val showAlbums = chip == null || chip == HomeChip.ALBUMS
                    val showArtists = chip == null || chip == HomeChip.ARTISTS
                    val showLists = chip == null || chip == HomeChip.PLAYLISTS
                    if (chip == null) item(key = "grid") { QuickGrid(f, nav) }
                    if (showAlbums) f.random.firstOrNull()?.let { pick ->
                        item(key = "pick-title") { SectionTitle(stringResource(R.string.jellyfin_picked_for_you)) }
                        item(key = "pick") { PickedCard(pick) { JellyfinRoutes.album(nav, pick.id) } }
                    }
                    if (showAlbums && f.recent.isNotEmpty()) {
                        item(key = "jump-title") { SectionTitle(stringResource(R.string.jellyfin_jump_back_in)) { JellyfinRoutes.list(nav, "recent") } }
                        item(key = "jump") { AlbumShelf(f.recent, nav, 160.dp) }
                    }
                    if (showAlbums && f.added.isNotEmpty()) {
                        item(key = "added-title") { SectionTitle(stringResource(R.string.jellyfin_recently_added)) { JellyfinRoutes.list(nav, "added") } }
                        item(key = "added") { AlbumShelf(f.added, nav) }
                    }
                    if (showArtists && f.artists.isNotEmpty()) {
                        item(key = "artists-title") { SectionTitle(stringResource(R.string.jellyfin_your_artists)) { JellyfinRoutes.list(nav, "artists") } }
                        item(key = "artists") { ArtistShelf(f.artists, nav) }
                    }
                    if (showAlbums && f.favorites.isNotEmpty()) {
                        item(key = "fav-title") { SectionTitle(stringResource(R.string.jellyfin_liked_albums)) { JellyfinRoutes.list(nav, "favorites") } }
                        item(key = "fav") { AlbumShelf(f.favorites, nav) }
                    }
                    if (showAlbums && f.mostPlayed.isNotEmpty()) {
                        item(key = "most-title") { SectionTitle(stringResource(R.string.jellyfin_most_played)) { JellyfinRoutes.list(nav, "most") } }
                        item(key = "most") { AlbumShelf(f.mostPlayed, nav) }
                    }
                    if (showAlbums && f.random.size > 1) {
                        item(key = "re-title") { SectionTitle(stringResource(R.string.jellyfin_rediscover)) { JellyfinRoutes.list(nav, "random") } }
                        item(key = "re") { AlbumShelf(f.random.drop(1), nav) { a -> listOf(a.artist, a.year.takeIf { it > 0 }?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString(" • ") } }
                    }
                    if (showLists && f.playlists.isNotEmpty()) {
                        item(key = "pl-title") { SectionTitle(stringResource(R.string.jellyfin_your_playlists)) }
                        item(key = "pl") {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)) {
                                items(f.playlists.size, key = { f.playlists[it].id }) { i ->
                                    val p = f.playlists[i]
                                    CoverCard(JellyfinApi.imageUrl(p.id, p.imageTag, 300), p.name,
                                        stringResource(R.string.jellyfin_songs_count, p.songCount)) { JellyfinRoutes.playlist(nav, p.id, p.name) }
                                }
                            }
                        }
                    }
                    if (f.recent.isEmpty() && f.added.isEmpty() && f.random.isEmpty()) item { Problem(stringResource(R.string.jellyfin_empty_library)) }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = AppText, modifier = Modifier.size(27.dp))
    }
}

/** A filter chip of Home and Your Library: grey, green when on. */
@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Color.Black else AppText, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) com.music.spotui.ui.theme.AppAccent else CardGrey)
            .pressable(0.95f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The two-column grid of shortcuts: Liked Songs, then the albums played last (else the ones added last). */
@Composable
private fun QuickGrid(f: HomeFeed, nav: NavController) {
    val albums = (f.recent + f.added).distinctBy { it.id }.take(7)
    val cells: List<@Composable (Modifier) -> Unit> = listOf<@Composable (Modifier) -> Unit>({ m ->
        Tile(m, stringResource(R.string.jellyfin_liked_songs), { LikedArt() }) { JellyfinRoutes.list(nav, "liked") }
    }) + albums.map { a -> @Composable { m: Modifier -> Tile(m, a.name, { Cover(albumCover(a, 160), 56.dp, RoundedCornerShape(0.dp)) }) { JellyfinRoutes.album(nav, a.id) } } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, title: String, art: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(4.dp)).background(CardGrey).pressable(0.97f, onClick = onClick),
    ) {
        art()
        Text(title, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

/** Liked Songs' cover: a heart on a violet gradient. */
@Composable
internal fun LikedArt(size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(Brush.linearGradient(listOf(Color(0xFF4A27C9), Color(0xFF8FB6C9)))),
    ) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.42f)) }
}

/** "Picked for you": a big cover beside a grey panel naming the album. */
@Composable
private fun PickedCard(a: JellyfinAlbum, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(8.dp)).background(CardGrey)
            .pressable(0.98f, onClick = onClick),
    ) {
        FillImage(albumCover(a, 500), Modifier.size(170.dp))
        Column(Modifier.fillMaxHeight().weight(1f).padding(14.dp), verticalArrangement = Arrangement.Top) {
            Text(listOf(stringResource(R.string.jellyfin_album), a.year.takeIf { it > 0 }?.toString().orEmpty())
                .filter { it.isNotBlank() }.joinToString(" • "), color = AppMuted, fontSize = 13.sp)
            Text(a.name, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
            if (a.artist.isNotBlank()) Text(a.artist, color = AppMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
