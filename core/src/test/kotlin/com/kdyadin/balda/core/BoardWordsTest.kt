package com.kdyadin.balda.core

import com.kdyadin.balda.core.TestSupport.board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardWordsTest {

    @Test
    fun `слово читается по ортогональному пути`() {
        val b = board(
            ".....",
            ".....",
            "балда",
            ".....",
            ".....",
        )
        assertTrue(BoardWords.canRead(b, "бал"))
        assertTrue(BoardWords.canRead(b, "лда"))
        assertTrue(BoardWords.canRead(b, "балда"))
        assertTrue(BoardWords.canRead(b, "адлаб"), "направление не важно")
        assertFalse(BoardWords.canRead(b, "лад"), "буквы есть, но не по соседству в нужном порядке")
        assertFalse(BoardWords.canRead(b, "дал"))
        assertTrue(BoardWords.canRead(b, "б"), "одна буква формально читается; слова короче двух букв отсекает валидатор")
        assertFalse(BoardWords.canRead(b, ""))
    }

    @Test
    fun `путь может поворачивать`() {
        val b = board(
            "к..",
            "ал.",
            "...",
        )
        assertTrue(BoardWords.canRead(b, "кал"))
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)), BoardWords.findPath(b, "кал"))
    }

    @Test
    fun `диагональ не считается путём`() {
        val b = board(
            "к..",
            ".а.",
            "..л",
        )
        assertFalse(BoardWords.canRead(b, "кал"))
        assertNull(BoardWords.findPath(b, "кал"))
    }

    @Test
    fun `клетка не используется дважды`() {
        val b = board(
            "кол",
            "...",
            "...",
        )
        assertTrue(BoardWords.canRead(b, "кол"))
        assertFalse(BoardWords.canRead(b, "колокол"), "вариант «колокол» из К, О, Л не поддерживается")
        assertFalse(BoardWords.canRead(b, "око"))
    }

    @Test
    fun `Ё на поле читается как Е`() {
        val b = board(
            "ёж.",
            "...",
            "...",
        )
        assertTrue(BoardWords.canRead(b, "еж"))
        assertTrue(BoardWords.canRead(b, "ёж"))
    }

    @Test
    fun `пустые клетки прерывают путь`() {
        val b = board(
            "к.л",
            "...",
            "...",
        )
        assertFalse(BoardWords.canRead(b, "кл"))
    }
}
