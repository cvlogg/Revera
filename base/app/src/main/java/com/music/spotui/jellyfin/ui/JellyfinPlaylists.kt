package com.music.spotui.jellyfin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.jellyfin.ArtistPictures
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinPlayback
import com.music.spotui.jellyfin.JellyfinPlaylist
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.jellyfin.JellyfinTrack
import com.music.spotui.jellyfin.PlaylistNotes
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.screens.movableRow
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Any playlist changed (created, renamed, songs added or removed, deleted): pages read theirs again. */
object PlaylistChanges {
    var version by mutableIntStateOf(0)
        private set
    fun changed() { version++ }
}

private fun toast(text: String) =
    android.widget.Toast.makeText(com.music.spotui.di.Graph.app, text, android.widget.Toast.LENGTH_SHORT).show()

private fun str(id: Int, vararg args: Any) = com.music.spotui.di.Graph.app.getString(id, *args)

// ---------------------------------------------------------------------------------------------
// Covers
// ---------------------------------------------------------------------------------------------

/** A playlist's picture for a row: the one chosen on the phone, else the server's; "" with none. */
internal fun playlistCoverUrl(p: JellyfinPlaylist, size: Int = 300): String =
    ArtistPictures.of(p.id) ?: JellyfinApi.imageUrl(p.id, p.imageTag, size)

/**
 * A playlist's cover, as the official app draws it: the picture chosen for it; else the covers of
 * its first four albums in a square (the first one alone with fewer); else the server's; else a
 * note on grey (an empty playlist).
 */
@Composable
internal fun PlaylistArt(id: String, imageTag: String, tracks: List<JellyfinTrack>?, size: Dp) {
    val own = ArtistPictures.of(id)
    val covers = tracks.orEmpty().filter { it.imageTag.isNotBlank() || it.albumId.isNotBlank() }
        .distinctBy { it.albumId.ifBlank { it.id } }.map { trackCover(it, 300) }
    when {
        own != null -> Cover(own, size, RoundedCornerShape(4.dp))
        covers.size >= 4 -> Column(Modifier.size(size).clip(RoundedCornerShape(4.dp))) {
            for (r in 0..1) Row { for (c in 0..1) FillImage(covers[r * 2 + c], Modifier.size(size / 2)) }
        }
        covers.isNotEmpty() -> Cover(covers.first(), size, RoundedCornerShape(4.dp))
        imageTag.isNotBlank() -> Cover(JellyfinApi.imageUrl(id, imageTag, 600), size, RoundedCornerShape(4.dp))
        else -> EmptyPlaylistArt(size)
    }
}

@Composable
internal fun EmptyPlaylistArt(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(4.dp)).background(Color(0xFF282828)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.MusicNote, null, tint = AppMuted, modifier = Modifier.size(size * 0.42f))
    }
}

/** A playlist row of Your Library and the lists: its cover, name, "Playlist • owner". */
@Composable
internal fun PlaylistRow(p: JellyfinPlaylist, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        val url = playlistCoverUrl(p, 160)
        if (url.isBlank()) EmptyPlaylistArt(64.dp) else Cover(url, 64.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(p.name, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// New playlist
// ---------------------------------------------------------------------------------------------

/**
 * "Give your playlist a name": full screen, the name ready to type over ("My playlist #n"), Cancel
 * and Create. [ids] go in the new playlist; [onCreated] gets its id and name.
 */
@Composable
internal fun NewPlaylistDialog(ids: List<String> = emptyList(), onDismiss: () -> Unit, onCreated: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val count = load("count") { JellyfinApi.playlists().size }.value
    val default = stringResource(R.string.jellyfin_my_playlist_n, (count ?: 0) + 1)
    var name by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(count) { if (count != null && name.text.isEmpty()) name = TextFieldValue(default, TextRange(0, default.length)) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A5A5A), Color(0xFF121212))))
                .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.jellyfin_name_your_playlist), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            BasicTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(AppAccent),
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp).focusRequester(focus),
            )
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(AppMuted))
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(stringResource(R.string.jellyfin_cancel), outlined = true, onClick = onDismiss)
                Pill(stringResource(R.string.jellyfin_create), enabled = !busy && name.text.isNotBlank()) {
                    busy = true
                    scope.launch {
                        val n = name.text.trim()
                        val id = runCatching { JellyfinApi.createPlaylist(n, ids) }.getOrNull()
                        busy = false
                        if (id == null) { toast(str(R.string.jellyfin_playlist_failed)); return@launch }
                        PlaylistChanges.changed()
                        onCreated(id, n)
                    }
                }
            }
        }
    }
}

