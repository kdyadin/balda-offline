package com.kdyadin.balda.core.dictionary

import com.kdyadin.balda.core.Alphabet
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Бинарный формат словаря `words.bin`:
 *
 * ```
 * magic   4 байта  "BLDW"
 * version 1 байт   = 1
 * count   4 байта  big-endian int, число слов
 * далее для каждого слова (в отсортированном порядке):
 *   len   1 байт   длина слова в буквах
 *   len байт       индексы букв в [Alphabet.NORMALIZED] (0..31)
 * ```
 *
 * Тот же формат пишет `tools/build-dictionary/build_words.py`.
 */
object DictionaryFormat {
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'L'.code.toByte(), 'D'.code.toByte(), 'W'.code.toByte())
    private const val VERSION = 1

    fun read(input: InputStream): SortedWordDictionary {
        val data = DataInputStream(input.buffered(1 shl 16))
        val magic = ByteArray(4)
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IOException("Неверный формат словаря")
        val version = data.readUnsignedByte()
        if (version != VERSION) throw IOException("Неподдерживаемая версия словаря: $version")
        val count = data.readInt()
        if (count < 0) throw IOException("Повреждён словарь")
        val alphabet = Alphabet.NORMALIZED
        val words = arrayOfNulls<String>(count)
        val buf = CharArray(64)
        for (i in 0 until count) {
            val len = data.readUnsignedByte()
            if (len == 0 || len > buf.size) throw IOException("Повреждён словарь: длина слова $len")
            for (j in 0 until len) {
                val idx = data.readUnsignedByte()
                if (idx >= alphabet.length) throw IOException("Повреждён словарь: индекс буквы $idx")
                buf[j] = alphabet[idx]
            }
            words[i] = String(buf, 0, len)
        }
        @Suppress("UNCHECKED_CAST")
        return SortedWordDictionary(words as Array<String>)
    }

    fun write(words: Collection<String>, output: OutputStream) {
        val sorted = words.map { Alphabet.normalize(it) }.toSortedSet()
        val data = DataOutputStream(output.buffered(1 shl 16))
        data.write(MAGIC)
        data.writeByte(VERSION)
        data.writeInt(sorted.size)
        for (w in sorted) {
            require(w.length in 1..64) { "Недопустимая длина слова: $w" }
            data.writeByte(w.length)
            for (c in w) {
                val idx = Alphabet.indexOf(c)
                require(idx >= 0) { "Недопустимая буква в слове $w" }
                data.writeByte(idx)
            }
        }
        data.flush()
    }
}
