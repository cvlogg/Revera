package com.music.spotui.jellyfin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.jellyfin.JellyfinAlbum
import com.music.spotui.jellyfin.JellyfinApi
import com.music.spotui.jellyfin.JellyfinPlayback
import com.music.spotui.jellyfin.JellyfinPref
import com.music.spotui.jellyfin.JellyfinTrack
import com.music.spotui.ui.components.FindInListField
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.headerTint

/**
 * The Jellyfin screen (both looks): sign in to the user's own server, pick its music library,
 * browse its albums or search it, and play through LocalPlayer ([JellyfinPlayback]).
 */
@Composable
fun JellyfinScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { JellyfinState(scope) }
    LaunchedEffect(JellyfinPref.signedIn) { state.start(context) }
    BackHandler(enabled = state.album != null) { state.closeAlbum() }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        when {
            !JellyfinPref.signedIn -> SignIn(state)
            state.album != null -> AlbumPage(state, state.album!!)
            else -> Browse(state)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(40.dp).clip(CircleShape)
                .background(AppBackground.copy(alpha = 0.4f))
                .pressable { if (state.album != null) state.closeAlbum() else navController.navigateUp() },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = AppText)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(headerTint(Color(0xFF5A3D8A), 0f), AppBackground)))
            .statusBarsPadding().padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(title, color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun problemText(p: JellyfinState.Problem): String? = when (p) {
    JellyfinState.Problem.NONE -> null
    JellyfinState.Problem.UNREACHABLE -> stringResource(R.string.jellyfin_error_unreachable)
    JellyfinState.Problem.WRONG_LOGIN -> stringResource(R.string.jellyfin_error_login)
    JellyfinState.Problem.NO_LIBRARY -> stringResource(R.string.jellyfin_error_no_library)
}

@Composable
private fun SignIn(state: JellyfinState) {
    val context = LocalContext.current
    var server by rememberSaveable { mutableStateOf(JellyfinPref.server) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppText, unfocusedTextColor = AppText, focusedBorderColor = AppAccent,
        unfocusedBorderColor = AppMuted, focusedLabelColor = AppAccent, unfocusedLabelColor = AppMuted, cursorColor = AppAccent,
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header(stringResource(R.string.jellyfin_title), stringResource(R.string.jellyfin_sign_in_subtitle)) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.jellyfin_server)) },
                    placeholder = { Text("http://192.168.1.10:8096") }, singleLine = true, colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.jellyfin_user)) },
                    singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.jellyfin_password)) },
                    singleLine = true, colors = colors, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                // Said before the password goes: over http it crosses the network as it is typed.
                if (JellyfinApi.normalizeServer(server)?.startsWith("http://") == true)
                    Text(stringResource(R.string.jellyfin_http_note), color = AppMuted, fontSize = 12.sp)
                problemText(state.problem)?.let { Text(it, color = AppAccent, fontSize = 13.sp) }
                val ready = !state.busy && JellyfinApi.normalizeServer(server) != null && user.isNotBlank()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(top = 8.dp).height(48.dp).fillMaxWidth().clip(RoundedCornerShape(50))
                        .background(if (ready) AppText else AppMuted)
                        .pressable(0.96f, enabled = ready) { state.signIn(context, server, user.trim(), password) },
                ) {
                    if (state.busy) CircularProgressIndicator(color = AppBackground, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.jellyfin_sign_in), color = AppBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
    label, color = if (selected) Color.Black else AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.clip(RoundedCornerShape(50))
        .background(if (selected) AppAccent else com.music.spotui.ui.theme.tone(0xFF2A2A2A))
        .pressable(0.95f, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 7.dp),
)

@Composable
private fun Browse(state: JellyfinState) {
    val context = LocalContext.current
    val host = remember(JellyfinPref.server) { runCatching { android.net.Uri.parse(JellyfinPref.server).host }.getOrNull().orEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
        item { Header(stringResource(R.string.jellyfin_title), listOf(JellyfinPref.userName, host).filter { it.isNotBlank() }.joinToString(" · ")) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.jellyfin_sign_out), color = AppMuted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressable(0.95f) { state.signOut(context) }.padding(vertical = 6.dp))
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_player_shuffle), stringResource(R.string.play_mode_item_shuffle_title), tint = AppText,
                    modifier = Modifier.size(28.dp).pressable { state.shuffleLibrary(context) })
            }
        }
        val libs = state.libraries.orEmpty()
        if (libs.size > 1) item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) { for (l in libs) Chip(l.name, l.id == JellyfinPref.libraryId) { state.pickLibrary(context, l.id) } }
        }
        item {
            FindInListField(state.query, { state.search(context, it) }, stringResource(R.string.jellyfin_search_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (state.problem != JellyfinState.Problem.NONE) item {
            problemText(state.problem)?.let { msg ->
                Text(msg, color = AppMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp))
            }
        }
        if (state.query.isNotBlank()) {
            searchResults(state)
        } else {
            if (state.albums.isEmpty() && state.busy) item { Loading() }
            itemsIndexed(state.albums, key = { _, a -> "album-" + a.id }) { i, a ->
                AlbumRow(a) { state.open(context, a) }
                if (i == state.albums.lastIndex) LaunchedEffect(state.albums.size) { state.more(context) }
            }
        }
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = AppAccent)
}