/** A round-ended button: green filled, or outlined in grey. */
@Composable
internal fun Pill(text: String, outlined: Boolean = false, enabled: Boolean = true, white: Boolean = false, onClick: () -> Unit) {
    val bg = when { outlined -> Color.Transparent; white -> Color.White; else -> AppAccent }
    Text(
        text, color = if (outlined) AppText else Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (outlined) Modifier.border(BorderStroke(1.dp, AppMuted), RoundedCornerShape(50)) else Modifier)
            .pressable(0.96f, enabled = enabled, onClick = onClick).padding(horizontal = 28.dp, vertical = 13.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Add to playlist
// ---------------------------------------------------------------------------------------------

/**
 * "Add to playlist" for [tracks] (a song, an album): New playlist, a search, the user's playlists.
 * A song already in the one picked asks first ("Already added": Add anyway / Don't add; for
 * several, "Some already added": Add all / Add new ones).
 */
@Composable
internal fun AddToPlaylistSheet(tracks: List<JellyfinTrack>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf<Triple<JellyfinPlaylist, List<String>, List<String>>?>(null) }
    val lists = load(PlaylistChanges.version) { JellyfinApi.playlists() }
    val ids = tracks.map { it.id }

    fun add(p: JellyfinPlaylist, which: List<String>) {
        scope.launch {
            val ok = runCatching { JellyfinApi.addToPlaylist(p.id, which) }.isSuccess
            if (ok) { PlaylistChanges.changed(); toast(str(R.string.jellyfin_added_to, p.name)); onDismiss() }
            else toast(str(R.string.jellyfin_playlist_failed))
        }
    }

    fun pick(p: JellyfinPlaylist) {
        scope.launch {
            val inside = runCatching { JellyfinApi.playlistTracks(p.id).map { it.id }.toSet() }.getOrDefault(emptySet())
            val already = ids.filter { it in inside }
            if (already.isEmpty()) add(p, ids) else asking = Triple(p, ids, ids.filterNot { it in inside })
        }
    }

    if (creating) NewPlaylistDialog(ids, onDismiss = { creating = false }) { _, name ->
        creating = false
        toast(str(R.string.jellyfin_added_to, name))
        onDismiss()
    }
    asking?.let { (p, all, fresh) ->
        val single = all.size == 1 || fresh.isEmpty()
        AlertDialog(
            onDismissRequest = { asking = null },
            containerColor = Color(0xFF282828), titleContentColor = AppText, textContentColor = AppMuted,
            title = { Text(stringResource(if (single) R.string.jellyfin_already_added else R.string.jellyfin_some_already_added), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(if (single) R.string.jellyfin_already_in else R.string.jellyfin_some_already_in, p.name)) },
            confirmButton = {
                TextButton(onClick = { asking = null; add(p, all) }) {
                    Text(stringResource(if (single) R.string.jellyfin_add_anyway else R.string.jellyfin_add_all), color = AppAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = null; if (single) onDismiss() else add(p, fresh) }) {
                    Text(stringResource(if (single) R.string.jellyfin_dont_add else R.string.jellyfin_add_new_ones), color = AppText)
                }
            },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, stringResource(R.string.jellyfin_cancel), tint = AppText)
                }
                Text(stringResource(R.string.jellyfin_add_to_playlist), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Pill(stringResource(R.string.jellyfin_new_playlist), white = true) { creating = true }
            }
            FindField(find, { find = it }, stringResource(R.string.jellyfin_find_playlist), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val all = lists.value
            when {
                all == null && lists.failed -> Problem()
                all == null -> Loading()
                else -> LazyColumn(Modifier.weight(1f)) {
                    val shown = all.filter { find.isBlank() || it.name.contains(find.trim(), ignoreCase = true) }
                    items(shown.size, key = { shown[it].id }) { i ->
                        val p = shown[i]
                        PlaylistRow(p, stringResource(R.string.jellyfin_songs_count, p.songCount)) { pick(p) }
                    }
                }
            }
        }
    }
}

