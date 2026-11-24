package com.music.spotui.jellyfin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private class ArtistPage(val artist: JellyfinArtist, val top: List<JellyfinTrack>, val albums: List<JellyfinAlbum>)

/**
 * An artist: their picture across the top with the name over it, a line of counts, the latest
 * release, Follow (the server's favourite), shuffle and play, then Popular (their songs this user
 * played most), the discography, and About.
 */
@Composable
fun JellyfinArtistScreen(nav: NavController, id: String) {
    val page = load(id) {
        coroutineScope {
            val artist = async { JellyfinApi.artist(id) }
            val top = async { JellyfinApi.artistTopTracks(id, 10) }
            val albums = async { JellyfinApi.artistAlbums(id) }
            ArtistPage(artist.await() ?: throw java.io.IOException("No artist"), top.await(), albums.await())
        }
    }
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        val p = page.value
        when {
            p == null && page.failed -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Problem() }
            p == null -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Loading() }
            else -> ArtistContent(p, nav)
        }
        BackButton(nav)
    }
}

@Composable
private fun ArtistContent(p: ArtistPage, nav: NavController) {
    val a = p.artist
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The picture chosen on this phone, else the server's backdrop, its picture, then the latest cover:
    // each one that fails to load gives way to the next.
    val images = listOfNotNull(
        com.music.spotui.jellyfin.ArtistPictures.of(a.id),
        JellyfinApi.imageUrl(a.id, a.backdropTag, 900, "Backdrop"),
        JellyfinApi.imageUrl(a.id, a.imageTag, 900),
        p.albums.firstOrNull { it.imageTag.isNotBlank() }?.let { albumCover(it, 900) },
    ).filter { it.isNotBlank() }
    val image = images.firstOrNull().orEmpty()
    var changing by remember { mutableStateOf(false) }
    if (changing) ChangeArtistPicture(a.id) { changing = false }
    var tint by remember(image) { mutableStateOf(com.music.spotui.di.Palette.cachedFirst(image) ?: Color(0xFF3A2A2A)) }
    LaunchedEffect(image) { if (image.isNotBlank()) com.music.spotui.di.Palette().extractFirstColorFromImageUrl(context, image) { tint = it } }
    var following by remember(a.id) { mutableStateOf(a.favorite) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf(false) }
    var allTracks by remember { mutableStateOf<List<JellyfinTrack>?>(null) }
    val from = JellyfinPlayback.From.artist(a.id, a.name)
    suspend fun everything(): List<JellyfinTrack> = allTracks ?: JellyfinApi.artistTracks(a.id).also { allTracks = it }
    val songs = a.songCount.takeIf { it > 0 }
    val stats = listOfNotNull(
        p.albums.size.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.jellyfin_albums_count, it, it) },
        songs?.let { context.resources.getQuantityString(R.plurals.jellyfin_songs_plural, it, it) },
    ).joinToString(" • ")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                FillImage(images, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f))))
                Text(a.name, color = Color.White, fontSize = if (a.name.length > 18) 36.sp else 48.sp, fontWeight = FontWeight.ExtraBold,
                    lineHeight = if (a.name.length > 18) 40.sp else 52.sp, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 18.dp))
                ArtistMenu(Modifier.align(Alignment.TopEnd), onPicture = { changing = true }) {
                    scope.launch { runCatching { everything().forEach { JellyfinPlayback.enqueue(it, next = false) } } }
                }
            }
        }
        item(key = "bar") {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), AppBackground)))
                .padding(horizontal = 16.dp).padding(top = 14.dp)) {
                if (stats.isNotBlank()) Text(stats, color = AppMuted, fontSize = 15.sp)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    p.albums.firstOrNull()?.let { latest ->
                        Box(Modifier.pressable(0.94f) { JellyfinRoutes.album(nav, latest.id) }) { FramedThumb(albumCover(latest, 160)) }
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        stringResource(if (following) R.string.jellyfin_following else R.string.jellyfin_follow),
                        color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, AppMuted), RoundedCornerShape(50))
                            .pressable(0.95f) {
                                val on = !following
                                following = on
                                scope.launch { runCatching { JellyfinApi.setFavorite(a.id, on) }.onFailure { following = !on } }
                            }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    ShuffleButton { scope.launch { runCatching { everything().let { if (it.isNotEmpty()) JellyfinPlayback.play(it, it.indices.random(), from, shuffle = true) } } } }
                    Spacer(Modifier.width(22.dp))
                    PlayCircle(60.dp) { scope.launch { runCatching { JellyfinPlayback.play(everything(), 0, from) } } }
                }
            }
        }
        item(key = "tabs") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                for ((i, label) in listOf(R.string.jellyfin_music, R.string.jellyfin_about).withIndex()) {
                    Column(Modifier.padding(end = 28.dp).pressable(0.96f) { tab = i }) {
                        Text(stringResource(label), color = if (tab == i) AppText else AppMuted, fontSize = 17.sp,
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
                        Box(Modifier.height(3.dp).width(if (tab == i) 56.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(AppAccent))
                    }
                }
            }
        }
        if (tab == 0) {
            if (p.top.isNotEmpty()) {
                item(key = "popular") { SectionTitle(stringResource(R.string.jellyfin_popular)) }
                val shown = if (more) p.top else p.top.take(5)
                items(shown.size, key = { "pop" + shown[it].id }) { i ->
                    val t = shown[i]
                    TrackRow(t, nav, number = i + 1,
                        subtitle = if (t.playCount > 0) java.text.NumberFormat.getIntegerInstance().format(t.playCount) else t.album) {
                        JellyfinPlayback.play(p.top, p.top.indexOf(t), from)
                    }
                }
                if (p.top.size > 5) item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (more) R.string.jellyfin_see_less else R.string.jellyfin_see_more), color = AppText, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, AppMuted), RoundedCornerShape(50)).pressable(0.95f) { more = !more }
                                .padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            if (p.albums.isNotEmpty()) {
                item(key = "disco-title") { SectionTitle(stringResource(R.string.jellyfin_discography)) }
                item(key = "disco") {
                    AlbumShelf(p.albums, nav) { al -> listOf(al.year.takeIf { it > 0 }?.toString().orEmpty(), context.getString(R.string.jellyfin_album))
                        .filter { it.isNotBlank() }.joinToString(" • ") }
                }
            }
        } else item(key = "about") {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))) {
                    FillImage(listOf(artistImage(a, 800)) + images, Modifier.fillMaxSize())
                }
                Text(a.overview.ifBlank { stringResource(R.string.jellyfin_no_bio) }, color = AppText, fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

/** The ⋮ over the picture's top right: add all their songs to the queue. */
@Composable
private fun ArtistMenu(modifier: Modifier, onPicture: () -> Unit, onQueue: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.statusBarsPadding().padding(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).pressable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.jellyfin_more), tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.jellyfin_add_all_to_queue)) }, onClick = { open = false; onQueue() })
            DropdownMenuItem(text = { Text(stringResource(R.string.jellyfin_change_artist_picture)) }, onClick = { open = false; onPicture() })
        }
    }
}
