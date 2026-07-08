package com.ofixhub.blockstry.shared

data class LeaderboardEntry(
    val playerName: String = "Jugador",
    val score: Int = 0,
    val game: String = "Tetris",
    val timestamp: Long = System.currentTimeMillis()
)

/** Delegates to [SettingsManager] for local-first persistence. No remote backend needed. */
class LeaderboardRepository {
    fun submitScore(playerName: String, score: Int, game: String = SettingsManager.currentGame) {
        SettingsManager.submitScore(score, game)
    }

    fun getTopScores(limit: Int = 10): List<LeaderboardEntry> {
        return SettingsManager.leaderboard.take(limit)
    }
}
