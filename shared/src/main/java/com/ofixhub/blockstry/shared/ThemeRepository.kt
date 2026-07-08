package com.ofixhub.blockstry.shared

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
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
    val snakeColor: String? = null,  // Color del cuerpo de la serpiente (opcional)
    val foodColor: String? = null,   // Color de la comida en Snake (opcional)
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
            snakeColor = snakeColor?.let { Color(android.graphics.Color.parseColor(it)) },
            foodColor = foodColor?.let { Color(android.graphics.Color.parseColor(it)) },
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
                snakeColor = item.optString("snakeColor", "").takeIf { it.isNotBlank() },
                foodColor = item.optString("foodColor", "").takeIf { it.isNotBlank() },
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

    companion object {
        private const val CACHE_TTL_MS = 60L * 60 * 1000 // 1 hora
        private const val MANIFEST_CACHE_FILE = "themes_manifest_cache.json"
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 segundos
        private const val READ_TIMEOUT_MS   = 30_000 // 30 segundos

        @Volatile private var sharedManifest: RemoteContentManifest? = null
        @Volatile private var sharedCacheTimestamp: Long = 0
    }

    /** Descarga una URL con timeouts definidos para evitar bloqueos indefinidos. */
    private fun downloadWithTimeout(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout    = READ_TIMEOUT_MS
            requestMethod  = "GET"
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun getManifest(
        manifestUrl: String = DEFAULT_REMOTE_MANIFEST_URL
    ): RemoteContentManifest = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Caché en memoria compartida entre instancias
        sharedManifest?.takeIf { now - sharedCacheTimestamp < CACHE_TTL_MS }
            ?.let { return@withContext it }

        // 2. Caché en disco
        val cacheFile = File(context.cacheDir, MANIFEST_CACHE_FILE)
        if (cacheFile.exists() && now - cacheFile.lastModified() < CACHE_TTL_MS) {
            try {
                val manifest = RemoteContentManifestParser.parseManifest(cacheFile.readText())
                sharedManifest = manifest
                sharedCacheTimestamp = cacheFile.lastModified()
                return@withContext manifest
            } catch (_: Exception) { /* caché corrupta, continuar */ }
        }

        // 3. Descarga desde red con timeout para evitar bloqueo indefinido
        try {
            val json = downloadWithTimeout(manifestUrl)
            val manifest = RemoteContentManifestParser.parseManifest(json)
            cacheFile.writeText(json)
            synchronized(this) {
                sharedManifest = manifest
                sharedCacheTimestamp = now
            }
            manifest
        } catch (_: Exception) {
            // Fallback: usar caché aunque esté expirada si no hay red
            if (cacheFile.exists()) {
                try {
                    RemoteContentManifestParser.parseManifest(cacheFile.readText()).also {
                        synchronized(this) {
                            sharedManifest = it
                            sharedCacheTimestamp = now
                        }
                    }
                } catch (_: Exception) { RemoteContentManifest(emptyList(), emptyList()) }
            } else {
                RemoteContentManifest(emptyList(), emptyList())
            }
        }
    }

    suspend fun getAvailableThemes(
        manifestUrl: String = DEFAULT_REMOTE_MANIFEST_URL
    ): List<CloudTheme> = getManifest(manifestUrl).themes

    suspend fun getAvailableGames(
        manifestUrl: String = DEFAULT_REMOTE_MANIFEST_URL
    ): List<RemoteGame> = getManifest(manifestUrl).games

    suspend fun downloadThemeAssets(theme: CloudTheme): Boolean {
        if (theme.archiveUrl.isNullOrBlank()) return true

        val themeDir = File(context.filesDir, "themes/${theme.id}")
        if (!themeDir.exists()) themeDir.mkdirs()

        return withContext(Dispatchers.IO) {
            try {
                val archiveFile = File(themeDir, "theme.zip")
                val conn = (URL(theme.archiveUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout    = READ_TIMEOUT_MS
                    requestMethod  = "GET"
                }
                try {
                    conn.inputStream.use { input ->
                        FileOutputStream(archiveFile).use { output -> input.copyTo(output) }
                    }
                } finally {
                    conn.disconnect()
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
