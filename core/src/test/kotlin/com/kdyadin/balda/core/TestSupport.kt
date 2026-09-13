package com.kdyadin.balda.core

import com.kdyadin.balda.core.dictionary.SortedWordDictionary
import com.kdyadin.balda.core.dictionary.WordDictionary

object TestSupport {
    val WORDS = listOf(
        "балда", "бал", "лад", "дал", "дала", "бак", "кал", "кабала", "ада", "лак", "аул",
        "еж", "жажда", "жар", "дар", "рак", "кол", "око", "колокол", "лом", "мол", "омар",
        "кит", "тик", "кот", "ток", "тир", "рот", "утка", "укол",
    )

    val DICTIONARY: WordDictionary = SortedWordDictionary.of(WORDS.map { Alphabet.normalize(it) })

    fun settings(
        size: Int = 5,
        startWord: String = "балда",
        firstPlayer: Int = 0,
        hintLimit: Int = 1,
        hintPenalty: Int = 5,
    ) = GameSettings(
        boardSize = size,
        startWord = startWord,
        playerNames = listOf("Инна", "Олег"),
        firstPlayer = firstPlayer,
        hintLimit = hintLimit,
        hintPenalty = hintPenalty,
    )

    fun newGame(size: Int = 5, startWord: String = "балда"): GameState =
        GameEngine.newGame(settings(size, startWord))

    fun cells(vararg rc: Int): List<Cell> {
        require(rc.size % 2 == 0)
        return (rc.indices step 2).map { Cell(rc[it], rc[it + 1]) }
    }

    /** Поле из строк, '.' — пустая клетка. */
    fun board(vararg rows: String): Board = Board(rows.size, rows.joinToString(""))

    fun accept(state: GameState, proposal: MoveProposal): GameState =
        when (val r = GameEngine.applyMove(state, proposal, DICTIONARY)) {
            is MoveResult.Accepted -> r.state
            is MoveResult.Rejected -> error("Ход отклонён: ${r.error}")
        }

    fun reject(state: GameState, proposal: MoveProposal): MoveError =
        when (val r = GameEngine.applyMove(state, proposal, DICTIONARY)) {
            is MoveResult.Accepted -> error("Ход неожиданно принят")
            is MoveResult.Rejected -> r.error
        }
}
