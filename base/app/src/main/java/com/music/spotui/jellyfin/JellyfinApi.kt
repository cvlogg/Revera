package com.music.spotui.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A music library ("folder") of the server: a view whose collection type is music. */
data class JellyfinLibrary(val id: String, val name: String)

data class JellyfinAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int,
    /** The cover's image tag, "" when the album has none. */
    val imageTag: String,
)

data class JellyfinTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val durationMs: Long,
    /** Disc × 1000 + track, as LocalTrack.trackNo has it (0 unknown). */
    val trackNo: Int,
    /** The id of the item whose primary image is the cover (the album, else the track itself), "" none. */
    val imageItemId: String,
    val imageTag: String,
)

/** The server said no to the user name and password (or to the saved token): signing in again is needed. */
class JellyfinAuthException(message: String) : IOException(message)

/**
 * A thin client for the user's own Jellyfin server (https://jellyfin.org, REST API), only what
 * the Jellyfin screen needs: sign in, the music libraries, their albums, an album's songs, a
 * search, and each song's audio address.
 *
 * The audio is asked as the original file (`/Audio/{id}/stream?static=true`), never transcoded:
 * the player then reads the file's own encoder delay and padding (LAME/Xing for MP3, iTunSMPB for
 * AAC, none needed for FLAC, Opus, WAV), which is what lets the songs of an album play into each
 * other with no gap, and seek anywhere.
 */
