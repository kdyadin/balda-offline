package com.kdyadin.balda.core

import com.kdyadin.balda.core.dictionary.WordDictionary

/** Предлагаемый ход: поставленная буква и путь слова. */
data class MoveProposal(val cell: Cell, val letter: Char, val path: List<Cell>)

/** Причина отклонения хода. Текст для пользователя формирует UI. */
sealed class MoveError {
    /** Буква ещё не поставлена. */
    data object NoLetterPlaced : MoveError()
    /** Клетка занята — ставить букву можно только в пустую. */
    data object CellOccupied : MoveError()
    /** Путь пуст или короче двух букв. */
    data object PathTooShort : MoveError()
    /** Соседние клетки пути стоят по диагонали. */
    data object Diagonal : MoveError()
    /** Клетки пути не соседние. */
    data object NotAdjacent : MoveError()
    /** Клетка использована в слове дважды. */
    data object CellRepeated : MoveError()
    /** Путь проходит через пустую клетку. */
    data object EmptyCellInPath : MoveError()
    /** Путь не проходит через только что поставленную букву. */
    data object NewLetterNotUsed : MoveError()
    /** Слова нет во встроенном словаре. */
    data class NotInDictionary(val word: String) : MoveError()
    /** Слово совпадает со стартовым. */
    data object IsStartWord : MoveError()
    /** Слово уже составлено в этой партии игроком [playerName]. */
    data class AlreadyUsed(val word: String, val playerIndex: Int, val playerName: String) : MoveError()
    /** Слово уже читалось на поле до этого хода. */
    data class AlreadyOnBoard(val word: String) : MoveError()
}

object MoveValidator {

    /**
     * Проверка добавления клетки [next] к строящемуся пути — для мгновенной обратной связи в UI.
     * Возвращает null, если клетку можно добавить.
     */
    fun checkPathStep(board: Board, path: List<Cell>, next: Cell): MoveError? {
        if (!board.isInside(next)) return MoveError.NotAdjacent
        if (board.isEmpty(next)) return MoveError.EmptyCellInPath
        if (next in path) return MoveError.CellRepeated
        val last = path.lastOrNull() ?: return null
        if (last.isOrthogonalNeighbor(next)) return null
        return if (last.isDiagonalNeighbor(next)) MoveError.Diagonal else MoveError.NotAdjacent
    }

    /** Геометрия пути: соседство, повторы, пустые клетки, прохождение через новую букву. */
    fun checkPathShape(board: Board, cell: Cell, path: List<Cell>): MoveError? {
        if (path.size < 2) return MoveError.PathTooShort
        val seen = HashSet<Cell>(path.size * 2)
        for ((i, c) in path.withIndex()) {
            if (!board.isInside(c)) return MoveError.NotAdjacent
            if (board.isEmpty(c)) return MoveError.EmptyCellInPath
            if (!seen.add(c)) return MoveError.CellRepeated
            if (i > 0) {
                val prev = path[i - 1]
                if (!prev.isOrthogonalNeighbor(c)) {
                    return if (prev.isDiagonalNeighbor(c)) MoveError.Diagonal else MoveError.NotAdjacent
                }
            }
        }
        if (cell !in seen) return MoveError.NewLetterNotUsed
        return null
    }

    /**
     * Полная проверка хода. [state].board — поле ДО постановки буквы.
     * Возвращает null, если ход допустим.
     */
    fun validate(state: GameState, dictionary: WordDictionary, proposal: MoveProposal): MoveError? {
        val before = state.board
        if (!before.isInside(proposal.cell)) return MoveError.CellOccupied
        if (!before.isEmpty(proposal.cell)) return MoveError.CellOccupied
        if (!Alphabet.isRussianLetter(proposal.letter)) return MoveError.NoLetterPlaced

        val after = before.with(proposal.cell, proposal.letter)
        checkPathShape(after, proposal.cell, proposal.path)?.let { return it }

        val raw = after.wordAlong(proposal.path)
        val word = Alphabet.normalize(raw)

        if (word == Alphabet.normalize(state.settings.startWord)) return MoveError.IsStartWord
        state.ownerOf(word)?.let { owner ->
            return MoveError.AlreadyUsed(raw, owner, state.players[owner].name)
        }
        if (!dictionary.contains(word)) return MoveError.NotInDictionary(raw)
        if (BoardWords.canRead(before, word)) return MoveError.AlreadyOnBoard(raw)
        return null
    }
}
