package com.kdyadin.balda.core

import kotlin.random.Random

/**
 * Выбор стартового слова нужной длины. В рамках одной сессии слова не повторяются,
 * пока не исчерпан весь список для данной длины.
 */
class StartWordPicker(
    words: Collection<String>,
    private val random: Random = Random.Default,
) {
    private val byLength: Map<Int, List<String>> = words
        .map { it.trim().lowercase() }
        .filter { Alphabet.isValidWord(it) }
        .distinct()
        .groupBy { it.length }

    private val used = HashMap<Int, MutableSet<String>>()

    fun available(length: Int): Int = byLength[length]?.size ?: 0

    /** Случайное слово длины [length], не выдававшееся ранее в этой сессии. */
    fun pick(length: Int, exclude: String? = null): String? {
        val pool = byLength[length] ?: return null
        if (pool.isEmpty()) return null
        val usedSet = used.getOrPut(length) { HashSet() }
        var candidates = pool.filter { it !in usedSet && it != exclude }
        if (candidates.isEmpty()) {
            usedSet.clear()
            candidates = pool.filter { it != exclude }
            if (candidates.isEmpty()) candidates = pool
        }
        val word = candidates[random.nextInt(candidates.size)]
        usedSet.add(word)
        return word
    }
}
