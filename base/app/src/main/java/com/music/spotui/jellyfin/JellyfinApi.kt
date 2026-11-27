package com.music.spotui.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
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
    /** The first album artist's id, "" unknown. */
    val artistId: String = "",
    /** The album's genres (id to name), when the list was asked with them. */
    val genres: List<Pair<String, String>> = emptyList(),
    val favorite: Boolean = false,
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
    /** The first artist's id (the song's, else the album's), "" unknown. */
    val artistId: String = "",
    /** How many times this user played it, as the server counts. */
    val playCount: Int = 0,
    /** Its entry in a playlist (the same song twice is two entries), "" outside a playlist. */
    val entryId: String = "",
    /** In this user's Liked Songs (a favourite on the server). */
    val favorite: Boolean = false,
)

data class JellyfinArtist(
    val id: String,
    val name: String,
    val imageTag: String,
    val backdropTag: String = "",
    val favorite: Boolean = false,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val overview: String = "",
)

data class JellyfinGenre(val id: String, val name: String, val imageItemId: String, val imageTag: String)

data class JellyfinPlaylist(val id: String, val name: String, val imageTag: String, val songCount: Int)

/** The server said no to the user name and password (or to the saved token): signing in again is needed. */
class JellyfinAuthException(message: String) : IOException(message)

