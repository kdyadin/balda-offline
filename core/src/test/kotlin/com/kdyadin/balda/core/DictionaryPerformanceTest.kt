package com.kdyadin.balda.core

import com.kdyadin.balda.core.dictionary.DictionaryFormat
import com.kdyadin.balda.core.dictionary.SortedWordDictionary
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты производительности на настоящем словаре из `app/src/main/assets`.
 * Пропускаются, если словарь ещё не собран (см. tools/build-dictionary).
 */
class DictionaryPerformanceTest {

    private val assetsDir = File(System.getProperty("balda.assetsDir") ?: "../app/src/main/assets")
    private val wordsFile = File(assetsDir, "words.bin")
    private val startWordsFile = File(assetsDir, "start_words.txt")

    private fun loadDictionary(): SortedWordDictionary = wordsFile.inputStream().use { DictionaryFormat.read(it) }

    @Test
    fun `загрузка словаря быстрее 1 секунды`() {
        assumeTrue(wordsFile.exists())
        loadDictionary() // прогрев JIT и кэша ФС
        val start = System.nanoTime()
        val dict = loadDictionary()
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Словарь: ${dict.size} слов, загрузка $ms мс, размер файла ${wordsFile.length() / 1024} КБ")
        assertTrue(dict.size > 20_000, "слишком маленький словарь: ${dict.size}")
        assertTrue(ms < 1000, "загрузка заняла $ms мс")
        assertTrue(wordsFile.length() < 10L * 1024 * 1024, "словарь больше 10 МБ")
    }

    @Test
    fun `проверка слова быстрее 10 мс`() {
        assumeTrue(wordsFile.exists())
        val dict = loadDictionary()
        val random = Random(42)
        val probes = (1..10_000).map { i ->
            if (i % 2 == 0) dict.wordAt(random.nextInt(dict.size))
            else (1..random.nextInt(2, 8)).map { Alphabet.NORMALIZED[random.nextInt(32)] }.joinToString("")
        }
        var hits = 0
        val start = System.nanoTime()
        for (p in probes) if (dict.contains(p)) hits++
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perLookupMs = totalMs / probes.size
        println("10 000 проверок: $totalMs мс, найдено $hits")
        assertTrue(perLookupMs < 10, "проверка слова: $perLookupMs мс")
        assertTrue(hits >= probes.size / 2)
    }

    @Test
    fun `поиск подсказки на пустом поле 7x7 быстрее 2 секунд`() {
        assumeTrue(wordsFile.exists() && startWordsFile.exists())
        val dict = loadDictionary()
        val picker = StartWordPicker(startWordsFile.readLines(), Random(5))
        for (size in Board.SUPPORTED_SIZES) {
            val word = assertNotNull(picker.pick(size), "нет стартовых слов длины $size")
            val state = GameEngine.newGame(GameSettings(boardSize = size, startWord = word))
            val start = System.nanoTime()
            val hint = HintFinder(dict, Random(1)).findHint(state, timeBudgetMillis = 10_000)
            val ms = (System.nanoTime() - start) / 1_000_000
            println("Подсказка ${size}x$size для «$word»: ${hint?.word} за $ms мс")
            assertNotNull(hint)
            assertTrue(ms < 2000, "поиск подсказки занял $ms мс")
        }
    }

    @Test
    fun `поиск подсказки в разгаре партии 7x7 быстрее 2 секунд`() {
        assumeTrue(wordsFile.exists() && startWordsFile.exists())
        val dict = loadDictionary()
        val picker = StartWordPicker(startWordsFile.readLines(), Random(9))
        val word = assertNotNull(picker.pick(7))
        var state = GameEngine.newGame(GameSettings(boardSize = 7, startWord = word))
        val finder = HintFinder(dict, Random(2))
        var slowest = 0L
        // Играем подсказками, пока есть ходы: поле постепенно заполняется, поиск усложняется.
        var moves = 0
        while (!state.isFinished && moves < 20) {
            val start = System.nanoTime()
            val hint = finder.findHint(state, timeBudgetMillis = 10_000) ?: break
            val ms = (System.nanoTime() - start) / 1_000_000
            slowest = maxOf(slowest, ms)
            val result = GameEngine.applyMove(state, MoveProposal(hint.cell, hint.letter, hint.path), dict)
            state = (result as MoveResult.Accepted).state
            moves++
        }
        println("Сыграно ходов подсказками: $moves, самый долгий поиск: $slowest мс")
        assertTrue(moves >= 10, "слишком мало ходов: $moves")
        assertTrue(slowest < 2000, "самый долгий поиск подсказки: $slowest мс")
    }
}