/** A grey search field ("Find in playlist", "Find playlist"). */
@Composable
internal fun FindField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = AppText, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = AppText.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
            BasicTextField(value, onValue, singleLine = true, textStyle = TextStyle(color = AppText, fontSize = 14.sp),
                cursorBrush = SolidColor(AppAccent), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Close, stringResource(R.string.jellyfin_clear), tint = AppText,
            modifier = Modifier.size(20.dp).pressable { onValue("") })
    }
}

// ---------------------------------------------------------------------------------------------
// The playlist page
// ---------------------------------------------------------------------------------------------

private enum class PlaylistSort(val label: Int) {
    CUSTOM(R.string.jellyfin_sort_custom), TITLE(R.string.jellyfin_sort_title), ARTIST(R.string.jellyfin_sort_artist), ALBUM(R.string.jellyfin_sort_album)
}

private class PlaylistPage(val playlist: JellyfinPlaylist, val tracks: List<JellyfinTrack>)

/**
 * A playlist, as the official page: its cover, name, description, owner, songs and length; add
 * songs, its menu (edit, change cover, queue, delete), shuffle and play; find in it and sort it;
 * each song's menu can take it out; and recommended songs to add at the foot.
 */
@Composable
fun JellyfinPlaylistScreen(nav: NavController, id: String, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val page = load(id, refresh, PlaylistChanges.version) {
        coroutineScope {
            val p = async { JellyfinApi.playlist(id) }
            val t = async { JellyfinApi.playlistTracks(id) }
            PlaylistPage(p.await() ?: JellyfinPlaylist(id, name, "", 0), t.await())
        }
    }
    var find by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PlaylistSort.CUSTOM) }
    var sorting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var changingCover by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val notes = PlaylistNotes.version

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        val p = page.value
        when {
            p == null && page.failed -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Problem() }
            p == null -> Column(Modifier.statusBarsPadding().padding(top = 64.dp)) { Loading() }
            else -> {
                val pl = p.playlist
                val from = JellyfinPlayback.From.playlist(id, pl.name)
                val shown = p.tracks.filter { t -> find.isBlank() || listOf(t.title, t.artist, t.album).any { it.contains(find.trim(), true) } }
                    .let { l ->
                        when (sort) {
                            PlaylistSort.CUSTOM -> l
                            PlaylistSort.TITLE -> l.sortedBy { it.title.lowercase() }
                            PlaylistSort.ARTIST -> l.sortedBy { it.artist.lowercase() }
                            PlaylistSort.ALBUM -> l.sortedBy { it.album.lowercase() }
                        }
                    }
                val coverForTint = ArtistPictures.of(id) ?: p.tracks.firstOrNull()?.let { trackCover(it, 300) } ?: JellyfinApi.imageUrl(id, pl.imageTag)
                val description = PlaylistNotes.description(context, id)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
                    item(key = "head") {
                        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(coverTint(coverForTint), AppBackground)))
                            .statusBarsPadding().padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(56.dp))
                            Box(Modifier.align(Alignment.CenterHorizontally)) { PlaylistArt(id, pl.imageTag, p.tracks, 240.dp) }
                            Text(pl.name, color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
                            if (description.isNotBlank()) Text(description, color = AppMuted, fontSize = 13.sp, lineHeight = 17.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                                Avatar(22.dp)
                                Text(JellyfinPref.userName, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                            }
                            val minutes = p.tracks.sumOf { it.durationMs } / 60_000
                            Text(listOf(context.resources.getQuantityString(R.plurals.jellyfin_songs_plural, p.tracks.size, p.tracks.size),
                                if (minutes >= 60) stringResource(R.string.jellyfin_hours_minutes, minutes / 60, minutes % 60) else stringResource(R.string.jellyfin_minutes, minutes))
                                .joinToString(" • "), color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconAction(Icons.Filled.AddCircleOutline, stringResource(R.string.jellyfin_add_to_this_playlist)) { JellyfinRoutes.addSongs(nav, id) }
                                IconAction(Icons.Filled.MoreVert, stringResource(R.string.jellyfin_more)) { menu = true }
                                Spacer(Modifier.weight(1f))
                                ShuffleButton { if (p.tracks.isNotEmpty()) JellyfinPlayback.play(p.tracks, p.tracks.indices.random(), from, shuffle = true) }
                                Spacer(Modifier.width(20.dp))
                                PlayCircle { if (p.tracks.isNotEmpty()) JellyfinPlayback.play(shown.ifEmpty { p.tracks }, 0, from) }
                            }
                        }
                    }
                    if (p.tracks.isNotEmpty()) item(key = "find") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FindField(find, { find = it }, stringResource(R.string.jellyfin_find_in_playlist), Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp).height(40.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.1f)).pressable { sorting = true }.padding(horizontal = 12.dp),
                            ) {
                                Text(stringResource(R.string.jellyfin_sort), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (p.tracks.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.jellyfin_start_building), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            Pill(stringResource(R.string.jellyfin_add_to_this_playlist), white = true) { JellyfinRoutes.addSongs(nav, id) }
                        }
                    }
                    itemsIndexed(shown, key = { i, t -> t.entryId.ifBlank { t.id + "#" + i } }) { _, t ->
                        TrackRow(t, nav, onRemove = if (t.entryId.isBlank()) null else ({
                            scope.launch {
                                if (runCatching { JellyfinApi.removeFromPlaylist(id, listOf(t.entryId)) }.isSuccess) {
                                    PlaylistChanges.changed(); toast(str(R.string.jellyfin_removed_from, pl.name))
                                } else toast(str(R.string.jellyfin_playlist_failed))
                            }
                        })) { JellyfinPlayback.play(shown, shown.indexOf(t), from) }
                    }
                    item(key = "recommended") { Recommended(id, pl.name, p.tracks) }
                }
                if (menu) PlaylistMenu(pl, onDismiss = { menu = false },
                    onAdd = { JellyfinRoutes.addSongs(nav, id) },
                    onEdit = { JellyfinRoutes.editPlaylist(nav, id) },
                    onCover = { changingCover = true },
                    onQueue = { p.tracks.forEach { JellyfinPlayback.enqueue(it, next = false) }; toast(str(R.string.jellyfin_added_to_queue, pl.name)) },
                    onDelete = { deleting = true })
                if (sorting) SortSheet(sort, { sort = it; sorting = false }) { sorting = false }
                if (changingCover) ChangePicture(id, stringResource(R.string.jellyfin_change_cover),
                    random = { ArtistPictures.fromRandom(context, id, p.tracks.map { trackCover(it, 1000) }) }) { changingCover = false }
                if (deleting) DeletePlaylistDialog(pl, onDismiss = { deleting = false }) {
                    deleting = false
                    nav.navigateUp()
                }
            }
        }
        BackButton(nav)
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(0.9f, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Icon(icon, label, tint = AppMuted, modifier = Modifier.size(28.dp))
    }
}