/**
 * A thin client for the user's own Jellyfin server (https://jellyfin.org, REST API): sign in, the
 * music libraries, albums, artists, genres, playlists, searches, favourites, play reports, and
 * each song's audio address.
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
            return if (body.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(body)
        }
    }

    private fun request(path: String, query: Map<String, String> = emptyMap()) =
        Request.Builder().url(url(JellyfinPref.server, path, query)).header("Authorization", authHeader(JellyfinPref.token))

    private fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement = call(request(path, query).get().build())

    private fun post(path: String, body: JsonObject? = null): JsonElement =
        call(request(path).post((body?.toString() ?: "").toRequestBody("application/json".toMediaType())).build())

    private fun delete(path: String): JsonElement = call(request(path).delete().build())

    /** [newer] first; on a 404 (an older server), [older]. */
    private fun <T> eitherRoute(newer: () -> T, older: () -> T): T = try {
        newer()
    } catch (e: JellyfinAuthException) {
        throw e
    } catch (e: IOException) {
        older()
    }

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
        val r = eitherRoute({ get("/UserViews", mapOf("userId" to JellyfinPref.userId)) }, { get("/Users/${JellyfinPref.userId}/Views") })
        items(r).filter { it.str("CollectionType").equals("music", true) }
            .map { JellyfinLibrary(it.str("Id"), it.str("Name")) }
    }

    private fun itemQuery(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf("userId" to JellyfinPref.userId, "recursive" to "true", "enableTotalRecordCount" to "true") + extra

    private fun lib(): Map<String, String> =
        JellyfinPref.libraryId.takeIf { it.isNotBlank() }?.let { mapOf("parentId" to it) }.orEmpty()

    // ---- albums ----

    /** The library's albums by name, a page from [start]; the second value is how many there are in all. */
    suspend fun albums(libraryId: String, start: Int = 0, search: String = ""): Pair<List<JellyfinAlbum>, Int> = withContext(Dispatchers.IO) {
        val q = itemQuery(
            "parentId" to libraryId, "includeItemTypes" to "MusicAlbum",
            "sortBy" to "SortName", "sortOrder" to "Ascending",
            "startIndex" to start.toString(), "limit" to PAGE.toString(),
        ) + (if (search.isNotBlank()) mapOf("searchTerm" to search.trim()) else emptyMap())
        val r = get("/Items", q)
        items(r).map { it.toAlbum() } to ((r as? JsonObject)?.int("TotalRecordCount") ?: 0)
    }

    /** Albums of the library in an order the server knows: "DatePlayed", "DateCreated", "PlayCount", "Random". */
    suspend fun albumsBy(sort: String, limit: Int = 20, played: Boolean = false, genreId: String = "", years: String = "", favorites: Boolean = false): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        // Several genres (a tag like "Emoviolence, Deathcore" split by the app): each asked on its
        // own and merged here. A list in one filter was ignored by the server (the whole library came back).
        val ids = genreId.split(',').filter { it.isNotBlank() }
        if (ids.size > 1) {
            val all = ids.flatMap { albumsBy(sort, limit, played, it, years, favorites) }.distinctBy { it.id }
            return@withContext when (sort) {
                "SortName" -> all.sortedBy { it.name.lowercase() }
                "Random" -> all.shuffled()
                else -> all
            }.take(limit)
        }
        val q = itemQuery(
            "includeItemTypes" to "MusicAlbum", "sortBy" to "$sort,SortName",
            "sortOrder" to (if (sort == "SortName") "Ascending" else "Descending"), "limit" to limit.toString(),
        ) + lib() +
            (if (played) mapOf("filters" to "IsPlayed") else emptyMap()) +
            (if (favorites) mapOf("filters" to "IsFavorite") else emptyMap()) +
            (if (genreId.isNotBlank()) mapOf("genreIds" to genreId) else emptyMap()) +
            (if (years.isNotBlank()) mapOf("years" to years) else emptyMap())
        items(get("/Items", q)).map { it.toAlbum() }
    }

    /**
     * The albums of the songs played last, newest first: an album counts as played as soon as one
     * of its songs is (the server marks an album played only once all its songs are).
     */
    suspend fun recentlyPlayedAlbums(limit: Int = 12): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        val songs = items(get("/Items", itemQuery(
            "includeItemTypes" to "Audio", "sortBy" to "DatePlayed", "sortOrder" to "Descending",
            "filters" to "IsPlayed", "limit" to "120",
        ) + lib())).map { it.toTrack() }
        val ids = songs.map { it.albumId }.filter { it.isNotBlank() }.distinct().take(limit)
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = items(get("/Items", mapOf("userId" to JellyfinPref.userId, "ids" to ids.joinToString(",")))).associate { it.str("Id") to it.toAlbum() }
        ids.mapNotNull { byId[it] }
    }

    suspend fun album(id: String): JellyfinAlbum? = withContext(Dispatchers.IO) {
        items(get("/Items", mapOf("userId" to JellyfinPref.userId, "ids" to id))).firstOrNull()?.toAlbum()
    }

    /** An album's songs, in disc and track order. */
    suspend fun albumTracks(albumId: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val r = get("/Items", itemQuery(
            "parentId" to albumId, "includeItemTypes" to "Audio",
            "sortBy" to "ParentIndexNumber,IndexNumber,SortName", "sortOrder" to "Ascending",
        ))
        items(r).map { it.toTrack() }
    }

    // ---- songs ----

    /** Songs of the library matching [search], at most a page. */
    suspend fun searchTracks(libraryId: String, search: String, limit: Int = PAGE): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val r = get("/Items", itemQuery(
            "parentId" to libraryId, "includeItemTypes" to "Audio", "searchTerm" to search.trim(),
            "sortBy" to "SortName", "limit" to limit.toString(),
        ))
        items(r).map { it.toTrack() }
    }

    /** A random pick of the library's songs (or of a genre's, or the favourites), at most a page. */
    suspend fun randomTracks(libraryId: String, genreId: String = "", favorites: Boolean = false): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        val ids = genreId.split(',').filter { it.isNotBlank() }
        if (ids.size > 1) {
            return@withContext ids.flatMap { randomTracks(libraryId, it, favorites) }.distinctBy { it.id }.shuffled().take(PAGE)
        }
        val q = itemQuery("parentId" to libraryId, "includeItemTypes" to "Audio", "sortBy" to "Random", "limit" to PAGE.toString()) +
            (if (genreId.isNotBlank()) mapOf("genreIds" to genreId) else emptyMap()) +
            (if (favorites) mapOf("filters" to "IsFavorite") else emptyMap())
        items(get("/Items", q)).map { it.toTrack() }
    }

    /** The songs this user liked (favourites), by name. */
    suspend fun favoriteTracks(): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("includeItemTypes" to "Audio", "filters" to "IsFavorite", "sortBy" to "SortName", "limit" to "1000") + lib())).map { it.toTrack() }
    }

    /** A random pick of albums with their genres: what the search page's genre tiles and images are made from. */
    suspend fun albumsWithGenres(limit: Int = 150): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("includeItemTypes" to "MusicAlbum", "sortBy" to "Random", "limit" to limit.toString(),
            "fields" to "Genres") + lib())).map { it.toAlbum() }
    }

    /** The songs this user played most, most first. */
    suspend fun mostPlayedTracks(limit: Int = 50): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("includeItemTypes" to "Audio", "sortBy" to "PlayCount,SortName", "sortOrder" to "Descending",
            "filters" to "IsPlayed", "limit" to limit.toString()) + lib())).map { it.toTrack() }
    }

    // ---- artists ----

    /** Album artists of the library by name (or matching [search]). */
    suspend fun artists(search: String = "", limit: Int = PAGE, start: Int = 0, favorites: Boolean = false, sort: String = "SortName"): List<JellyfinArtist> = withContext(Dispatchers.IO) {
        val q = mapOf("userId" to JellyfinPref.userId, "sortBy" to sort, "sortOrder" to "Ascending",
            "limit" to limit.toString(), "startIndex" to start.toString(), "fields" to "ChildCount") + lib() +
            (if (search.isNotBlank()) mapOf("searchTerm" to search.trim()) else emptyMap()) +
            (if (favorites) mapOf("isFavorite" to "true") else emptyMap())
        items(get("/Artists/AlbumArtists", q)).map { it.toArtist() }
    }

    suspend fun artist(id: String): JellyfinArtist? = withContext(Dispatchers.IO) {
        val o = eitherRoute({ get("/Items/$id", mapOf("userId" to JellyfinPref.userId)) }, { get("/Users/${JellyfinPref.userId}/Items/$id") }) as? JsonObject
        o?.toArtist()
    }

    /** The artist's albums, newest first. */
    suspend fun artistAlbums(artistId: String): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("albumArtistIds" to artistId, "includeItemTypes" to "MusicAlbum",
            "sortBy" to "ProductionYear,PremiereDate,SortName", "sortOrder" to "Descending"))).map { it.toAlbum() }
    }

    /** The artist's songs this user played most, then the rest by name: the artist page's "Popular". */
    suspend fun artistTopTracks(artistId: String, limit: Int = 10): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("artistIds" to artistId, "includeItemTypes" to "Audio",
            "sortBy" to "PlayCount,SortName", "sortOrder" to "Descending", "limit" to limit.toString()))).map { it.toTrack() }
    }

    /** All the artist's songs, by album then track (the artist page's play and shuffle). */
    suspend fun artistTracks(artistId: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("artistIds" to artistId, "includeItemTypes" to "Audio",
            "sortBy" to "ProductionYear,Album,ParentIndexNumber,IndexNumber", "sortOrder" to "Ascending", "limit" to "500"))).map { it.toTrack() }
    }

    // ---- genres, playlists ----

    /** "Emoviolence, Deathcore" → the two genres it names (the server splits tags on / | ; \\ only). */
    fun genreParts(name: String): List<String> =
        name.split(',', ';', '/', '|', '\\').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    private var genreCache: Pair<Long, List<JellyfinGenre>>? = null

    /**
     * Every server genre naming [name], alone or in a tag of several ("Crust Punk" and "Crust Punk,
     * Sludge Metal"), as ids separated by commas; "" with none. The library's whole genre list is
     * read (kept 10 minutes), not a sample.
     */
    suspend fun genreIdsNamed(name: String): String {
        val now = System.currentTimeMillis()
        val all = genreCache?.takeIf { now - it.first < 10 * 60_000L }?.second
            ?: genres(10_000).also { genreCache = now to it }
        return all.filter { g -> genreParts(g.name).any { it.equals(name.trim(), ignoreCase = true) } }.joinToString(",") { it.id }
    }

    suspend fun genres(limit: Int = 60): List<JellyfinGenre> = withContext(Dispatchers.IO) {
        items(get("/MusicGenres", mapOf("userId" to JellyfinPref.userId, "sortBy" to "SortName", "limit" to limit.toString()) + lib()))
            .map { JellyfinGenre(it.str("Id"), it.str("Name"), if (it.primaryTag().isNotBlank()) it.str("Id") else "", it.primaryTag()) }
    }

    suspend fun playlists(): List<JellyfinPlaylist> = withContext(Dispatchers.IO) {
        items(get("/Items", itemQuery("includeItemTypes" to "Playlist", "sortBy" to "SortName", "fields" to "ChildCount")))
            .filter { it.str("MediaType").let { m -> m.isBlank() || m.equals("Audio", true) } }
            .map { JellyfinPlaylist(it.str("Id"), it.str("Name"), it.primaryTag(), it.int("ChildCount") ?: 0) }
    }

    suspend fun playlistTracks(id: String): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Playlists/$id/Items", mapOf("userId" to JellyfinPref.userId))).filter { it.str("Type") == "Audio" }.map { it.toTrack() }
    }

    /** Songs that go with [itemId] (a playlist, an album, a song), the server's "instant mix". */
    suspend fun instantMix(itemId: String, limit: Int = 30): List<JellyfinTrack> = withContext(Dispatchers.IO) {
        items(get("/Items/$itemId/InstantMix", mapOf("userId" to JellyfinPref.userId, "limit" to limit.toString()))).map { it.toTrack() }
    }

    /** One playlist, read again (its name, picture and size). */
    suspend fun playlist(id: String): JellyfinPlaylist? = withContext(Dispatchers.IO) {
        items(get("/Items", mapOf("userId" to JellyfinPref.userId, "ids" to id, "fields" to "ChildCount"))).firstOrNull()
            ?.let { JellyfinPlaylist(it.str("Id"), it.str("Name"), it.primaryTag(), it.int("ChildCount") ?: 0) }
    }

    /** A new audio playlist of this user, with [ids] in it (none for an empty one). Its id. */
    suspend fun createPlaylist(name: String, ids: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val r = post("/Playlists", buildJsonObject {
            put("Name", JsonPrimitive(name))
            put("UserId", JsonPrimitive(JellyfinPref.userId))
            put("MediaType", JsonPrimitive("Audio"))
            put("Ids", JsonArray(ids.map { JsonPrimitive(it) }))
        }) as? JsonObject
        r?.str("Id")?.takeIf { it.isNotBlank() } ?: throw IOException("No playlist in the answer")
    }

    /** Adds [ids] at the end of the playlist. */
    suspend fun addToPlaylist(playlistId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        // In parts: a whole album makes an address too long for some servers.
        ids.chunked(50).forEach { part ->
            post("/Playlists/$playlistId/Items?ids=${part.joinToString(",")}&userId=${JellyfinPref.userId}")
        }
    }

    /** Takes these entries (JellyfinTrack.entryId) out of the playlist. */
    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) = withContext(Dispatchers.IO) {
        entryIds.chunked(50).forEach { part -> delete("/Playlists/$playlistId/Items?entryIds=${part.joinToString(",")}") }
    }

    /** Moves an entry to [newIndex] (0 the first) of the playlist. */
    suspend fun movePlaylistEntry(playlistId: String, entryId: String, newIndex: Int) = withContext(Dispatchers.IO) {
        post("/Playlists/$playlistId/Items/$entryId/Move/$newIndex")
        Unit
    }

    /** A new name (Jellyfin 10.9 and later). */
    suspend fun renamePlaylist(playlistId: String, name: String) = withContext(Dispatchers.IO) {
        post("/Playlists/$playlistId", buildJsonObject { put("Name", JsonPrimitive(name)) })
        Unit
    }

    /** Deletes the playlist on the server (its owner, or a user allowed to delete). */
    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        delete("/Items/$playlistId")
        Unit
    }

    // ---- favourites and play reports ----

    suspend fun setFavorite(itemId: String, on: Boolean) = withContext(Dispatchers.IO) {
        val u = JellyfinPref.userId
        if (on) eitherRoute({ post("/UserFavoriteItems/$itemId?userId=$u") }, { post("/Users/$u/FavoriteItems/$itemId") })
        else eitherRoute({ delete("/UserFavoriteItems/$itemId?userId=$u") }, { delete("/Users/$u/FavoriteItems/$itemId") })
        Unit
    }

    private fun report(path: String, itemId: String, positionMs: Long, paused: Boolean) {
        post(path, buildJsonObject {
            put("ItemId", JsonPrimitive(itemId))
            put("PositionTicks", JsonPrimitive(positionMs * 10_000L))
            put("IsPaused", JsonPrimitive(paused))
            put("CanSeek", JsonPrimitive(true))
            put("PlayMethod", JsonPrimitive("DirectPlay"))
        })
    }

    /** The server's "now playing" and its play counts: a song started, went on, stopped. Failures are the caller's to ignore. */
    fun reportStart(itemId: String, positionMs: Long) = report("/Sessions/Playing", itemId, positionMs, false)
    fun reportProgress(itemId: String, positionMs: Long, paused: Boolean) = report("/Sessions/Playing/Progress", itemId, positionMs, paused)
    fun reportStopped(itemId: String, positionMs: Long) = report("/Sessions/Playing/Stopped", itemId, positionMs, false)

    /** Counts a play now (the song was heard past its half): its play count and its date played. */
    fun markPlayed(itemId: String) {
        val u = JellyfinPref.userId
        eitherRoute({ post("/UserPlayedItems/$itemId?userId=$u") }, { post("/Users/$u/PlayedItems/$itemId") })
    }

    // ---- addresses ----

    /**
     * The song's original file, as stored on the server. The token goes in the address (the player
     * sends no headers of its own): it is the session's, revocable on the server.
     */
    fun streamUrl(trackId: String): String = url(JellyfinPref.server, "/Audio/$trackId/stream",
        mapOf("static" to "true", "deviceId" to JellyfinPref.deviceId, "ApiKey" to JellyfinPref.token)).toString()

    /**
     * A song's cover: the album's or its own as the server named it; when it named none, the
     * album's asked all the same (the server often has the album's picture without linking it
     * from each song), then the song's own.
     */
    fun trackImageUrl(t: JellyfinTrack, size: Int): String = when {
        t.imageTag.isNotBlank() -> imageUrl(t.imageItemId, t.imageTag, size)
        t.albumId.isNotBlank() -> untaggedImageUrl(t.albumId, size)
        else -> untaggedImageUrl(t.id, size)
    }

    /**
     * Every address the cover of [t] may answer at, best first: the one the player asks, then
     * other sizes, the server's own format, the album's and the song's own pictures named or not.
     */
    fun trackImageCandidates(t: JellyfinTrack, size: Int): List<String> {
        val out = LinkedHashSet<String>()
        out += trackImageUrl(t, size)
        if (t.imageTag.isNotBlank()) {
            out += imageUrl(t.imageItemId, t.imageTag, 600)
            out += url(JellyfinPref.server, "/Items/${t.imageItemId}/Images/Primary", mapOf("tag" to t.imageTag, "maxHeight" to size.toString())).toString()
        }
        for (id in listOf(t.albumId, t.id).filter { it.isNotBlank() }) {
            out += untaggedImageUrl(id, size)
            out += url(JellyfinPref.server, "/Items/$id/Images/Primary", mapOf("maxHeight" to size.toString())).toString()
        }
        return out.filter { it.isNotBlank() }
    }

    /** "ok", or why [imageUrl] does not give an image Android can show (HTTP status, not an image, no answer). */
    fun checkImage(imageUrl: String): String = fetchImage(imageUrl).first

    /** [checkImage]'s verdict, with the image's bytes when it is "ok". */
    fun fetchImage(imageUrl: String): Pair<String, ByteArray?> = try {
        http.newCall(Request.Builder().url(imageUrl).get().build()).execute().use { r ->
            if (!r.isSuccessful) "HTTP ${r.code}" to null
            else {
                val bytes = r.body?.bytes() ?: ByteArray(0)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0) "ok" to bytes
                else "not an image (${r.header("Content-Type")}, ${bytes.size} bytes)" to null
            }
        }
    } catch (e: IOException) {
        "no answer (${e.javaClass.simpleName})" to null
    }

    private fun untaggedImageUrl(itemId: String, size: Int): String =
        url(JellyfinPref.server, "/Items/$itemId/Images/Primary",
            mapOf("fillHeight" to size.toString(), "fillWidth" to size.toString(), "quality" to "90", "format" to "Jpg")).toString()

    /**
     * An item's image ("Primary" or "Backdrop"), [size] px at most; "" when it has none. Always as
     * a JPEG: asked bigger than the stored picture, the server can send the stored file as it is,
     * and a cover kept as TIFF, HEIC or the like then showed in the lists (small, converted) but
     * not in the player (big, unconverted).
     */
    fun imageUrl(itemId: String, tag: String, size: Int = 400, type: String = "Primary"): String =
        if (itemId.isBlank() || tag.isBlank()) ""
        else url(JellyfinPref.server, "/Items/$itemId/Images/$type",
            mapOf("tag" to tag, "fillHeight" to size.toString(), "fillWidth" to size.toString(), "quality" to "90", "format" to "Jpg")).toString()

    // ---- parsing ----

    private fun items(r: JsonElement): List<JsonObject> =
        ((r as? JsonObject)?.get("Items") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.primaryTag(): String = ((this["ImageTags"] as? JsonObject)?.get("Primary") as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.firstId(key: String): String =
        ((this[key] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("Id").orEmpty()
    private fun JsonObject.userData(): JsonObject? = this["UserData"] as? JsonObject

    private fun JsonObject.toAlbum() = JellyfinAlbum(
        id = str("Id"), name = str("Name"),
        artist = str("AlbumArtist").ifBlank { ((this["Artists"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty() },
        year = int("ProductionYear") ?: 0, imageTag = primaryTag(),
        artistId = firstId("AlbumArtists").ifBlank { firstId("ArtistItems") },
        genres = (this["GenreItems"] as? JsonArray)?.mapNotNull { g ->
            (g as? JsonObject)?.let { it.str("Id") to it.str("Name") }?.takeIf { it.first.isNotBlank() && it.second.isNotBlank() }
        }.orEmpty(),
        favorite = (userData()?.get("IsFavorite") as? JsonPrimitive)?.booleanOrNull == true,
    )

    private fun JsonObject.toArtist() = JellyfinArtist(
        id = str("Id"), name = str("Name"), imageTag = primaryTag(),
        backdropTag = ((this["BackdropImageTags"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty(),
        favorite = (userData()?.get("IsFavorite") as? JsonPrimitive)?.booleanOrNull == true,
        albumCount = int("AlbumCount") ?: int("ChildCount") ?: 0,
        songCount = int("SongCount") ?: 0,
        overview = str("Overview"),
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
            artistId = firstId("ArtistItems").ifBlank { firstId("AlbumArtists") },
            playCount = (userData()?.get("PlayCount") as? JsonPrimitive)?.intOrNull ?: 0,
            entryId = str("PlaylistItemId"),
            favorite = (userData()?.get("IsFavorite") as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
}
