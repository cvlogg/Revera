package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinArtist
import com.music.spotui.jellyfin.JellyfinAuthException
import com.music.spotui.jellyfin.JellyfinPlayback
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.jellyfin.JellyfinTrack
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The Jellyfin-only pages' routes, and how to open them. */
object JellyfinRoutes {
    const val ARTIST = "jf-artist"
    const val ALBUM = "jf-album"
    const val GENRE = "jf-genre"
    const val PLAYLIST = "jf-playlist"
    const val LIST = "jf-list"
    const val ADD_SONGS = "jf-addsongs"
    const val EDIT_PLAYLIST = "jf-editplaylist"

    fun artist(nav: NavController, id: String) { if (id.isNotBlank()) nav.navigate("$ARTIST/$id") }
    fun album(nav: NavController, id: String) { if (id.isNotBlank()) nav.navigate("$ALBUM/$id") }
    fun genre(nav: NavController, id: String, name: String) { nav.navigate("$GENRE/$id?name=${android.net.Uri.encode(name)}") }
    fun playlist(nav: NavController, id: String, name: String) { nav.navigate("$PLAYLIST/$id?name=${android.net.Uri.encode(name)}") }
    fun list(nav: NavController, kind: String) { nav.navigate("$LIST/$kind") }
    fun addSongs(nav: NavController, playlistId: String) { nav.navigate("$ADD_SONGS/$playlistId") }
    fun editPlaylist(nav: NavController, playlistId: String) { nav.navigate("$EDIT_PLAYLIST/$playlistId") }
}

/** What a page asked the server: loading, its answer, or a failure (a refused token signs out). */
internal class Loaded<T>(val value: T?, val failed: Boolean) {
    val loading: Boolean get() = value == null && !failed
}

@Composable
internal fun <T> load(vararg keys: Any?, block: suspend () -> T): Loaded<T> {
    val context = LocalContext.current
    // Kept across a reload (the page shows what it had until the new answer comes, no flash).
    var state by remember { mutableStateOf(Loaded<T>(null, false)) }
    LaunchedEffect(*keys) {
        state = try {
            Loaded(block(), false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyfinAuthException) {
            JellyfinPref.signOut(context)
            Loaded(null, true)
        } catch (e: Exception) {
            Loaded(null, true)
        }
    }
    return state
}

/** Browse tiles' colours, in turn. */
internal val TileColors = listOf(
    Color(0xFFDC148C), Color(0xFF006450), Color(0xFF1E3264), Color(0xFF8400E7), Color(0xFF8D67AB),
    Color(0xFFE8115B), Color(0xFFBA5D07), Color(0xFF477D95), Color(0xFF27856A), Color(0xFFAF2896),
    Color(0xFF0D73EC), Color(0xFFE91429), Color(0xFF503750), Color(0xFF148A08), Color(0xFF7358FF),
)

internal fun tileColor(i: Int) = TileColors[((i % TileColors.size) + TileColors.size) % TileColors.size]
internal fun tileColor(name: String) = tileColor(name.hashCode())

internal val CardGrey = Color(0xFF2A2A2A)

internal fun albumCover(a: JellyfinAlbum, size: Int = 300) = JellyfinApi.imageUrl(a.id, a.imageTag, size)
internal fun trackCover(t: JellyfinTrack, size: Int = 160) = JellyfinApi.trackImageUrl(t, size)
/** The artist's picture: the one chosen on this phone (Change artist picture), else the server's. */
internal fun artistImage(a: JellyfinArtist, size: Int = 300) =
    com.music.spotui.jellyfin.ArtistPictures.of(a.id) ?: JellyfinApi.imageUrl(a.id, a.imageTag, size)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun Cover(url: String, size: Dp, shape: Shape = RoundedCornerShape(4.dp), modifier: Modifier = Modifier) {
    GlideImage(
        model = url.ifBlank { R.drawable.jellyfin_cover }, contentDescription = null, contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.jellyfin_cover),
        modifier = modifier.size(size).clip(shape),
    )
}

@Composable
internal fun FillImage(url: String, modifier: Modifier) = FillImage(listOf(url), modifier)

