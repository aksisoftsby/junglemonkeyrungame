package com.example.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Offline storage manager for high scores, game statistics, and user preferences.
 */
class ScoreStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jungle_monkey_run_prefs", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST_SCORE, 0)
        set(value) {
            prefs.edit().putInt(KEY_BEST_SCORE, value).apply()
        }

    var tutorialCompleted: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, value).apply()
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
        }

    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC_ENABLED, value).apply()
        }

    var totalCoins: Int
        get() = prefs.getInt(KEY_TOTAL_COINS, 0)
        set(value) {
            prefs.edit().putInt(KEY_TOTAL_COINS, value).apply()
        }

    var totalBananas: Int
        get() = prefs.getInt(KEY_TOTAL_BANANAS, 0)
        set(value) {
            prefs.edit().putInt(KEY_TOTAL_BANANAS, value).apply()
        }

    fun updateScoreIfBest(score: Int): Boolean {
        if (score > bestScore) {
            bestScore = score
            return true
        }
        return false
    }

    fun addCollectibles(coins: Int, bananas: Int) {
        totalCoins += coins
        totalBananas += bananas
    }

    companion object {
        private const val KEY_BEST_SCORE = "best_score"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_TOTAL_COINS = "total_coins"
        private const val KEY_TOTAL_BANANAS = "total_bananas"
    }
}
