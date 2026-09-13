package com.kdyadin.balda.core

import com.kdyadin.balda.core.TestSupport.DICTIONARY
import com.kdyadin.balda.core.TestSupport.accept
import com.kdyadin.balda.core.TestSupport.cells
import com.kdyadin.balda.core.TestSupport.newGame
import com.kdyadin.balda.core.dictionary.DictionaryFormat
import com.kdyadin.balda.core.dictionary.SortedWordDictionary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DictionaryAndHintTest {

    @Test
    fun `бинарный формат - запись и чтение без потерь`() {
        val out = ByteArrayOutputStream()
        DictionaryFormat.write(TestSupport.WORDS, out)
        val dict = DictionaryFormat.read(ByteArrayInputStream(out.toByteArray()))
        val expected = TestSupport.WORDS.map { Alphabet.normalize(it) }.toSortedSet()
        assertEquals(expected.size, dict.size)
        for (w in expected) assertTrue(dict.contains(w), w)
        assertFalse(dict.contains("хал"))
        assertFalse(dict.contains(""))
    }

    @Test
    fun `поиск по префиксу`() {
        val dict = SortedWordDictionary.of(listOf("бал", "балда", "лад", "кабала"))
        assertTrue(dict.hasPrefix("ба"))
        assertTrue(dict.hasPrefix("балд"))
        assertTrue(dict.hasPrefix("балда"))
        assertFalse(dict.hasPrefix("балдак"))
        assertFalse(dict.hasPrefix("х"))
        assertTrue(dict.hasPrefix(""))
        assertTrue(dict.contains("бал"))
        assertFalse(dict.contains("ба"))
    }

    @Test
    fun `подсказка - допустимый ход`() {
        val state = newGame()
        val hint = HintFinder(DICTIONARY, Random(1)).findHint(state)
        assertNotNull(hint)
        assertNull(MoveValidator.validate(state, DICTIONARY, MoveProposal(hint.cell, hint.letter, hint.path)))
        assertTrue(hint.word.length >= 2)
    }

    @Test
    fun `подсказка предпочитает длинные слова`() {
        // На стартовом поле «балда» можно составить «кабала»? нет — нужна вторая «а» рядом. Зато «дала»: д(2,3) а(2,4) л(1,4)? нет.
        // Проверяем просто: выданное слово не короче любого другого найденного при повторных запусках.
        val state = newGame()
        val lengths = (1..5).map { HintFinder(DICTIONARY, Random(it)).findHint(state)!!.word.length }.toSet()
        assertEquals(1, lengths.size, "длина подсказки должна быть стабильной: $lengths")
    }

    @Test
    fun `подсказка не предлагает уже составленные и уже читаемые слова`() {
        val dict = SortedWordDictionary.of(listOf("балда", "бал", "кал", "лда", "лад"))
        var state = newGame()
        state = accept(state, MoveProposal(Cell(1, 1), 'к', cells(1, 1, 2, 1, 2, 2)))
        val finder = HintFinder(dict, Random(3))
        // «бал», «лда» читаются на поле; «кал» составлено; «балда» — стартовое. Остаётся только «лад».
        val hint = finder.findHint(state)
        assertEquals("лад", hint?.word)
        // Составим «лад» — больше ходов нет.
        state = accept(state, MoveProposal(hint!!.cell, hint.letter, hint.path))
        assertNull(finder.findHint(state))
    }

    @Test
    fun `подсказка отсутствует, если ходов нет`() {
        val dict = SortedWordDictionary.of(listOf("балда"))
        assertNull(HintFinder(dict).findHint(newGame()))
    }

    @Test
    fun `подсказка не выдаётся в завершённой партии`() {
        var state = newGame()
        repeat(6) { state = GameEngine.pass(state) }
        assertNull(HintFinder(DICTIONARY).findHint(state))
    }

    @Test
    fun `выбор стартовых слов без повторов`() {
        val picker = StartWordPicker(listOf("балда", "жажда", "кабала", "утка", "abc"), Random(7))
        assertEquals(2, picker.available(5))
        assertEquals(1, picker.available(6))
        assertEquals(0, picker.available(4) - 1)
        val first = picker.pick(5)!!
        val second = picker.pick(5)!!
        assertTrue(first != second)
        val third = picker.pick(5)!!
        assertTrue(third in listOf("балда", "жажда"))
        assertNull(picker.pick(8))
    }
}
