package com.ofixhub.blockstry.shared

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

private const val DEFAULT_REMOTE_MANIFEST_URL =
    "https://raw.githubusercontent.com/OfixHub/BlocksTry/main/themes.json"

data class CloudTheme(
    val id: String,
    val name: String,
    val pieceColors: List<String>,
    val backgroundColor: String,
    val gridColor: String,
    val buttonColor: String,
    val buttonTextColor: String,
    val hasCustomAssets: Boolean = false,
    val archiveUrl: String? = null,
    val version: String? = null
) {
    fun toColorTheme(localPath: String? = null): ColorTheme {
        return ColorTheme(
            id = id,
            name = name,
            pieceColors = pieceColors.map { Color(android.graphics.Color.parseColor(it)) },
            backgroundColor = Color(android.graphics.Color.parseColor(backgroundColor)),
            gridColor = Color(android.graphics.Color.parseColor(gridColor)),
            buttonColor = Color(android.graphics.Color.parseColor(buttonColor)),
            buttonTextColor = Color(android.graphics.Color.parseColor(buttonTextColor)),
            hasCustomAssets = hasCustomAssets,
            localPath = localPath,
            remoteManifest = this
        )
    }
}

data class RemoteContentManifest(
    val themes: List<CloudTheme>,
    val games: List<RemoteGame>
)

data class RemoteGame(
    val id: String,
    val name: String,
    val archiveUrl: String? = null,
    val version: String? = null
)

object RemoteContentManifestParser {
    fun parseManifest(json: String): RemoteContentManifest {
        val themes = mutableListOf<CloudTheme>()
        val games = mutableListOf<RemoteGame>()

        val data = JSONObject(json)
        val themesArray = data.optJSONArray("themes") ?: org.json.JSONArray()
        for (i in 0 until themesArray.length()) {
            val item = themesArray.getJSONObject(i)
            val pieceColorsArray = item.optJSONArray("pieceColors") ?: org.json.JSONArray()
            val pieceColors = mutableListOf<String>()
            for (j in 0 until pieceColorsArray.length()) {
                pieceColors.add(pieceColorsArray.optString(j))
            }
            themes += CloudTheme(
                id = item.optString("id", "theme_$i"),
                name = item.optString("name", "Tema sin nombre"),
                pieceColors = pieceColors,
                backgroundColor = item.optString("backgroundColor", "#000000"),
                gridColor = item.optString("gridColor", "#444444"),
                buttonColor = item.optString("buttonColor", "#888888"),
                buttonTextColor = item.optString("buttonTextColor", "#FFFFFF"),
                hasCustomAssets = item.optBoolean("hasCustomAssets", false),
                archiveUrl = item.optString("archiveUrl", "").takeIf { it.isNotBlank() },
                version = item.optString("version", "").takeIf { it.isNotBlank() }
            )
        }

        val gamesArray = data.optJSONArray("games") ?: org.json.JSONArray()
        for (i in 0 until gamesArray.length()) {
            val item = gamesArray.getJSONObject(i)
            games += RemoteGame(
                id = item.optString("id", "game_$i"),
                name = item.optString("name", "Juego sin nombre"),
                archiveUrl = item.optString("archiveUrl", "").takeIf { it.isNotBlank() },
                version = item.optString("version", "").takeIf { it.isNotBlank() }
            )
        }

        return RemoteContentManifest(themes = themes, games = games)
    }
}

class ThemeRepository(private val context: Context) {

    suspend fun getAvailableThemes(
        manifestUrl: String = DEFAULT_REMOTE_MANIFEST_URL
    ): List<CloudTheme> = withContext(Dispatchers.IO) {
        try {
            val json = URL(manifestUrl).readText()
            RemoteContentManifestParser.parseManifest(json).themes
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAvailableGames(
        manifestUrl: String = DEFAULT_REMOTE_MANIFEST_URL
    ): List<RemoteGame> = withContext(Dispatchers.IO) {
        try {
            val json = URL(manifestUrl).readText()
            RemoteContentManifestParser.parseManifest(json).games
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun downloadThemeAssets(theme: CloudTheme): Boolean {
        if (theme.archiveUrl.isNullOrBlank()) return true

        val themeDir = File(context.filesDir, "themes/${theme.id}")
        if (!themeDir.exists()) themeDir.mkdirs()

        return withContext(Dispatchers.IO) {
            try {
                val archiveFile = File(themeDir, "theme.zip")
                URL(theme.archiveUrl).openStream().use { input ->
                    FileOutputStream(archiveFile).use { output -> input.copyTo(output) }
                }
                unzipArchive(archiveFile, themeDir)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun getDownloadedThemeDir(themeId: String): File =
        File(context.filesDir, "themes/$themeId")

    fun getLocalThemePath(themeId: String): String? {
        val dir = File(context.filesDir, "themes/$themeId")
        return if (dir.exists()) dir.absolutePath else null
    }

    private fun unzipArchive(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        zipFile.delete()
    }
}
