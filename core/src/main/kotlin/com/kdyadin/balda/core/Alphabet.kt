package com.kdyadin.balda.core

/**
 * Русский алфавит для игры. В словаре и при сравнении слов буква Ё нормализуется в Е,
 * поэтому «ёж» и «еж» считаются одним и тем же словом.
 */
object Alphabet {
    /** 32 буквы без Ё — именно в таком виде слова хранятся в словаре. */
    const val NORMALIZED = "абвгдежзийклмнопрстуфхцчшщъыьэюя"

    /** Раскладка экранной клавиатуры: четыре ряда по алфавиту, включая Ё (так букву проще найти). */
    val KEYBOARD_ROWS: List<String> = listOf(
        "абвгдеёжз",
        "ийклмноп",
        "рстуфхцч",
        "шщъыьэюя",
    )

    fun normalizeChar(c: Char): Char = when (val l = c.lowercaseChar()) {
        'ё' -> 'е'
        else -> l
    }

    fun normalize(word: String): String {
        val sb = StringBuilder(word.length)
        for (c in word) sb.append(normalizeChar(c))
        return sb.toString()
    }

    fun isRussianLetter(c: Char): Boolean {
        val l = c.lowercaseChar()
        return l in 'а'..'я' || l == 'ё'
    }

    fun isValidWord(word: String): Boolean = word.isNotEmpty() && word.all { isRussianLetter(it) }

    /** Индекс нормализованной буквы в алфавите (0..31) или -1. */
    fun indexOf(c: Char): Int = NORMALIZED.indexOf(normalizeChar(c))
}