/** The first of [urls] that loads (a picture that fails gives way to the next), else the app's cover. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun FillImage(urls: List<String>, modifier: Modifier) {
    val context = LocalContext.current
    val tries = urls.filter { it.isNotBlank() }
    GlideImage(
        model = tries.firstOrNull() ?: R.drawable.jellyfin_cover, contentDescription = null, contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.jellyfin_cover), modifier = modifier,
        requestBuilderTransform = { first ->
            tries.drop(1).foldRight<String, com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable>?>(null) { u, next ->
                com.bumptech.glide.Glide.with(context).load(u).let { b -> if (next != null) b.error(next) else b }
            }?.let { first.error(it) } ?: first
        },
    )
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = AppAccent)
}

@Composable
internal fun Problem(text: String = stringResource(R.string.jellyfin_error_unreachable)) {
    Text(text, color = AppMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp))
}

/** A section's title, with an optional "See all" on its right. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, seeAll: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
        if (seeAll != null) Text(stringResource(R.string.jellyfin_see_all), color = AppMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.pressable(0.95f, onClick = seeAll).padding(4.dp))
    }
}

/** The round back button over a page's top left. */
@Composable
internal fun BackButton(nav: NavController, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.statusBarsPadding().padding(12.dp).size(40.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)).pressable { nav.navigateUp() },
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = Color.White) }
}

/** The big round play button of a page. */
@Composable
internal fun PlayCircle(size: Dp = 56.dp, playing: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).pressable(0.92f, onClick = onClick).clip(CircleShape).background(AppAccent),
    ) {
        com.music.spotui.ui.components.PlayPauseIcon(playing, stringResource(R.string.content_description_play_button),
            tint = Color.Black, modifier = Modifier.size(size * 0.46f))
    }
}

/** The shuffle glyph beside a page's play button. */
@Composable
internal fun ShuffleButton(onClick: () -> Unit) {
    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_player_shuffle), stringResource(R.string.play_mode_item_shuffle_title),
        tint = AppMuted, modifier = Modifier.size(30.dp).pressable(onClick = onClick))
}

/** A song row: number or cover, title (green while it plays), its artist line, and its menu. */
@Composable
internal fun TrackRow(
    t: JellyfinTrack, nav: NavController, number: Int? = null, showCover: Boolean = true,
    subtitle: String = t.artist,
    /** In a playlist of the user: the menu's "Remove from this playlist". */
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentId by com.music.spotui.di.Graph.currentSongState.songId
    val current = currentId == JellyfinPlayback.songIdFor(t)
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    if (menu) TrackMenu(t, nav, onAddToPlaylist = { adding = true }, onRemove = onRemove) { menu = false }
    if (adding) AddToPlaylistSheet(listOf(t)) { adding = false }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.98f, onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
    ) {
        if (number != null) Text(number.toString(), color = AppText, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        if (number != null && showCover) Spacer(Modifier.width(14.dp))
        if (showCover) Cover(trackCover(t), 50.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(t.title, color = if (current) AppAccent else AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.size(44.dp).pressable(0.9f) { menu = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.jellyfin_more), tint = AppMuted)
        }
    }
}

