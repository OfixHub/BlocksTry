package com.ofixhub.blockstry.shared

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class ColorTheme(
    val id: String,
    val name: String,
    val pieceColors: List<Color>,
    val backgroundColor: Color,
    val gridColor: Color,
    val buttonColor: Color,
    val buttonTextColor: Color,
    val hasCustomAssets: Boolean = false,
    val localPath: String? = null,
    val remoteManifest: CloudTheme? = null // Metadata for remote themes not yet downloaded
) {
    val isDownloaded: Boolean
        get() = !hasCustomAssets || localPath != null
}

object SettingsManager {

    private const val PREFS_NAME = "BlocksTryPrefs"
    private lateinit var prefs: SharedPreferences

    private const val HIGH_SCORE_KEY = "high_score"
    private const val GHOST_PIECE_ENABLED_KEY = "ghost_piece_enabled"
    private const val GRID_ENABLED_KEY = "grid_enabled"
    private const val THEME_KEY = "theme"
    private const val CONSTANT_SPEED_KEY = "constant_speed"
    private const val CURRENT_GAME_KEY = "current_game"
    private const val PLAYER_NAME_KEY = "player_name"
    private const val LEADERBOARD_KEY = "leaderboard"
    private const val MAX_LEADERBOARD_ENTRIES = 10

    var currentGame by mutableStateOf("Tetris")
        private set

    fun updateCurrentGame(value: String) {
        currentGame = value
        prefs.edit().putString(CURRENT_GAME_KEY, value).apply()
    }

    private val defaultThemes = listOf(
        ColorTheme(
            id = "classic",
            name = "Clásico",
            pieceColors = listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red),
            backgroundColor = Color.Black,
            gridColor = Color.DarkGray,
            buttonColor = Color.LightGray,
            buttonTextColor = Color.Black
        ),
        ColorTheme(
            id = "ocean",
            name = "Océano",
            pieceColors = listOf(Color(0xFF80DEEA), Color(0xFF4DD0E1), Color(0xFF26C6DA), Color(0xFF00BCD4), Color(0xFF00ACC1), Color(0xFF0097A7), Color(0xFF00838F)),
            backgroundColor = Color(0xFF001F3F),
            gridColor = Color(0xFF003366),
            buttonColor = Color(0xFF0077B6),
            buttonTextColor = Color.White
        ),
        ColorTheme(
            id = "forest",
            name = "Bosque",
            pieceColors = listOf(Color(0xFFA5D6A7), Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF4CAF50), Color(0xFF43A047), Color(0xFF388E3C), Color(0xFF2E7D32)),
            backgroundColor = Color(0xFF1B4332),
            gridColor = Color(0xFF2D6A4F),
            buttonColor = Color(0xFF40916C),
            buttonTextColor = Color.White
        )
    )

    val themes = mutableStateMapOf<String, ColorTheme>().apply {
        defaultThemes.forEach { put(it.name, it) }
    }

    var currentThemeName by mutableStateOf("Clásico")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highScore = prefs.getInt(HIGH_SCORE_KEY, 0)
        isGhostPieceEnabled = prefs.getBoolean(GHOST_PIECE_ENABLED_KEY, true)
        isGridEnabled = prefs.getBoolean(GRID_ENABLED_KEY, true)
        currentThemeName = prefs.getString(THEME_KEY, "Clásico") ?: "Clásico"
        isConstantSpeedEnabled = prefs.getBoolean(CONSTANT_SPEED_KEY, false)
        currentGame = prefs.getString(CURRENT_GAME_KEY, "Tetris") ?: "Tetris"
        playerName = prefs.getString(PLAYER_NAME_KEY, "Jugador") ?: "Jugador"
        loadLeaderboard()
    }

    fun addTheme(theme: ColorTheme) {
        themes[theme.name] = theme
    }

    var highScore by mutableStateOf(0)
        private set

    fun updateHighScore(value: Int) {
        if (value > highScore) {
            highScore = value
            prefs.edit().putInt(HIGH_SCORE_KEY, value).apply()
        }
    }

    fun resetHighScore() {
        highScore = 0
        prefs.edit().putInt(HIGH_SCORE_KEY, 0).apply()
    }

    var isGhostPieceEnabled by mutableStateOf(true)
        private set

    fun setIsGhostPieceEnabled(value: Boolean) {
        isGhostPieceEnabled = value
        prefs.edit().putBoolean(GHOST_PIECE_ENABLED_KEY, value).apply()
    }

    var isGridEnabled by mutableStateOf(true)
        private set

    fun setIsGridEnabled(value: Boolean) {
        isGridEnabled = value
        prefs.edit().putBoolean(GRID_ENABLED_KEY, value).apply()
    }

    fun updateCurrentThemeName(value: String) {
        currentThemeName = value
        prefs.edit().putString(THEME_KEY, value).apply()
    }

    val currentTheme: ColorTheme
        get() = themes[currentThemeName] ?: themes["Clásico"]!!

    var isConstantSpeedEnabled by mutableStateOf(false)
        private set

    fun setIsConstantSpeedEnabled(value: Boolean) {
        isConstantSpeedEnabled = value
        prefs.edit().putBoolean(CONSTANT_SPEED_KEY, value).apply()
    }

    // --- Player name ---
    var playerName by mutableStateOf("Jugador")
        private set

    fun updatePlayerName(value: String) {
        playerName = value.take(20).ifBlank { "Jugador" }
        prefs.edit().putString(PLAYER_NAME_KEY, playerName).apply()
    }

    // --- Local Leaderboard ---
    val leaderboard = mutableStateListOf<LeaderboardEntry>()

    fun submitScore(score: Int, game: String = currentGame) {
        if (score <= 0) return
        updateHighScore(score)
        val entry = LeaderboardEntry(
            playerName = playerName,
            score = score,
            game = game,
            timestamp = System.currentTimeMillis()
        )
        val merged = (leaderboard.toList() + entry)
            .sortedByDescending { it.score }
            .take(MAX_LEADERBOARD_ENTRIES)
        leaderboard.clear()
        leaderboard.addAll(merged)
        saveLeaderboard()
    }

    private fun loadLeaderboard() {
        val json = prefs.getString(LEADERBOARD_KEY, "[]") ?: "[]"
        try {
            val array = JSONArray(json)
            val entries = mutableListOf<LeaderboardEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries.add(
                    LeaderboardEntry(
                        playerName = obj.optString("name", "Jugador"),
                        score = obj.optInt("score", 0),
                        game = obj.optString("game", "Tetris"),
                        timestamp = obj.optLong("ts", 0L)
                    )
                )
            }
            leaderboard.clear()
            leaderboard.addAll(entries.sortedByDescending { it.score })
        } catch (_: Exception) {
            leaderboard.clear()
        }
    }

    private fun saveLeaderboard() {
        val array = JSONArray()
        leaderboard.forEach { entry ->
            val obj = JSONObject()
            obj.put("name", entry.playerName)
            obj.put("score", entry.score)
            obj.put("game", entry.game)
            obj.put("ts", entry.timestamp)
            array.put(obj)
        }
        prefs.edit().putString(LEADERBOARD_KEY, array.toString()).apply()
    }

    fun clearLeaderboard() {
        leaderboard.clear()
        prefs.edit().remove(LEADERBOARD_KEY).apply()
    }
}
