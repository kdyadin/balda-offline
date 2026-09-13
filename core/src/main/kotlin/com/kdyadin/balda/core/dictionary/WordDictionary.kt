package com.kdyadin.balda.core.dictionary

/** Словарь допустимых слов. Все слова — в нижнем регистре, Ё нормализована в Е. */
interface WordDictionary {
    val size: Int

    /** Есть ли слово (уже нормализованное). */
    fun contains(word: String): Boolean

    /** Есть ли хотя бы одно слово с таким префиксом (для отсечения при поиске подсказки). */
    fun hasPrefix(prefix: String): Boolean
}

/**
 * Отсортированный массив слов + бинарный поиск. Порядок — по кодам символов (Кириллица а..я идёт подряд),
 * такой же, как в скрипте сборки словаря.
 */
class SortedWordDictionary(private val words: Array<String>) : WordDictionary {
    init {
        for (i in 1 until words.size) {
            require(words[i - 1] < words[i]) { "Словарь должен быть строго отсортирован: ${words[i - 1]} >= ${words[i]}" }
        }
    }

    override val size: Int get() = words.size

    override fun contains(word: String): Boolean = words.binarySearch(word) >= 0

    override fun hasPrefix(prefix: String): Boolean {
        if (prefix.isEmpty()) return words.isNotEmpty()
        val idx = lowerBound(prefix)
        return idx < words.size && words[idx].startsWith(prefix)
    }

    fun wordAt(index: Int): String = words[index]

    /** Индекс первого слова, не меньшего [key]. */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        fun of(words: Collection<String>): SortedWordDictionary =
            SortedWordDictionary(words.toSortedSet().toTypedArray())
    }
}
