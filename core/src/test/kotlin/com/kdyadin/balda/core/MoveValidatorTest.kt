package com.kdyadin.balda.core

import com.kdyadin.balda.core.TestSupport.DICTIONARY
import com.kdyadin.balda.core.TestSupport.accept
import com.kdyadin.balda.core.TestSupport.cells
import com.kdyadin.balda.core.TestSupport.newGame
import com.kdyadin.balda.core.TestSupport.reject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Стартовое поле 5×5 со словом «балда» в строке 2:
 * ```
 *  . . . . .
 *  . . . . .
 *  б а л д а
 *  . . . . .
 *  . . . . .
 * ```
 */
class MoveValidatorTest {

    @Test
    fun `допустимый ход принимается`() {
        val state = newGame()
        // к над буквой а: к(1,1) → а(2,1) → л(2,2) = «кал»
        val next = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        assertEquals('к', next.board[1, 1])
        assertEquals(1, next.players[0].words.size)
        assertEquals("кал", next.players[0].words[0].word)
        assertEquals(3, next.players[0].score)
        assertEquals(1, next.currentPlayer)
        assertEquals(2, next.moveNumber)
    }

    @Test
    fun `нельзя ставить букву в занятую клетку`() {
        val err = reject(newGame(), MoveProposal(Cell(2, 1), 'к', cells(2, 1, 2, 2)))
        assertIs<MoveError.CellOccupied>(err)
    }

    @Test
    fun `нельзя ставить букву вне поля`() {
        val err = reject(newGame(), MoveProposal(Cell(5, 0), 'к', cells(5, 0, 2, 2)))
        assertIs<MoveError.CellOccupied>(err)
    }

    @Test
    fun `слово из одной буквы недопустимо`() {
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'к', cells(1, 1)))
        assertIs<MoveError.PathTooShort>(err)
    }

    @Test
    fun `путь по диагонали отклоняется`() {
        // к(1,0) → а(2,1) — диагональ
        val err = reject(newGame(), MoveProposal(Cell(1, 0), 'к', cells(1, 0, 2, 1, 2, 2)))
        assertIs<MoveError.Diagonal>(err)
    }

    @Test
    fun `несоседние клетки отклоняются`() {
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 4)))
        assertIs<MoveError.NotAdjacent>(err)
    }

    @Test
    fun `клетка не может использоваться дважды`() {
        // л(1,2) → л(2,2) → л(1,2)
        val err = reject(newGame(), MoveProposal(Cell(1, 2), 'л', cells(1, 2, 2, 2, 1, 2)))
        assertIs<MoveError.CellRepeated>(err)
    }

    @Test
    fun `путь через пустую клетку отклоняется`() {
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'к', cells(1, 1, 1, 2, 2, 2)))
        assertIs<MoveError.EmptyCellInPath>(err)
    }

    @Test
    fun `слово обязано проходить через новую букву`() {
        // ставим к(1,1), но выделяем «бал» из стартового ряда
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'к', cells(2, 0, 2, 1, 2, 2)))
        assertIs<MoveError.NewLetterNotUsed>(err)
    }

    @Test
    fun `слова нет в словаре`() {
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'х', cells(1, 1, 2, 1, 2, 2)))
        assertIs<MoveError.NotInDictionary>(err)
        assertEquals("хал", err.word)
    }

    @Test
    fun `слово не может совпадать со стартовым`() {
        // б(1,1) → а(2,1) → л(2,2) → д(2,3) → а(2,4) = «балда»
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'б', cells(1, 1, 2, 1, 2, 2, 2, 3, 2, 4)))
        assertIs<MoveError.IsStartWord>(err)
    }

    @Test
    fun `слово уже составлено соперником`() {
        var state = newGame()
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        // второй игрок пробует «кал» снизу: к(3,1) → а(2,1) → л(2,2)
        val err = reject(state, MoveProposal(Cell(3, 1), 'к', cells(3, 1, 2, 1, 2, 2)))
        assertIs<MoveError.AlreadyUsed>(err)
        assertEquals(0, err.playerIndex)
        assertEquals("Инна", err.playerName)
    }

    @Test
    fun `слово уже составлено самим игроком`() {
        var state = newGame()
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        state = GameEngine.pass(state)
        val err = reject(state, MoveProposal(Cell(3, 1), 'к', cells(3, 1, 2, 1, 2, 2)))
        assertIs<MoveError.AlreadyUsed>(err)
        assertEquals(0, err.playerIndex)
    }

    @Test
    fun `слово, которое уже читается на поле, отклоняется`() {
        // «бал» уже читается по стартовому ряду; ставим л(1,1) и читаем б(2,0) → а(2,1) → л(1,1)
        val err = reject(newGame(), MoveProposal(Cell(1, 1), 'л', cells(2, 0, 2, 1, 1, 1)))
        assertIs<MoveError.AlreadyOnBoard>(err)
        assertEquals("бал", err.word)
    }

    @Test
    fun `слово, читаемое на поле только через новую букву, допустимо`() {
        // «лад» читается в стартовом ряду; но «дал» до хода не читается только если нет пути…
        // на стартовом поле «дал» читается: д(2,3) → а(2,4)? нет, дальше «л» нет. д(2,3) → а(2,1)? не соседи.
        // Ставим л(1,4): д(2,3) → а(2,4) → л(1,4) = «дал» — допустимо.
        val next = accept(newGame(), MoveProposal(Cell(1, 4), 'л', cells(2, 3, 2, 4, 1, 4)))
        assertEquals("дал", next.players[0].words[0].word)
    }

    @Test
    fun `Ё и Е считаются одной буквой`() {
        // жажда: ж(2,0) а(2,1) ж(2,2) д(2,3) а(2,4); ставим ё(1,2) над ж(2,2): «ёж», в словаре — «еж»
        var state = newGame(startWord = "жажда")
        state = accept(state, MoveProposal(Cell(1, 2), 'ё', cells(1, 2, 2, 2)))
        assertEquals("ёж", state.players[0].words[0].word)
        assertEquals(2, state.players[0].score)
        // теперь «еж» через е(3,2) → ж(2,2) — уже составлено (и уже читается)
        val err = reject(state, MoveProposal(Cell(3, 2), 'е', cells(3, 2, 2, 2)))
        assertIs<MoveError.AlreadyUsed>(err)
    }

    @Test
    fun `проверка шагов пути для UI`() {
        val board = newGame().board.with(Cell(1, 1), 'к')
        assertNull(MoveValidator.checkPathStep(board, emptyList(), Cell(1, 1)))
        assertNull(MoveValidator.checkPathStep(board, cells(1, 1), Cell(2, 1)))
        assertIs<MoveError.Diagonal>(MoveValidator.checkPathStep(board, cells(1, 1), Cell(2, 2)))
        assertIs<MoveError.NotAdjacent>(MoveValidator.checkPathStep(board, cells(1, 1), Cell(2, 4)))
        assertIs<MoveError.CellRepeated>(MoveValidator.checkPathStep(board, cells(1, 1, 2, 1), Cell(1, 1)))
        assertIs<MoveError.EmptyCellInPath>(MoveValidator.checkPathStep(board, cells(1, 1), Cell(0, 1)))
    }

    @Test
    fun `валидатор не меняет поле`() {
        val state = newGame()
        MoveValidator.validate(state, DICTIONARY, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        assertNull(state.board[1, 1])
    }
}
