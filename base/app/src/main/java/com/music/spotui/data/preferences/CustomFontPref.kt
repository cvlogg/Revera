package com.music.spotui.data.preferences

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * "Custom font": font files the user picks on their phone (a .ttf or .otf each), used for the
 * whole app in place of its own font. Copies are kept in the app's private storage, nothing
 * is shipped with the app: any font the user owns, under its own licence, stays on their phone.
 *
 * Several files make one family (Regular, Medium, Bold…): each file's weight is read from its
 * name; a weight with no file is drawn from the nearest one (bold made up from the regular one
 * when only that one was picked).
 */
object CustomFontPref {
    private const val DIR = "custom_font"
    private const val PREFS = "custom_font_prefs"
    private const val KEY_NAME = "name"

    /** Bumped at each change: the fonts are read again, and the screens redraw. */
    var version by mutableIntStateOf(0)
        private set

    /** The font's name as the first file was called ("SpotifyMix"), "" with none. */
    var name by mutableStateOf("")
        private set

    private fun dir(c: Context) = File(c.filesDir, DIR)

    fun init(c: Context) {
        name = if (files(c).isEmpty()) "" else c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "").orEmpty()
        version++
    }

    private fun files(c: Context): List<File> = dir(c).listFiles { f -> f.isFile && f.length() > 0 }?.toList().orEmpty()

    val active: Boolean get() = name.isNotBlank()

    /** A file's weight from its name ("SpotifyMix-Bold.ttf" → 700); 400 when it names none. */
    internal fun weightOf(fileName: String): Int {
        val n = fileName.lowercase().replace("-", "").replace("_", "").replace(" ", "")
        return when {
            "extrablack" in n || "ultrablack" in n -> 950
            "black" in n || "heavy" in n -> 900
            "extrabold" in n || "ultrabold" in n -> 800
            "semibold" in n || "demibold" in n -> 600
            "bold" in n -> 700
            "medium" in n -> 500
            "extralight" in n || "ultralight" in n -> 200
            "thin" in n || "hairline" in n -> 100
            "light" in n -> 300
            else -> 400
        }.coerceAtMost(900)
    }

    /**
     * Copies the picked files. Files of the font already in use (same name before the "-", e.g.
     * SpotifyMix-Bold after SpotifyMix-Regular) are added to it, a weight picked again replacing
     * its file; another font replaces the one before. False when none is a font Android can read
     * (then the font before stays).
     */
    fun import(c: Context, uris: List<Uri>): Boolean {
        val tmp = File(c.filesDir, "$DIR.new").apply { deleteRecursively(); mkdirs() }
        var first = ""
        for (uri in uris) {
            val display = displayName(c, uri)
            val weight = weightOf(display)
            val out = File(tmp, "w$weight.font")
            runCatching { c.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } }
            // A file Android cannot read as a font is left out (a wrong pick, a broken download).
            val ok = out.length() > 0 && runCatching { android.graphics.Typeface.Builder(out).build() != null }.getOrDefault(false)
            if (!ok) out.delete() else if (first.isEmpty()) first = display.substringBeforeLast('.').substringBefore('-').ifBlank { display }
        }
        if (tmp.listFiles().isNullOrEmpty()) { tmp.deleteRecursively(); return false }
        if (active && first.equals(name, ignoreCase = true)) {
            // More weights of the same font: added to the files already there.
            dir(c).mkdirs()
            tmp.listFiles()?.forEach { f -> f.copyTo(File(dir(c), f.name), overwrite = true) }
            tmp.deleteRecursively()
            first = name
        } else {
            dir(c).deleteRecursively()
            tmp.renameTo(dir(c))
        }
        name = first
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, first).apply()
        cached = null
        version++
        return true
    }

    fun clear(c: Context) {
        dir(c).deleteRecursively()
        name = ""
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NAME).apply()
        cached = null
        version++
    }

    private fun displayName(c: Context, uri: Uri): String =
        runCatching {
            c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private var cached: Pair<Int, FontFamily>? = null

    /** The family made of the picked files, null with none. */
    fun family(c: Context): FontFamily? {
        if (!active) return null
        cached?.takeIf { it.first == version }?.let { return it.second }
        val fonts = files(c).mapNotNull { f ->
            f.name.removePrefix("w").removeSuffix(".font").toIntOrNull()?.let { w -> androidx.compose.ui.text.font.Font(f, FontWeight(w)) }
        }
        if (fonts.isEmpty()) return null
        return FontFamily(fonts).also { cached = version to it }
    }
}
