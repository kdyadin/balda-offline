package com.kdyadin.balda.core

import com.kdyadin.balda.core.dictionary.WordDictionary

/** Результат применения хода. */
sealed class MoveResult {
    data class Accepted(val state: GameState) : MoveResult()
    data class Rejected(val error: MoveError) : MoveResult()
}

/** Все переходы состояния партии. Чистые функции, без побочных эффектов. */
object GameEngine {

    /** Сколько пасов подряд (суммарно у обоих) завершает партию: по три у каждого. */
    const val PASSES_TO_FINISH = 6

    fun newGame(settings: GameSettings): GameState = GameState(
        settings = settings,
        board = Board.withStartWord(settings.boardSize, settings.startWord),
        players = settings.playerNames.map { PlayerState(name = it) },
        currentPlayer = settings.firstPlayer,
    )

    fun applyMove(state: GameState, proposal: MoveProposal, dictionary: WordDictionary): MoveResult {
        check(!state.isFinished) { "Партия завершена" }
        MoveValidator.validate(state, dictionary, proposal)?.let { return MoveResult.Rejected(it) }

        val board = state.board.with(proposal.cell, proposal.letter)
        val entry = WordEntry(
            word = board.wordAlong(proposal.path),
            path = proposal.path,
            placedCell = proposal.cell,
            placedLetter = proposal.letter.lowercaseChar(),
            playerIndex = state.currentPlayer,
            moveNumber = state.moveNumber,
        )
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayer) p.copy(words = p.words + entry) else p
        }
        val next = state.copy(
            board = board,
            players = players,
            consecutivePasses = 0,
            log = state.log + LogEntry.Word(state.currentPlayer, state.moveNumber, entry),
        )
        return MoveResult.Accepted(finishIfNeeded(advanceTurn(next)))
    }

    fun pass(state: GameState, byTimer: Boolean = false): GameState {
        check(!state.isFinished) { "Партия завершена" }
        val next = state.copy(
            consecutivePasses = state.consecutivePasses + 1,
            log = state.log + LogEntry.Pass(state.currentPlayer, state.moveNumber, byTimer),
        )
        return finishIfNeeded(advanceTurn(next))
    }

    fun timerExpired(state: GameState): GameState = pass(state, byTimer = true)

    /** Списать подсказку: штраф, счётчик, запись в журнал. Найти саму подсказку — задача [HintFinder]. */
    fun useHint(state: GameState, hint: Hint): GameState {
        check(!state.isFinished) { "Партия завершена" }
        val player = state.current
        check(state.settings.hintsAllowed(player.hintsUsed)) { "Лимит подсказок исчерпан" }
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayer) {
                p.copy(hintsUsed = p.hintsUsed + 1, penalty = p.penalty + state.settings.hintPenalty)
            } else p
        }
        return state.copy(
            players = players,
            activeHint = hint,
            log = state.log + LogEntry.Hint(state.currentPlayer, state.moveNumber, hint.word, state.settings.hintPenalty),
        )
    }

    fun surrender(state: GameState): GameState {
        check(!state.isFinished) { "Партия завершена" }
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayer) p.copy(surrendered = true) else p
        }
        return state.copy(
            players = players,
            activeHint = null,
            log = state.log + LogEntry.Surrender(state.currentPlayer, state.moveNumber),
            status = GameStatus.Finished(FinishReason.SURRENDER, winner = state.opponentIndex),
        )
    }

    fun winnerByScore(players: List<PlayerState>): Int? {
        val a = players[0].score
        val b = players[1].score
        return when {
            a > b -> 0
            b > a -> 1
            else -> null
        }
    }

    private fun advanceTurn(state: GameState): GameState = state.copy(
        currentPlayer = state.opponentIndex,
        moveNumber = state.moveNumber + 1,
        activeHint = null,
    )

    private fun finishIfNeeded(state: GameState): GameState = when {
        state.board.isFull ->
            state.copy(status = GameStatus.Finished(FinishReason.BOARD_FULL, winnerByScore(state.players)))
        state.consecutivePasses >= PASSES_TO_FINISH ->
            state.copy(status = GameStatus.Finished(FinishReason.PASSES, winnerByScore(state.players)))
        else -> state
    }
}
