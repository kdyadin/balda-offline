package com.kdyadin.balda.core

import com.kdyadin.balda.core.TestSupport.accept
import com.kdyadin.balda.core.TestSupport.board
import com.kdyadin.balda.core.TestSupport.cells
import com.kdyadin.balda.core.TestSupport.newGame
import com.kdyadin.balda.core.TestSupport.settings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    @Test
    fun `новая партия - стартовое слово в центральной строке, число пустых клеток чётно`() {
        for (size in Board.SUPPORTED_SIZES) {
            val word = "а".repeat(size)
            val state = GameEngine.newGame(settings(size, word, firstPlayer = 1))
            assertEquals(size * size - size, state.board.emptyCount)
            assertEquals(0, state.board.emptyCount % 2)
            for (c in 0 until size) assertEquals('а', state.board[size / 2, c])
            assertEquals(1, state.currentPlayer)
            assertEquals(1, state.moveNumber)
            assertFalse(state.isFinished)
        }
    }

    @Test
    fun `пас передаёт ход и увеличивает счётчик пасов`() {
        val state = GameEngine.pass(newGame())
        assertEquals(1, state.currentPlayer)
        assertEquals(1, state.consecutivePasses)
        assertIs<LogEntry.Pass>(state.log.single())
        assertFalse((state.log.single() as LogEntry.Pass).byTimer)
    }

    @Test
    fun `истёкший таймер засчитывается как пас`() {
        val state = GameEngine.timerExpired(newGame())
        assertEquals(1, state.consecutivePasses)
        assertTrue((state.log.single() as LogEntry.Pass).byTimer)
    }

    @Test
    fun `шесть пасов подряд завершают партию`() {
        var state = newGame()
        repeat(5) {
            state = GameEngine.pass(state)
            assertFalse(state.isFinished)
        }
        state = GameEngine.pass(state)
        val status = assertIs<GameStatus.Finished>(state.status)
        assertEquals(FinishReason.PASSES, status.reason)
        assertNull(status.winner, "при равном счёте — ничья")
    }

    @Test
    fun `ход сбрасывает счётчик пасов`() {
        var state = newGame()
        repeat(3) { state = GameEngine.pass(state) }
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        assertEquals(0, state.consecutivePasses)
        repeat(5) { state = GameEngine.pass(state) }
        assertFalse(state.isFinished)
        state = GameEngine.pass(state)
        val status = assertIs<GameStatus.Finished>(state.status)
        assertEquals(FinishReason.PASSES, status.reason)
        assertEquals(1, status.winner, "победил тот, кто составил слово")
    }

    @Test
    fun `заполнение поля завершает партию и определяет победителя`() {
        val base = GameEngine.newGame(settings(size = 3, startWord = "бал"))
        val state = base.copy(
            board = board(
                "к.л",
                "бал",
                "ада",
            ),
        )
        val next = accept(state, MoveProposal(Cell(0, 1), 'а', cells(0, 0, 0, 1, 0, 2)))
        assertTrue(next.board.isFull)
        val status = assertIs<GameStatus.Finished>(next.status)
        assertEquals(FinishReason.BOARD_FULL, status.reason)
        assertEquals(0, status.winner)
        assertEquals(3, next.players[0].score)
        assertEquals(0, next.players[1].score)
    }

    @Test
    fun `при равенстве очков - ничья`() {
        var state = newGame()
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2))) // кал, 3
        state = accept(state, MoveProposal(Cell(1, 4), 'л', cells(2, 3, 2, 4, 1, 4))) // дал, 3
        assertEquals(3, state.players[0].score)
        assertEquals(3, state.players[1].score)
        assertNull(GameEngine.winnerByScore(state.players))
    }

    @Test
    fun `подсказка списывает штраф и отмечается в журнале`() {
        val state = newGame()
        val hint = Hint(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2), "кал")
        val next = GameEngine.useHint(state, hint)
        assertEquals(1, next.players[0].hintsUsed)
        assertEquals(5, next.players[0].penalty)
        assertEquals(-5, next.players[0].score)
        assertEquals(hint, next.activeHint)
        assertEquals(0, next.currentPlayer, "ход остаётся у игрока")
        val log = assertIs<LogEntry.Hint>(next.log.single())
        assertEquals("кал", log.word)
        assertEquals(5, log.penalty)

        val after = accept(next, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        assertEquals(3 - 5, after.players[0].score)
        assertNull(after.activeHint, "подсказка сбрасывается при смене хода")
    }

    @Test
    fun `лимит подсказок соблюдается`() {
        val hint = Hint(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2), "кал")
        val s1 = GameEngine.useHint(newGame(), hint)
        assertFailsWith<IllegalStateException> { GameEngine.useHint(s1, hint) }

        val unlimited = GameEngine.newGame(settings(hintLimit = GameSettings.UNLIMITED_HINTS))
        var s = unlimited
        repeat(10) { s = GameEngine.useHint(s, hint) }
        assertEquals(10, s.players[0].hintsUsed)

        val none = GameEngine.newGame(settings(hintLimit = 0))
        assertFalse(none.settings.hintsAllowed(0))
    }

    @Test
    fun `сдача обнуляет счёт и отдаёт победу сопернику`() {
        var state = newGame()
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2))) // Инна: 3
        state = GameEngine.pass(state) // Олег
        assertEquals(0, state.currentPlayer)
        state = GameEngine.surrender(state) // сдаётся Инна
        val status = assertIs<GameStatus.Finished>(state.status)
        assertEquals(FinishReason.SURRENDER, status.reason)
        assertEquals(1, status.winner)
        assertEquals(0, state.players[0].score)
        assertTrue(state.players[0].surrendered)
        assertIs<LogEntry.Surrender>(state.log.last())
    }

    @Test
    fun `после завершения ходы невозможны`() {
        var state = newGame()
        repeat(6) { state = GameEngine.pass(state) }
        assertFailsWith<IllegalStateException> { GameEngine.pass(state) }
        assertFailsWith<IllegalStateException> {
            GameEngine.applyMove(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)), TestSupport.DICTIONARY)
        }
    }

    @Test
    fun `состояние сериализуется и восстанавливается без потерь`() {
        var state = newGame()
        state = GameEngine.useHint(state, Hint(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2), "кал"))
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        state = GameEngine.pass(state)
        val json = Json.encodeToString(GameState.serializer(), state)
        val restored = Json.decodeFromString(GameState.serializer(), json)
        assertEquals(state, restored)
    }

    @Test
    fun `очки - по букве за слово`() {
        var state = newGame(startWord = "жажда")
        state = accept(state, MoveProposal(Cell(1, 2), 'ё', cells(1, 2, 2, 2))) // Инна: ёж = 2
        state = accept(state, MoveProposal(Cell(1, 4), 'р', cells(2, 3, 2, 4, 1, 4))) // Олег: дар = 3
        assertEquals(2, state.players[0].score)
        assertEquals(3, state.players[1].score)
        assertEquals(1, GameEngine.winnerByScore(state.players))
    }
}