/** A song's menu: like, queue, play next, its album, its artist. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TrackMenu(
    t: JellyfinTrack, nav: NavController, onAddToPlaylist: (() -> Unit)? = null, onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val app = com.music.spotui.di.Graph.app
    var liked by remember { mutableStateOf<Boolean?>(null) }
    com.music.spotui.ui.components.LookSheet(onDismissRequest = onDismiss, containerColor = com.music.spotui.ui.theme.tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(trackCover(t), 48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(t.title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            @Composable
            fun Item(icon: ImageVector, label: String, action: () -> Unit) = Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().pressable(0.98f) { action(); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(icon, null, tint = AppMuted, modifier = Modifier.size(24.dp))
                Text(label, color = AppText, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
            }
            Item(if (liked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(if (liked == true) R.string.jellyfin_unlike else R.string.jellyfin_like)) {
                val on = liked != true
                scope.launch { runCatching { JellyfinApi.setFavorite(t.id, on) } }
                android.widget.Toast.makeText(app, app.getString(if (on) R.string.jellyfin_liked else R.string.jellyfin_unliked), android.widget.Toast.LENGTH_SHORT).show()
            }
            if (onAddToPlaylist != null) Item(Icons.Filled.AddCircleOutline, stringResource(R.string.jellyfin_add_to_playlist)) { onAddToPlaylist() }
            if (onRemove != null) Item(Icons.Filled.RemoveCircleOutline, stringResource(R.string.jellyfin_remove_from_playlist)) { onRemove() }
            Item(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.jellyfin_add_to_queue)) {
                JellyfinPlayback.enqueue(t, next = false)
                android.widget.Toast.makeText(app, app.getString(R.string.jellyfin_added_to_queue, t.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            Item(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.jellyfin_play_next)) { JellyfinPlayback.enqueue(t, next = true) }
            if (t.albumId.isNotBlank()) Item(Icons.Filled.Album, stringResource(R.string.jellyfin_go_to_album)) { JellyfinRoutes.album(nav, t.albumId) }
            if (t.artistId.isNotBlank()) Item(Icons.Filled.Person, stringResource(R.string.jellyfin_go_to_artist)) { JellyfinRoutes.artist(nav, t.artistId) }
        }
    }
}

/** An album (or playlist) in a row of cards: cover, name, one line under it. */
@Composable
internal fun CoverCard(url: String, title: String, subtitle: String, size: Dp = 150.dp, round: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(size).pressable(0.96f, onClick = onClick)) {
        Cover(url, size, if (round) CircleShape else RoundedCornerShape(4.dp))
        Text(title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = if (subtitle.isBlank()) 2 else 1,
            overflow = TextOverflow.Ellipsis, textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (subtitle.isNotBlank()) Text(subtitle, color = AppMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

/** A horizontal shelf of album cards. */
@Composable
internal fun AlbumShelf(albums: List<JellyfinAlbum>, nav: NavController, size: Dp = 150.dp, subtitle: (JellyfinAlbum) -> String = { it.artist }) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(albums.size, key = { albums[it].id }) { i ->
            val a = albums[i]
            CoverCard(albumCover(a), a.name, subtitle(a), size) { JellyfinRoutes.album(nav, a.id) }
        }
    }
}

/** A horizontal shelf of round artist cards. */
@Composable
internal fun ArtistShelf(artists: List<JellyfinArtist>, nav: NavController) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(artists.size, key = { artists[it].id }) { i ->
            val a = artists[i]
            CoverCard(artistImage(a), a.name, "", 130.dp, round = true) { JellyfinRoutes.artist(nav, a.id) }
        }
    }
}

/** A list row: cover (round for an artist), title, one line under it. */
@Composable
internal fun ListRow(url: String, title: String, subtitle: String, round: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Cover(url, 64.dp, if (round) CircleShape else RoundedCornerShape(4.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** The user's round badge (the first letter of their name), as on Home, Search and Your Library. */
@Composable
internal fun Avatar(size: Dp = 34.dp, onClick: () -> Unit = {}) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(AppAccent).pressable(onClick = onClick),
    ) {
        Text(JellyfinPref.userName.trim().take(1).uppercase().ifBlank { "?" }, color = Color.Black,
            fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

/** A coloured tile with its name at the top left and a tilted cover in its bottom right corner (Search). */
@Composable
internal fun BrowseTile(title: String, color: Color, coverUrl: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(96.dp).clip(RoundedCornerShape(6.dp)).background(color).pressable(0.97f, onClick = onClick),
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 64.dp))
        Cover(coverUrl, 70.dp, RoundedCornerShape(4.dp), Modifier.align(Alignment.BottomEnd)
            .graphicsLayer { translationX = 16.dp.toPx(); translationY = 6.dp.toPx(); rotationZ = 25f })
    }
}

/** A small framed thumbnail (the artist page's latest release beside Follow). */
@Composable
internal fun FramedThumb(url: String) {
    Cover(url, 44.dp, RoundedCornerShape(4.dp), Modifier.border(1.5.dp, AppMuted, RoundedCornerShape(4.dp)))
}