/** "Recommended songs": what the server's instant mix gives for the playlist, each with a + to add it. */
@Composable
private fun Recommended(id: String, name: String, inside: List<JellyfinTrack>) {
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    val have = inside.map { it.id }.toSet()
    val recs = load(id, round, inside.size) {
        val mix = if (inside.isEmpty()) emptyList() else runCatching { JellyfinApi.instantMix(id, 60) }.getOrDefault(emptyList())
        mix.ifEmpty { JellyfinApi.randomTracks(JellyfinPref.libraryId) }.filter { it.id !in have }.distinctBy { it.id }.shuffled().take(10)
    }.value ?: return
    if (recs.isEmpty()) return
    Column(Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.jellyfin_recommended_songs), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp))
        Text(stringResource(R.string.jellyfin_recommended_detail), color = AppMuted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        for (t in recs) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().pressable(0.98f) { JellyfinPlayback.play(recs, recs.indexOf(t), JellyfinPlayback.From.playlist(id, name)) }
                .padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        ) {
            Cover(trackCover(t), 48.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(t.title, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(44.dp).pressable(0.9f) {
                scope.launch {
                    if (runCatching { JellyfinApi.addToPlaylist(id, listOf(t.id)) }.isSuccess) PlaylistChanges.changed()
                    else toast(str(R.string.jellyfin_playlist_failed))
                }
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AddCircleOutline, stringResource(R.string.jellyfin_add_to_this_playlist), tint = AppText, modifier = Modifier.size(28.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Pill(stringResource(R.string.jellyfin_refresh), outlined = true) { round++ }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenu(
    p: JellyfinPlaylist, onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: () -> Unit, onCover: () -> Unit,
    onQueue: () -> Unit, onDelete: () -> Unit,
) {
    com.music.spotui.ui.components.LookSheet(onDismissRequest = onDismiss, containerColor = com.music.spotui.ui.theme.tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(p.name, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetItem(Icons.Filled.AddCircleOutline, stringResource(R.string.jellyfin_add_to_this_playlist)) { onDismiss(); onAdd() }
            SheetItem(Icons.Filled.Edit, stringResource(R.string.jellyfin_edit_playlist)) { onDismiss(); onEdit() }
            SheetItem(Icons.Filled.Image, stringResource(R.string.jellyfin_change_cover)) { onDismiss(); onCover() }
            SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.jellyfin_add_to_queue)) { onDismiss(); onQueue() }
            SheetItem(Icons.Outlined.Delete, stringResource(R.string.jellyfin_delete_playlist)) { onDismiss(); onDelete() }
        }
    }
}

@Composable
internal fun SheetItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = if (selected) AppAccent else AppMuted, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) AppAccent else AppText, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp).weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = AppAccent, modifier = Modifier.size(22.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(current: PlaylistSort, onPick: (PlaylistSort) -> Unit, onDismiss: () -> Unit) {
    com.music.spotui.ui.components.LookSheet(onDismissRequest = onDismiss, containerColor = com.music.spotui.ui.theme.tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.jellyfin_sort_by), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            for (s in PlaylistSort.entries) SheetItem(Icons.AutoMirrored.Filled.Sort, stringResource(s.label), selected = s == current) { onPick(s) }
        }
    }
}

