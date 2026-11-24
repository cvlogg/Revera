package com.music.spotui.jellyfin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinArtist
import com.music.spotui.jellyfin.JellyfinPlayback
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.jellyfin.JellyfinTrack
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.navigation.TabReselect
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** A genre of the library, with a cover from one of its albums. */
internal class GenreCard(val id: String, val name: String, val cover: String, val albums: Int)

/** What the browse part of Search is drawn from. */
private class Browse(val covers: List<String>, val genres: List<GenreCard>, val decades: List<Pair<Int, String>>)

/** The genres of [albums], most albums first, each with one album's cover. */
internal fun genreCards(albums: List<JellyfinAlbum>): List<GenreCard> =
    albums.flatMap { a -> a.genres.map { it to a } }.groupBy { it.first.first }
        .map { (id, pairs) -> GenreCard(id, pairs.first().first.second, albumCover(pairs.first().second, 400), pairs.size) }
        .sortedByDescending { it.albums }

private class Results(val artists: List<JellyfinArtist>, val albums: List<JellyfinAlbum>, val tracks: List<JellyfinTrack>)

/**
 * Search: the user's badge and the title, the white field, then browse tiles (what is in the
 * library), "Discover something new" (tall genre cards), "More for you", genres and decades.
 * Typing shows the server's matches: artists, songs, albums.
 */
