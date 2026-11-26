package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.vector.path
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (menu) TrackMenu(t, nav, onAddToPlaylist = { adding = true }, onRemove = onRemove) { menu = false }
    if (adding) AddToPlaylistSheet(listOf(t)) { adding = false }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            // A tap plays; holding the row opens its menu, as in the official app.
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true },
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
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

/**
 * A song's menu, drawn as the official one: the song (cover, title, artist) over a rule, then
 * Share, Liked Songs, Add to playlist, (Remove from this playlist), Add to Queue, Play next,
 * Go to Queue, Go to album, Go to artist; thin outlined glyphs, 58 dp rows.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TrackMenu(
    t: JellyfinTrack, nav: NavController, onAddToPlaylist: (() -> Unit)? = null, onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = com.music.spotui.di.Graph.app
    val liked = LikedSongs.isLiked(t)
    com.music.spotui.ui.components.LookSheet(onDismissRequest = onDismiss, containerColor = MenuBackground) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(start = 17.dp, end = 17.dp, top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(trackCover(t), 50.dp, RoundedCornerShape(2.dp))
                Column(Modifier.padding(start = 13.dp)) {
                    Text(t.title, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = AppMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            MenuRow(MenuIcons.share, stringResource(R.string.jellyfin_share)) {
                onDismiss()
                scope.launch {
                    runCatching {
                        com.music.spotui.ui.components.ShareCard.share(context, t.title, t.artist, trackCover(t, 640),
                            context.getString(R.string.action_share_label))
                    }
                }
            }
            MenuRow(
                icon = null,
                art = {
                    if (liked) Icon(Icons.Filled.CheckCircle, null, tint = AppAccent, modifier = Modifier.size(25.dp))
                    else Box(Modifier.size(24.dp).clip(RoundedCornerShape(3.dp))) { LikedArt(24.dp) }
                },
                label = stringResource(if (liked) R.string.jellyfin_unlike else R.string.jellyfin_like),
            ) {
                onDismiss()
                val on = !liked
                LikedSongs.set(t, on)
                scope.launch { runCatching { JellyfinApi.setFavorite(t.id, on) }.onFailure { LikedSongs.set(t, !on) } }
                android.widget.Toast.makeText(app, app.getString(if (on) R.string.jellyfin_liked else R.string.jellyfin_unliked), android.widget.Toast.LENGTH_SHORT).show()
            }
            if (onAddToPlaylist != null) MenuRow(MenuIcons.addCircle, stringResource(R.string.jellyfin_add_to_playlist)) { onDismiss(); onAddToPlaylist() }
            if (onRemove != null) MenuRow(MenuIcons.removeCircle, stringResource(R.string.jellyfin_remove_from_playlist)) { onDismiss(); onRemove() }
            MenuRow(MenuIcons.addQueue, stringResource(R.string.jellyfin_add_to_queue_menu)) {
                onDismiss()
                JellyfinPlayback.enqueue(t, next = false)
                android.widget.Toast.makeText(app, app.getString(R.string.jellyfin_added_to_queue, t.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            MenuRow(MenuIcons.playNext, stringResource(R.string.jellyfin_play_next)) { onDismiss(); JellyfinPlayback.enqueue(t, next = true) }
            MenuRow(MenuIcons.queue, stringResource(R.string.jellyfin_go_to_queue)) {
                onDismiss(); nav.navigate(com.music.spotui.ui.navigation.Routes.Queue.route)
            }
            if (t.albumId.isNotBlank()) MenuRow(MenuIcons.album, stringResource(R.string.jellyfin_go_to_album)) { onDismiss(); JellyfinRoutes.album(nav, t.albumId) }
            if (t.artistId.isNotBlank()) MenuRow(MenuIcons.artist, stringResource(R.string.jellyfin_go_to_artist)) { onDismiss(); JellyfinRoutes.artist(nav, t.artistId) }
        }
    }
}

/** The official menus' sheet colour. */
internal val MenuBackground = Color(0xFF282828)