/** "Delete playlist?": Cancel / Delete; deleted, its description and picture on the phone go too. */
@Composable
private fun DeletePlaylistDialog(p: JellyfinPlaylist, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF282828), titleContentColor = AppText, textContentColor = AppMuted,
        title = { Text(stringResource(R.string.jellyfin_delete_playlist_q), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.jellyfin_delete_playlist_detail, p.name)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (runCatching { JellyfinApi.deletePlaylist(p.id) }.isSuccess) {
                        PlaylistNotes.forget(context, p.id); ArtistPictures.remove(context, p.id)
                        PlaylistChanges.changed(); toast(str(R.string.jellyfin_playlist_deleted, p.name)); onDeleted()
                    } else { toast(str(R.string.jellyfin_delete_refused)); onDismiss() }
                }
            }) { Text(stringResource(R.string.jellyfin_delete), color = AppAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.jellyfin_cancel), color = AppText) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Add songs
// ---------------------------------------------------------------------------------------------

/** "Add to this playlist": search the library, a + on each song (a check once added); suggestions before typing. */
@Composable
fun JellyfinAddSongsScreen(nav: NavController, id: String) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { kotlinx.coroutines.delay(300); debounced = query.trim() }
    val added = remember { mutableStateListOf<String>() }
    val inside = load(id, "inside") { JellyfinApi.playlistTracks(id).map { it.id }.toSet() }.value.orEmpty()
    val results = load(debounced) {
        if (debounced.isBlank()) {
            val mix = if (inside.isEmpty()) emptyList() else runCatching { JellyfinApi.instantMix(id, 40) }.getOrDefault(emptyList())
            mix.ifEmpty { JellyfinApi.randomTracks(JellyfinPref.libraryId) }.take(40)
        } else JellyfinApi.searchTracks(JellyfinPref.libraryId, debounced, 60)
    }
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable { nav.navigateUp() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = AppText)
            }
            Text(stringResource(R.string.jellyfin_add_to_this_playlist), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = Color(0xFF121212), modifier = Modifier.size(24.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(stringResource(R.string.jellyfin_search), color = Color(0xFF535353), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color(0xFF121212), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(Color(0xFF121212)), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
            }
        }
        if (debounced.isBlank()) Text(stringResource(R.string.jellyfin_suggested), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        val list = results.value
        when {
            list == null && results.failed -> Problem()
            list == null -> Loading()
            list.isEmpty() -> Problem(stringResource(R.string.jellyfin_no_results_for, debounced))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
                items(list.size, key = { list[it].id + "#" + it }) { i ->
                    val t = list[i]
                    val done = t.id in added || t.id in inside
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)) {
                        Cover(trackCover(t), 48.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(t.title, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" • "), color = AppMuted, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(44.dp).pressable(0.9f) {
                            if (t.id in added) return@pressable
                            added += t.id
                            scope.launch {
                                if (runCatching { JellyfinApi.addToPlaylist(id, listOf(t.id)) }.isSuccess) PlaylistChanges.changed()
                                else { added -= t.id; toast(str(R.string.jellyfin_playlist_failed)) }
                            }
                        }, contentAlignment = Alignment.Center) {
                            Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline, stringResource(R.string.jellyfin_add_to_this_playlist),
                                tint = if (done) AppAccent else AppText, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Edit
// ---------------------------------------------------------------------------------------------

/**
 * "Edit playlist": its cover (Change image), name and description, the songs with a minus to take
 * one out and a handle to move it. Nothing changes until Save; the close button leaves it all.
 */
@Composable
fun JellyfinEditPlaylistScreen(nav: NavController, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val page = load(id, "edit") {
        coroutineScope {
            val p = async { JellyfinApi.playlist(id) }
            val t = async { JellyfinApi.playlistTracks(id) }
            PlaylistPage(p.await() ?: JellyfinPlaylist(id, "", "", 0), t.await())
        }
    }
    val p = page.value
    var name by remember(p) { mutableStateOf(p?.playlist?.name.orEmpty()) }
    var description by remember(p) { mutableStateOf(PlaylistNotes.description(context, id)) }
    var rows by remember(p) { mutableStateOf(p?.tracks.orEmpty().filter { it.entryId.isNotBlank() }) }
    var saving by remember { mutableStateOf(false) }
    var changingCover by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val drag = com.music.spotui.ui.screens.rememberPlaylistDrag(list)
    // The rows as songs of the app's own model (their entry id as the row id), for the drag helper.
    val asSongs = rows.map { t ->
        com.music.spotui.data.entity.SongsModel(id = 0, title = t.title, album = t.album, singer = t.artist,
            coverUri = trackCover(t), url = t.entryId, rowUid = t.entryId)
    }
    drag.rows = asSongs
    drag.onDrop = { move ->
        com.music.spotui.ui.screens.PlaylistReorder.applied(rows, move) { it.entryId }?.let { rows = it; true } ?: false
    }
    LaunchedEffect(rows) { drag.settle() }

    fun save() {
        val original = p ?: return
        saving = true
        scope.launch {
            val ok = runCatching {
                val n = name.trim()
                if (n.isNotBlank() && n != original.playlist.name) JellyfinApi.renamePlaylist(id, n)
                val keep = rows.map { it.entryId }
                val gone = original.tracks.map { it.entryId }.filter { it.isNotBlank() && it !in keep }
                if (gone.isNotEmpty()) JellyfinApi.removeFromPlaylist(id, gone)
                // Each entry moved to its place in turn, from the first: the server's order follows.
                val current = original.tracks.map { it.entryId }.filter { it in keep }.toMutableList()
                keep.forEachIndexed { i, e ->
                    if (current.getOrNull(i) != e) {
                        JellyfinApi.movePlaylistEntry(id, e, i)
                        current.remove(e); current.add(i, e)
                    }
                }
            }.isSuccess
            PlaylistNotes.setDescription(context, id, description)
            saving = false
            PlaylistChanges.changed()
            if (ok) nav.navigateUp() else toast(str(R.string.jellyfin_playlist_failed))
        }
    }

    BackHandler(enabled = saving) { }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable { nav.navigateUp() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, stringResource(R.string.jellyfin_cancel), tint = AppText)
            }
            Text(stringResource(R.string.jellyfin_edit_playlist), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.jellyfin_save), color = if (p != null && !saving) AppText else AppMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.pressable(0.95f, enabled = p != null && !saving) { save() }.padding(12.dp))
        }
        when {
            p == null && page.failed -> Problem()
            p == null -> Loading()
            else -> LazyColumn(state = list, modifier = Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "cover") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.pressable(0.97f) { changingCover = true }) { PlaylistArt(id, p.playlist.imageTag, rows, 160.dp) }
                        Text(stringResource(R.string.jellyfin_change_image), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp).pressable(0.95f) { changingCover = true })
                    }
                }
                item(key = "name") {
                    EditField(name, { name = it }, stringResource(R.string.jellyfin_playlist_name), big = true)
                }
                item(key = "description") {
                    EditField(description, { description = it }, stringResource(R.string.jellyfin_add_description))
                }
                val shownSongs = drag.order ?: asSongs
                items(shownSongs.size, key = { com.music.spotui.ui.screens.PlaylistReorder.key(shownSongs[it]) }) { i ->
                    val s = shownSongs[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = movableRow(drag, s, Color(0xFF2A2A2A))
                            .fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Box(Modifier.size(44.dp).pressable(0.9f) { rows = rows.filterNot { it.entryId == s.rowUid } }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.RemoveCircleOutline, stringResource(R.string.jellyfin_remove_from_playlist), tint = AppText, modifier = Modifier.size(24.dp))
                        }
                        Cover(s.coverUri, 44.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(s.title, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.singer, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(44.dp).then(drag.handle(s.rowUid)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DragHandle, stringResource(R.string.jellyfin_move), tint = AppMuted)
                        }
                    }
                }
            }
        }
    }
    if (changingCover) ChangePicture(id, stringResource(R.string.jellyfin_change_cover),
        random = { ArtistPictures.fromRandom(context, id, rows.map { trackCover(it, 1000) }) }) { changingCover = false }
}

@Composable
private fun EditField(value: String, onValue: (String) -> Unit, hint: String, big: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Box(contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = AppMuted, fontSize = if (big) 24.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
            BasicTextField(value, onValue, singleLine = big,
                textStyle = TextStyle(color = AppText, fontSize = if (big) 24.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal),
                cursorBrush = SolidColor(AppAccent), modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(AppMuted.copy(alpha = 0.5f)))
    }
}
