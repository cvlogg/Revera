package com.music.spotui.jellyfin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.myspoti.engine.R
import com.music.spotui.jellyfin.ArtistPictures
import com.music.spotui.ui.components.pressable
import kotlinx.coroutines.launch

private val DialogBg = Color(0xFF202122)
private val OptionBg = Color(0xFF393B3B)
private val OptionText = Color(0xFFDCE3E0)
private val TonalBg = Color(0xFFC3CEC8)
private val TonalText = Color(0xFF2B3330)

/**
 * "Change artist picture": a random cover of theirs, a file, a photo, an address on the web, or
 * back to the server's picture (see [ArtistPictures]).
 */
@Composable
internal fun ChangeArtistPicture(artistId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ChangePicture(artistId, stringResource(R.string.jellyfin_change_artist_picture),
        random = { ArtistPictures.fromRandomCover(context, artistId) }, onDismiss = onDismiss)
}

/** The same choices for any item kept by [ArtistPictures] under [itemId] (an artist, a playlist). */
@Composable
internal fun ChangePicture(itemId: String, title: String, random: (suspend () -> Boolean)?, onDismiss: () -> Unit) {
    val artistId = itemId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var askingUrl by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Boolean) {
        busy = true
        scope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            busy = false
            android.widget.Toast.makeText(context, context.getString(if (ok) R.string.jellyfin_picture_changed else R.string.jellyfin_picture_failed),
                android.widget.Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { ArtistPictures.fromUri(context, artistId, uri) }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) run { ArtistPictures.fromUri(context, artistId, uri) }
    }

    if (askingUrl) {
        UrlDialog(onDismiss = { askingUrl = false }) { url -> askingUrl = false; run { ArtistPictures.fromUrl(context, artistId, url) } }
        return
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DialogBg).padding(24.dp)) {
            Text(title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp,
                modifier = Modifier.padding(bottom = 22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (random != null) Option(Icons.Outlined.AutoAwesome, stringResource(R.string.jellyfin_picture_random), busy) {
                    run { random() }
                }
                Option(Icons.Outlined.FilePresent, stringResource(R.string.jellyfin_picture_file), busy) { file.launch(arrayOf("image/*")) }
                Option(Icons.Outlined.Image, stringResource(R.string.jellyfin_picture_photo), busy) {
                    photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Option(Icons.Filled.Public, stringResource(R.string.jellyfin_picture_url), busy) { askingUrl = true }
                if (ArtistPictures.has(artistId)) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(54.dp).clip(RoundedCornerShape(50)).background(TonalBg)
                        .pressable(0.97f, enabled = !busy) { ArtistPictures.remove(context, artistId); onDismiss() }
                        .padding(start = 20.dp, end = 26.dp),
                ) {
                    Icon(Icons.Outlined.Delete, null, tint = TonalText, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.jellyfin_picture_remove), color = TonalText, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 26.dp), contentAlignment = Alignment.CenterEnd) {
                if (busy) CircularProgressIndicator(color = OptionText, strokeWidth = 2.dp, modifier = Modifier.size(24.dp).align(Alignment.CenterStart))
                Text(stringResource(R.string.jellyfin_cancel), color = OptionText, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressable(0.95f, enabled = !busy, onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, label: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(50)).background(OptionBg)
            .pressable(0.98f, enabled = !busy, onClick = onClick).padding(horizontal = 20.dp),
    ) {
        Icon(icon, null, tint = OptionText, modifier = Modifier.size(26.dp))
        Text(label, color = OptionText, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

/** "From URL": an address of an image on the web. */
@Composable
private fun UrlDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DialogBg).padding(24.dp)) {
            Text(stringResource(R.string.jellyfin_picture_url), color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
            OutlinedTextField(
                url, { url = it }, singleLine = true, placeholder = { Text("https://…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = TonalBg, unfocusedBorderColor = OptionBg, cursorColor = TonalBg),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                Text(stringResource(R.string.jellyfin_cancel), color = OptionText, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressable(0.95f, onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp))
                val ok = url.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }
                Text(stringResource(R.string.jellyfin_ok), color = if (ok) OptionText else OptionText.copy(alpha = 0.4f), fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressable(0.95f, enabled = ok) { onDone(url.trim()) }
                        .padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}
