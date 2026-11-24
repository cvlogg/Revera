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
 * The Jellyfin row of Your Library (the app with Spotify): the Jellyfin Library page, or the
 * sign-in while no server is signed in to.
 */
@Composable
fun JellyfinScreen(navController: NavController) {
    if (JellyfinPref.signedIn) JellyfinLibraryScreen(navController) else JellyfinSignInPage()
}

/** The first screen of the Jellyfin-only app: the server's address, a user name and a password. */
@Composable
fun JellyfinSignInPage() {
    val scope = rememberCoroutineScope()
    val state = remember { JellyfinState(scope) }
    Box(Modifier.fillMaxSize().background(AppBackground)) { SignIn(state) }
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