private fun LazyListScope.searchResults(state: JellyfinState) {
    val tracks = state.foundTracks
    val albums = state.foundAlbums
    if (tracks == null && albums == null) { item { Loading() }; return }
    if (tracks.isNullOrEmpty() && albums.isNullOrEmpty()) item {
        Text(stringResource(R.string.jellyfin_no_results), color = AppMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp))
    }
    if (!tracks.isNullOrEmpty()) {
        item { SectionTitle(stringResource(R.string.jellyfin_songs)) }
        itemsIndexed(tracks, key = { _, t -> "song-" + t.id }) { i, t -> TrackRow(t, showCover = true) { JellyfinPlayback.play(tracks, i) } }
    }
    if (!albums.isNullOrEmpty()) {
        item { SectionTitle(stringResource(R.string.jellyfin_albums)) }
        itemsIndexed(albums, key = { _, a -> "found-" + a.id }) { _, a ->
            val context = LocalContext.current
            AlbumRow(a) { state.open(context, a) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Cover(url: String, size: Int) {
    GlideImage(
        model = url.ifBlank { R.drawable.local_files_cover }, contentDescription = null, contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.local_files_cover),
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun AlbumRow(a: JellyfinAlbum, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.97f, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Cover(JellyfinApi.imageUrl(a.id, a.imageTag, 160), 56)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(a.name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(a.artist.ifBlank { null }, a.year.takeIf { it > 0 }?.toString()).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(t: JellyfinTrack, showCover: Boolean, onClick: () -> Unit) {
    val currentId by com.music.spotui.di.Graph.currentSongState.songId
    val current = currentId == JellyfinPlayback.songIdFor(t)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.97f, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (showCover) Cover(JellyfinApi.imageUrl(t.imageItemId, t.imageTag, 160), 48)
        else Text(if (t.trackNo > 0) (t.trackNo % 1000).toString() else "", color = AppMuted, fontSize = 14.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(t.title, color = if (current) AppAccent else AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (showCover) listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · ") else t.artist
            if (sub.isNotBlank()) Text(sub, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.jellyfin_add_to_queue), tint = AppText,
            modifier = Modifier.size(24.dp).pressable(0.9f) {
                JellyfinPlayback.enqueue(t, next = false)
                android.widget.Toast.makeText(com.music.spotui.di.Graph.app,
                    com.music.spotui.di.Graph.app.getString(R.string.jellyfin_added_to_queue, t.title), android.widget.Toast.LENGTH_SHORT).show()
            })
    }
}

@Composable
private fun AlbumPage(state: JellyfinState, a: JellyfinAlbum) {
    val tracks = state.albumTracks
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace())) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(headerTint(Color(0xFF5A3D8A), 0f), AppBackground)))
                    .statusBarsPadding().padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(56.dp))
                Box(Modifier.align(Alignment.CenterHorizontally)) { Cover(JellyfinApi.imageUrl(a.id, a.imageTag, 600), 200) }
                Text(a.name, color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                val sub = listOfNotNull(a.artist.ifBlank { null }, a.year.takeIf { it > 0 }?.toString()).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = AppMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_player_shuffle), stringResource(R.string.play_mode_item_shuffle_title), tint = AppText,
                        modifier = Modifier.size(28.dp).pressable { tracks?.takeIf { it.isNotEmpty() }?.let { JellyfinPlayback.play(it, it.indices.random(), shuffle = true) } })
                    Spacer(Modifier.width(20.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp).pressable(0.92f) { tracks?.let { JellyfinPlayback.play(it, 0) } }
                            .clip(CircleShape).background(AppAccent),
                    ) {
                        com.music.spotui.ui.components.PlayPauseIcon(false, stringResource(R.string.content_description_play_button),
                            tint = Color.Black, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
        if (state.problem != JellyfinState.Problem.NONE) item {
            problemText(state.problem)?.let { msg ->
                Text(msg, color = AppMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
        if (tracks == null) item { Loading() }
        else itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, showCover = false) { JellyfinPlayback.play(tracks, i) } }
    }
}