@Composable
fun JellyfinSearchScreen(nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { TabReselect.search.collect { runCatching { focus.requestFocus() } } }
    BackHandler(enabled = query.isNotEmpty() || focused) { query = ""; focusManager.clearFocus() }

    val browse = load(JellyfinPref.libraryId) {
        coroutineScope {
            val withGenres = async { JellyfinApi.albumsWithGenres(200) }
            val all = withGenres.await()
            val decades = all.filter { it.year > 1900 }.groupBy { it.year / 10 * 10 }.entries.sortedByDescending { it.key }
                .map { (d, albums) -> d to albumCover(albums.first(), 300) }
            Browse(all.take(12).map { albumCover(it, 300) }, genreCards(all), decades)
        }
    }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(300); debounced = query.trim() }
    val results = if (debounced.isBlank()) null else load(debounced, JellyfinPref.libraryId) {
        coroutineScope {
            val lib = JellyfinPref.libraryId
            val artists = async { runCatching { JellyfinApi.artists(search = debounced, limit = 10) }.getOrDefault(emptyList()) }
            val albums = async { JellyfinApi.albums(lib, search = debounced).first.take(20) }
            val tracks = async { JellyfinApi.searchTracks(lib, debounced, 30) }
            Results(artists.await(), albums.await(), tracks.await())
        }
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
            if (!focused && query.isEmpty()) item(key = "head") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(36.dp) { JellyfinRoutes.list(nav, "account") }
                    Text(stringResource(R.string.jellyfin_search), color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 14.dp))
                }
            }
            stickyHeader(key = "field") {
                Box(Modifier.fillMaxWidth().background(AppBackground)
                    .then(if (focused || query.isNotEmpty()) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(query, { query = it }, focus, Modifier.onFocusChanged { focused = it.isFocused }) {
                        query = ""; focusManager.clearFocus()
                    }
                }
            }
            if (results != null) {
                val r = results.value
                when {
                    r == null && results.failed -> item { Problem() }
                    r == null -> item { Loading() }
                    r.artists.isEmpty() && r.albums.isEmpty() && r.tracks.isEmpty() -> item {
                        Problem(stringResource(R.string.jellyfin_no_results_for, debounced))
                    }
                    else -> {
                        if (r.artists.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.jellyfin_artists)) }
                            item { ArtistShelf(r.artists, nav) }
                        }
                        if (r.tracks.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.jellyfin_songs)) }
                            items(r.tracks.size, key = { "t" + r.tracks[it].id }) { i ->
                                val t = r.tracks[i]
                                TrackRow(t, nav, subtitle = listOf(stringResource(R.string.jellyfin_song), t.artist).filter { it.isNotBlank() }.joinToString(" • ")) {
                                    focusManager.clearFocus(); JellyfinPlayback.play(r.tracks, i, JellyfinPlayback.From.search(debounced))
                                }
                            }
                        }
                        if (r.albums.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.jellyfin_albums)) }
                            items(r.albums.size, key = { "a" + r.albums[it].id }) { i ->
                                val a = r.albums[i]
                                ListRow(albumCover(a, 160), a.name, listOf(stringResource(R.string.jellyfin_album), a.artist).filter { it.isNotBlank() }.joinToString(" • ")) {
                                    JellyfinRoutes.album(nav, a.id)
                                }
                            }
                        }
                    }
                }
            } else if (!focused) {
                val b = browse.value
                when {
                    b == null && browse.failed -> item { Problem() }
                    b == null -> item { Loading() }
                    else -> browseSections(b, nav)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.browseSections(b: Browse, nav: NavController) {
    fun cover(i: Int) = b.covers.getOrNull(i).orEmpty()
    // The top tiles: what the library holds, two by two (as the official app's Music, Podcasts…).
    item(key = "top") {
        val tiles = listOf(
            Triple(R.string.jellyfin_albums, Color(0xFFDC148C), "albums"),
            Triple(R.string.jellyfin_artists, Color(0xFF006450), "artists"),
            Triple(R.string.jellyfin_songs, Color(0xFF1E3264), "songs"),
            Triple(R.string.jellyfin_playlists, Color(0xFF8400E7), "playlists"),
            Triple(R.string.jellyfin_made_for_you, Color(0xFF8D67AB), "random"),
        )
        TileGrid(tiles.size) { i, m ->
            val (label, color, kind) = tiles[i]
            BrowseTile(stringResource(label), color, cover(i), m) { JellyfinRoutes.list(nav, kind) }
        }
    }
    if (b.genres.isNotEmpty()) {
        item(key = "discover-title") { SectionTitle(stringResource(R.string.jellyfin_discover)) }
        item(key = "discover") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                val picks = b.genres.shuffled(java.util.Random(b.genres.size.toLong())).take(10)
                items(picks.size, key = { picks[it].id }) { i ->
                    val g = picks[i]
                    Box(Modifier.width(140.dp).height(210.dp).clip(RoundedCornerShape(8.dp)).pressable(0.97f) { JellyfinRoutes.genre(nav, g.id, g.name) }) {
                        FillImage(g.cover, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                        Text("#" + g.name.lowercase().replace(" ", ""), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                    }
                }
            }
        }
    }
    item(key = "more-title") { SectionTitle(stringResource(R.string.jellyfin_more_for_you)) }
    item(key = "more") {
        val tiles = listOf(
            Triple(R.string.jellyfin_recently_added, "added", 5),
            Triple(R.string.jellyfin_recently_played, "recent", 6),
            Triple(R.string.jellyfin_most_played, "most", 7),
            Triple(R.string.jellyfin_liked_songs, "liked", 8),
            Triple(R.string.jellyfin_liked_albums, "favorites", 9),
            Triple(R.string.jellyfin_rediscover, "random", 10),
        )
        TileGrid(tiles.size, tall = true) { i, m ->
            val (label, kind, c) = tiles[i]
            BrowseTile(stringResource(label), tileColor(c), cover(c), m) { JellyfinRoutes.list(nav, kind) }
        }
    }
    if (b.genres.isNotEmpty()) {
        item(key = "genres-title") { SectionTitle(stringResource(R.string.jellyfin_genres)) { JellyfinRoutes.list(nav, "genres") } }
        item(key = "genres") {
            val shown = b.genres.take(6)
            TileGrid(shown.size, tall = true) { i, m ->
                val g = shown[i]
                BrowseTile(g.name, tileColor(g.name), g.cover, m) { JellyfinRoutes.genre(nav, g.id, g.name) }
            }
        }
    }
    if (b.decades.isNotEmpty()) {
        item(key = "decades-title") { SectionTitle(stringResource(R.string.jellyfin_decades)) }
        item(key = "decades") {
            TileGrid(b.decades.size, tall = true) { i, m ->
                val (d, c) = b.decades[i]
                BrowseTile(stringResource(R.string.jellyfin_decade, d), tileColor(d / 10), c, m) { JellyfinRoutes.list(nav, "decade$d") }
            }
        }
    }
}

/** Two tiles a row, 16 dp from the edges. */
@Composable
internal fun TileGrid(count: Int, tall: Boolean = false, tile: @Composable (Int, Modifier) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (0 until count).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { i -> tile(i, Modifier.weight(1f).then(if (tall) Modifier.height(110.dp) else Modifier)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The white search field: a magnifier, "What do you want to listen to?", a clear button while typed in. */
@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, focus: FocusRequester, modifier: Modifier, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)
            .pressable(1f) { runCatching { focus.requestFocus() } }.padding(horizontal = 12.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = Color(0xFF121212), modifier = Modifier.size(30.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(stringResource(R.string.jellyfin_search_placeholder), color = Color(0xFF535353),
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = TextStyle(color = Color(0xFF121212), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Color(0xFF121212)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Close, stringResource(R.string.jellyfin_clear), tint = Color(0xFF121212),
            modifier = Modifier.size(24.dp).pressable(0.9f, onClick = onClear))
    }
}
