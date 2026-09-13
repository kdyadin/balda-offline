package com.kdyadin.balda.data

import android.content.Context
import com.kdyadin.balda.core.Cell
import com.kdyadin.balda.core.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Сохранённая партия: состояние по правилам + то, что нужно UI для продолжения с того же места.
 */
@Serializable
data class SavedGame(
    val state: GameState,
    /** Ждём нажатия «Готов» на заслонке передачи хода. */
    val handoffPending: Boolean = false,
    /** Момент истечения таймера текущего хода (epoch millis), null — таймер не запущен. */
    val turnDeadlineMillis: Long? = null,
    /** Незавершённый ввод текущего хода. */
    val pendingCell: Cell? = null,
    val pendingLetter: Char? = null,
    val pendingPath: List<Cell> = emptyList(),
)

/** Хранение текущей партии в файле: переживает поворот, сворачивание и убийство процесса. */
class GameRepository(context: Context) {
    private val file = File(context.filesDir, "current_game.json")
    private val tmp = File(context.filesDir, "current_game.json.tmp")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): SavedGame? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            runCatching { json.decodeFromString(SavedGame.serializer(), file.readText()) }
                .onFailure { file.delete() }
                .getOrNull()
        }
    }

    suspend fun save(game: SavedGame) = withContext(Dispatchers.IO) {
        mutex.withLock {
            tmp.writeText(json.encodeToString(SavedGame.serializer(), game))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete(); tmp.delete() }
    }
}