/**
 * One row of a menu, at the official metrics: 58 dp tall, a 24 dp glyph 18 dp from the edge, its
 * label from 55 dp, 17 sp.
 */
@Composable
internal fun MenuRow(icon: ImageVector?, label: String, art: (@Composable () -> Unit)? = null, tint: Color = AppText,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(58.dp).pressable(0.98f, onClick = onClick).padding(start = 18.dp, end = 18.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (art != null) art() else if (icon != null) Icon(icon, null, tint = Color(0xFFD9D9D9), modifier = Modifier.size(24.dp))
        }
        Text(label, color = tint, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 13.dp).weight(1f))
        trailing?.invoke()
    }
}

/** Thin outlined glyphs in the official menus' style (1.6 strokes on a 24 grid). */
internal object MenuIcons {
    private fun stroked(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathBuilder = build,
            )
        }.build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy); arcTo(r, r, 0f, true, true, cx + r, cy); arcTo(r, r, 0f, true, true, cx - r, cy); close()
    }

    val share = stroked("share") {
        circle(18f, 5.5f, 2.5f); circle(6f, 12f, 2.5f); circle(18f, 18.5f, 2.5f)
        moveTo(8.2f, 10.8f); lineTo(15.8f, 6.7f); moveTo(8.2f, 13.2f); lineTo(15.8f, 17.3f)
    }
    val addCircle = stroked("addCircle") { circle(12f, 12f, 9.5f); moveTo(12f, 7.5f); lineTo(12f, 16.5f); moveTo(7.5f, 12f); lineTo(16.5f, 12f) }
    val removeCircle = stroked("removeCircle") { circle(12f, 12f, 9.5f); moveTo(7.5f, 12f); lineTo(16.5f, 12f) }
    val addQueue = stroked("addQueue") {
        moveTo(3f, 4.5f); lineTo(8f, 4.5f); moveTo(5.5f, 2f); lineTo(5.5f, 7f)
        moveTo(11f, 5f); lineTo(21f, 5f); arcTo(1.5f, 1.5f, 0f, false, true, 21f, 8f); lineTo(11f, 8f)
        moveTo(3f, 13f); lineTo(21f, 13f); moveTo(3f, 19f); lineTo(21f, 19f)
    }
    val playNext = stroked("playNext") {
        moveTo(3f, 6f); lineTo(14f, 6f); moveTo(3f, 12f); lineTo(14f, 12f); moveTo(3f, 18f); lineTo(10f, 18f)
        moveTo(17f, 14f); lineTo(21f, 17f); lineTo(17f, 20f); close()
    }
    val queue = stroked("queue") {
        moveTo(5f, 4f); lineTo(19f, 4f); arcTo(2f, 2f, 0f, false, true, 19f, 8f); lineTo(5f, 8f); arcTo(2f, 2f, 0f, false, true, 5f, 4f)
        moveTo(3f, 13f); lineTo(21f, 13f); moveTo(3f, 19f); lineTo(21f, 19f)
    }
    val album = stroked("album") { circle(12f, 12f, 9.5f); circle(12f, 12f, 2.2f) }
    val artist = stroked("artist") {
        circle(10f, 7.5f, 4f)
        moveTo(2.5f, 21f); lineTo(2.5f, 19.5f); arcTo(4.5f, 4.5f, 0f, false, true, 7f, 15f); lineTo(12.5f, 15f)
        moveTo(18f, 21.5f); lineTo(18f, 13.5f); lineTo(21.5f, 14.5f)
        circle(16.3f, 21.2f, 1.7f)
    }
}

/**
 * Liked Songs as this session knows them: each song's state from the server (JellyfinTrack.favorite),
 * then what the menus changed, so a song's menu and its pages agree at once.
 */
internal object LikedSongs {
    private val changed = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun isLiked(t: JellyfinTrack): Boolean = changed[t.id] ?: t.favorite
    fun set(t: JellyfinTrack, on: Boolean) { changed[t.id] = on }
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
