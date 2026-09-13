package com.kdyadin.balda.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kdyadin.balda.core.GameSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Пользовательские настройки приложения и параметры последней партии. */
data class AppSettings(
    val playerNames: List<String> = listOf(DEFAULT_NAME_1, DEFAULT_NAME_2),
    val boardSize: Int = 5,
    val timerSeconds: Int = 0,
    val hintLimit: Int = 1,
    val hintPenalty: Int = 5,
    val handoffScreen: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Предлагали ли уже загрузить базу толкований при первом запуске. */
    val definitionsPromptShown: Boolean = false,
) {
    companion object {
        const val DEFAULT_NAME_1 = "Игрок 1"
        const val DEFAULT_NAME_2 = "Игрок 2"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val NAME_1 = stringPreferencesKey("player_name_1")
        val NAME_2 = stringPreferencesKey("player_name_2")
        val BOARD_SIZE = intPreferencesKey("board_size")
        val TIMER = intPreferencesKey("timer_seconds")
        val HINT_LIMIT = intPreferencesKey("hint_limit")
        val HINT_PENALTY = intPreferencesKey("hint_penalty")
        val HANDOFF = booleanPreferencesKey("handoff_screen")
        val THEME = stringPreferencesKey("theme_mode")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val DEFINITIONS_PROMPT = booleanPreferencesKey("definitions_prompt_shown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            playerNames = listOf(
                p[Keys.NAME_1]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_NAME_1,
                p[Keys.NAME_2]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_NAME_2,
            ),
            boardSize = p[Keys.BOARD_SIZE] ?: 5,
            timerSeconds = p[Keys.TIMER] ?: 0,
            hintLimit = p[Keys.HINT_LIMIT] ?: 1,
            hintPenalty = p[Keys.HINT_PENALTY] ?: 5,
            handoffScreen = p[Keys.HANDOFF] ?: true,
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            soundEnabled = p[Keys.SOUND] ?: false,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            definitionsPromptShown = p[Keys.DEFINITIONS_PROMPT] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Сохранить параметры партии, чтобы подставить их в следующий раз. */
    suspend fun rememberGameSettings(s: GameSettings) {
        context.dataStore.edit { p ->
            p[Keys.NAME_1] = s.playerNames[0]
            p[Keys.NAME_2] = s.playerNames[1]
            p[Keys.BOARD_SIZE] = s.boardSize
            p[Keys.TIMER] = s.timerSeconds
            p[Keys.HINT_LIMIT] = s.hintLimit
            p[Keys.HINT_PENALTY] = s.hintPenalty
            p[Keys.HANDOFF] = s.handoffScreen
        }
    }

    suspend fun setHandoffScreen(enabled: Boolean) = context.dataStore.edit { it[Keys.HANDOFF] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setSoundEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setHapticsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setDefinitionsPromptShown() = context.dataStore.edit { it[Keys.DEFINITIONS_PROMPT] = true }
}
