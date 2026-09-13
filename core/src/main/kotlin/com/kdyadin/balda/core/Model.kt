package com.kdyadin.balda.core

import kotlinx.serialization.Serializable

/** Настройки одной партии. */
@Serializable
data class GameSettings(
    val boardSize: Int = 5,
    val startWord: String,
    val playerNames: List<String> = listOf("Игрок 1", "Игрок 2"),
    /** Индекс игрока, который ходит первым (0 или 1). */
    val firstPlayer: Int = 0,
    /** Секунд на ход; 0 — таймер выключен. */
    val timerSeconds: Int = 0,
    /** Лимит подсказок на игрока; [UNLIMITED_HINTS] — без ограничений. */
    val hintLimit: Int = 1,
    /** Штраф в очках за одну подсказку. */
    val hintPenalty: Int = 5,
    /** Показывать заслонку «Передай телефон» между ходами. */
    val handoffScreen: Boolean = true,
) {
    init {
        require(playerNames.size == 2) { "Игроков всегда двое" }
        require(firstPlayer in 0..1)
        require(startWord.length == boardSize)
    }

    fun hintsAllowed(used: Int): Boolean = hintLimit == UNLIMITED_HINTS || used < hintLimit

    companion object {
        const val UNLIMITED_HINTS = -1
        const val PLAYERS = 2
        val TIMER_OPTIONS = listOf(0, 30, 60, 120)
        val HINT_LIMIT_OPTIONS = listOf(0, 1, 3, UNLIMITED_HINTS)
        val HINT_PENALTY_OPTIONS = listOf(0, 3, 5, 10)
    }
}

/** Составленное слово в журнале партии. */
@Serializable
data class WordEntry(
    val word: String,
    val path: List<Cell>,
    val placedCell: Cell,
    val placedLetter: Char,
    val playerIndex: Int,
    val moveNumber: Int,
) {
    val points: Int get() = word.length
}

@Serializable
data class PlayerState(
    val name: String,
    val words: List<WordEntry> = emptyList(),
    val hintsUsed: Int = 0,
    val penalty: Int = 0,
    val surrendered: Boolean = false,
) {
    val wordPoints: Int get() = words.sumOf { it.points }
    val score: Int get() = if (surrendered) 0 else wordPoints - penalty
}

/** Запись журнала партии — всё, что произошло, в хронологическом порядке. */
@Serializable
sealed class LogEntry {
    abstract val playerIndex: Int
    abstract val moveNumber: Int

    @Serializable
    data class Word(override val playerIndex: Int, override val moveNumber: Int, val entry: WordEntry) : LogEntry()

    @Serializable
    data class Pass(override val playerIndex: Int, override val moveNumber: Int, val byTimer: Boolean) : LogEntry()

    @Serializable
    data class Hint(override val playerIndex: Int, override val moveNumber: Int, val word: String, val penalty: Int) : LogEntry()

    @Serializable
    data class Surrender(override val playerIndex: Int, override val moveNumber: Int) : LogEntry()
}

@Serializable
enum class FinishReason { BOARD_FULL, PASSES, SURRENDER }

@Serializable
sealed class GameStatus {
    @Serializable
    data object Ongoing : GameStatus()

    /** [winner] — индекс победителя, null при ничьей. */
    @Serializable
    data class Finished(val reason: FinishReason, val winner: Int?) : GameStatus()
}

/** Подсказка, показанная игроку в текущем ходу. */
@Serializable
data class Hint(val cell: Cell, val letter: Char, val path: List<Cell>, val word: String)

/** Полное состояние партии. Неизменяемое; переходы — в [GameEngine]. */
@Serializable
data class GameState(
    val settings: GameSettings,
    val board: Board,
    val players: List<PlayerState>,
    val currentPlayer: Int,
    /** Номер текущего хода, начиная с 1. */
    val moveNumber: Int = 1,
    val consecutivePasses: Int = 0,
    val log: List<LogEntry> = emptyList(),
    val status: GameStatus = GameStatus.Ongoing,
    /** Подсказка, полученная в текущем ходу (сбрасывается при смене хода). */
    val activeHint: Hint? = null,
) {
    val isFinished: Boolean get() = status is GameStatus.Finished
    val current: PlayerState get() = players[currentPlayer]
    val opponentIndex: Int get() = 1 - currentPlayer

    /** Нормализованные слова, уже составленные в партии, вместе со стартовым. */
    fun usedWordsNormalized(): Set<String> = buildSet {
        add(Alphabet.normalize(settings.startWord))
        for (p in players) for (w in p.words) add(Alphabet.normalize(w.word))
    }

    /** Кто составил слово (нормализованное), либо null. */
    fun ownerOf(normalizedWord: String): Int? {
        for ((i, p) in players.withIndex()) {
            if (p.words.any { Alphabet.normalize(it.word) == normalizedWord }) return i
        }
        return null
    }
}