object JellyfinApi {
    private const val CLIENT = "Revera"
    private const val VERSION = "1.0"
    private const val PAGE = 200

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }

    /** "192.168.1.10:8096" → "http://192.168.1.10:8096"; a trailing slash or "/web/…" is dropped. Null: not an address. */
    fun normalizeServer(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        val url = s.toHttpUrlOrNull() ?: return null
        // The address of the web client, pasted from the browser, is the server's plus "/web/…".
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val webAt = segments.indexOfFirst { it.equals("web", true) }
        val kept = if (webAt >= 0) segments.take(webAt) else segments
        val base = url.newBuilder().query(null).fragment(null).encodedPath("/").apply { kept.forEach { addPathSegment(it) } }.build()
        return base.toString().trimEnd('/')
    }

    private fun authHeader(token: String?): String = buildString {
        append("MediaBrowser Client=\"").append(CLIENT).append("\", Device=\"")
        append(android.os.Build.MODEL.replace("\"", "").ifBlank { "Android" })
        append("\", DeviceId=\"").append(JellyfinPref.deviceId).append("\", Version=\"").append(VERSION).append('"')
        if (!token.isNullOrBlank()) append(", Token=\"").append(token).append('"')
    }

    private fun url(server: String, path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val b = (server.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder() ?: throw IOException("Not a server address")
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun call(request: Request): JsonElement {
        http.newCall(request).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw JellyfinAuthException("HTTP ${r.code}")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body?.string().orEmpty()
            return json.parseToJsonElement(body)
        }
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement =
        call(Request.Builder().url(url(JellyfinPref.server, path, query)).header("Authorization", authHeader(JellyfinPref.token)).get().build())

    /**
     * Signs in with a user name and password. Returns (server, user id, user name, access token).
     * Throws [JellyfinAuthException] on wrong credentials, an [IOException] when the server does not answer.
     */
    suspend fun signIn(serverInput: String, user: String, password: String): List<String> = withContext(Dispatchers.IO) {
        val server = normalizeServer(serverInput) ?: throw IOException("Not a server address")
        val body = buildJsonObject {
            put("Username", JsonPrimitive(user))
            put("Pw", JsonPrimitive(password))
        }.toString().toRequestBody("application/json".toMediaType())
        val r = call(Request.Builder().url(url(server, "/Users/AuthenticateByName")).header("Authorization", authHeader(null)).post(body).build()).jsonObject
        val token = r.str("AccessToken")
        val u = r["User"] as? JsonObject
        val id = u?.str("Id").orEmpty()
        if (token.isBlank() || id.isBlank()) throw JellyfinAuthException("No session in the answer")
        listOf(server, id, u?.str("Name").orEmpty().ifBlank { user }, token)
    }

    /** The user's music libraries (views whose collection type is music). */
    suspend fun libraries(): List<JellyfinLibrary> = withContext(Dispatchers.IO) {
        // /UserViews is the 10.9+ address; /Users/{id}/Views the older one.
        val r = try {
            get("/UserViews", mapOf("userId" to JellyfinPref.userId))
        } catch (e: JellyfinAuthException) {
            throw e
        } catch (e: IOException) {
            get("/Users/${JellyfinPref.userId}/Views")
        }
        items(r).filter { it.str("CollectionType").equals("music", true) }
            .map { JellyfinLibrary(it.str("Id"), it.str("Name")) }
    }

    private fun itemQuery(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf("userId" to JellyfinPref.userId, "recursive" to "true", "enableTotalRecordCount" to "true") + extra

    /** The library's albums by name, a page from [start]; the second value is how many there are in all. */
    suspend fun albums(libraryId: String, start: Int = 0, search: String = ""): Pair<List<JellyfinAlbum>, Int> = withContext(Dispatchers.IO) {
        val q = itemQuery(
            "parentId" to libraryId, "includeItemTypes" to "MusicAlbum",
            "sortBy" to "SortName", "sortOrder" to "Ascending",
            "startIndex" to start.toString(), "limit" to PAGE.toString(),
            "fields" to "PrimaryImageAspectRatio", "enableImageTypes" to "Primary",
        ) + (if (search.isNotBlank()) mapOf("searchTerm" to search.trim()) else emptyMap())
        val r = get("/Items", q)
        items(r).map { it.toAlbum() } to ((r as? JsonObject)?.int("TotalRecordCount") ?: 0)
    }

    /** An album's songs, in disc and track order. */
    suspend fun albumTracks(albumId: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val r = get("/Items", itemQuery(
            "parentId" to albumId, "includeItemTypes" to "Audio",
            "sortBy" to "ParentIndexNumber,IndexNumber,SortName", "sortOrder" to "Ascending",
        ))
        items(r).map { it.toTrack() }
    }

    /** Songs of the library matching [search], at most a page. */
    suspend fun searchTracks(libraryId: String, search: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val r = get("/Items", itemQuery(
            "parentId" to libraryId, "includeItemTypes" to "Audio", "searchTerm" to search.trim(),
            "sortBy" to "SortName", "limit" to PAGE.toString(),
        ))
        items(r).map { it.toTrack() }
    }

    /** A random pick of the library's songs (the screen's shuffle button), at most a page. */
    suspend fun randomTracks(libraryId: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val r = get("/Items", itemQuery("parentId" to libraryId, "includeItemTypes" to "Audio", "sortBy" to "Random", "limit" to PAGE.toString()))
        items(r).map { it.toTrack() }
    }

    /**
     * The song's original file, as stored on the server. The token goes in the address (the player
     * sends no headers of its own): it is the session's, revocable on the server.
     */
    fun streamUrl(trackId: String): String = url(JellyfinPref.server, "/Audio/$trackId/stream",
        mapOf("static" to "true", "deviceId" to JellyfinPref.deviceId, "ApiKey" to JellyfinPref.token)).toString()

    /** An item's primary image, [size] px high at most; "" when it has none. */
    fun imageUrl(itemId: String, tag: String, size: Int = 400): String =
        if (itemId.isBlank() || tag.isBlank()) ""
        else url(JellyfinPref.server, "/Items/$itemId/Images/Primary",
            mapOf("tag" to tag, "fillHeight" to size.toString(), "fillWidth" to size.toString(), "quality" to "90")).toString()

    private fun items(r: JsonElement): List<JsonObject> =
        ((r as? JsonObject)?.get("Items") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.primaryTag(): String = ((this["ImageTags"] as? JsonObject)?.get("Primary") as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.toAlbum() = JellyfinAlbum(
        id = str("Id"), name = str("Name"),
        artist = str("AlbumArtist").ifBlank { ((this["Artists"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty() },
        year = int("ProductionYear") ?: 0, imageTag = primaryTag(),
    )

    private fun JsonObject.toTrack(): JellyfinTrack {
        val artists = (this["Artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val albumTag = str("AlbumPrimaryImageTag")
        val ownTag = primaryTag()
        val disc = int("ParentIndexNumber") ?: 0
        val no = int("IndexNumber") ?: 0
        return JellyfinTrack(
            id = str("Id"), title = str("Name"),
            artist = artists.joinToString(", ").ifBlank { str("AlbumArtist") },
            album = str("Album"), albumId = str("AlbumId"),
            // Ticks are 100 ns.
            durationMs = ((this["RunTimeTicks"] as? JsonPrimitive)?.longOrNull ?: 0L) / 10_000L,
            trackNo = if (no > 0) disc.coerceAtLeast(1) * 1000 + no else 0,
            imageItemId = if (albumTag.isNotBlank()) str("AlbumId") else if (ownTag.isNotBlank()) str("Id") else "",
            imageTag = albumTag.ifBlank { ownTag },
        )
    }
}
