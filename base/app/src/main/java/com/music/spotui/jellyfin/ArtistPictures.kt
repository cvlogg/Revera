package com.music.spotui.jellyfin

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Artist pictures chosen on this phone ("Change artist picture"), used in place of the server's
 * everywhere the artist shows. Kept in the app's private storage, one file per artist; the server
 * is not changed (setting an image there needs an administrator account).
 */
object ArtistPictures {
    private const val DIR = "artist_pictures"
    /** Images bigger than this are refused (a wrong pick, not a picture). */
    private const val MAX_BYTES = 25L * 1024 * 1024

    /** Bumped at each change: pictures shown are read again. */
    var version by mutableIntStateOf(0)
        private set

    private val http by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }

    private fun dir(c: Context) = File(c.filesDir, DIR).apply { mkdirs() }

    /** The picture chosen for [artistId], as an address the image loader reads; null with none. */
    fun of(artistId: String): String? {
        val v = version
        if (artistId.isBlank()) return null
        if (cacheVersion != v) { cache.clear(); cacheVersion = v }
        return cache.getOrPut(artistId) { find(artistId) ?: "" }.ifBlank { null }
    }

    private val cache = HashMap<String, String>()
    private var cacheVersion = -1

    private fun find(artistId: String): String? {
        // A new name at each change, so the image loader never shows the one before from its cache.
        val f = dir(com.music.spotui.di.Graph.app).listFiles { x -> x.name.startsWith("$artistId-") }?.maxByOrNull { it.lastModified() }
        return f?.let { Uri.fromFile(it).toString() }
    }

    fun has(artistId: String): Boolean = of(artistId) != null

    /** Keeps [bytes] as [artistId]'s picture when they are an image. False otherwise. */
    private fun save(c: Context, artistId: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val d = dir(c)
        d.listFiles { x -> x.name.startsWith("$artistId-") }?.forEach { it.delete() }
        File(d, "$artistId-${System.currentTimeMillis()}.img").writeBytes(bytes)
        version++
        return true
    }

    /** From a file or a photo the user picked. */
    suspend fun fromUri(c: Context, artistId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            c.contentResolver.openInputStream(uri)?.use { i -> i.readBytes().takeIf { it.size <= MAX_BYTES } }
        }.getOrNull() ?: return@withContext false
        withContext(Dispatchers.Main) { save(c, artistId, bytes) }
    }

    /** From an address on the web (or the server: a cover chosen at random). */
    suspend fun fromUrl(c: Context, artistId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return@withContext false
        val bytes = runCatching {
            http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                if (!r.isSuccessful) null
                else r.body?.let { b -> if (b.contentLength() > MAX_BYTES) null else b.bytes().takeIf { it.size <= MAX_BYTES } }
            }
        }.getOrNull() ?: return@withContext false
        withContext(Dispatchers.Main) { save(c, artistId, bytes) }
    }

    /** "Generate from tracks (Random)": the cover of one of the artist's albums (or songs), picked at random. */
    suspend fun fromRandomCover(c: Context, artistId: String): Boolean {
        val covers = runCatching { JellyfinApi.artistAlbums(artistId).filter { it.imageTag.isNotBlank() }
            .map { JellyfinApi.imageUrl(it.id, it.imageTag, 1000) } }.getOrDefault(emptyList())
            .ifEmpty {
                runCatching { JellyfinApi.artistTracks(artistId).filter { it.imageTag.isNotBlank() }
                    .map { JellyfinApi.imageUrl(it.imageItemId, it.imageTag, 1000) }.distinct() }.getOrDefault(emptyList())
            }
        val pick = covers.randomOrNull() ?: return false
        return fromUrl(c, artistId, pick)
    }

    /** One of [urls] at random (a playlist's covers). */
    suspend fun fromRandom(c: Context, id: String, urls: List<String>): Boolean {
        val pick = urls.filter { it.isNotBlank() }.distinct().randomOrNull() ?: return false
        return fromUrl(c, id, pick)
    }

    /** Back to the server's picture. */
    fun remove(c: Context, artistId: String) {
        dir(c).listFiles { x -> x.name.startsWith("$artistId-") }?.forEach { it.delete() }
        version++
    }
}
