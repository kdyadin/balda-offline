package com.kdyadin.balda.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Cell(val row: Int, val col: Int) {
    /** Соседство только по горизонтали и вертикали, диагонали запрещены. */
    fun isOrthogonalNeighbor(other: Cell): Boolean =
        abs(row - other.row) + abs(col - other.col) == 1

    fun isDiagonalNeighbor(other: Cell): Boolean =
        abs(row - other.row) == 1 && abs(col - other.col) == 1

    override fun toString(): String = "($row,$col)"
}

/**
 * Неизменяемое квадратное поле. Пустая клетка — [EMPTY].
 * Буквы хранятся так, как их поставил игрок (Ё сохраняется), нормализация происходит при проверке.
 */
@Serializable
data class Board(val size: Int, val cells: String) {
    init {
        require(size in MIN_SIZE..MAX_SIZE) { "Недопустимый размер поля: $size" }
        require(cells.length == size * size) { "Ожидалось ${size * size} клеток, получено ${cells.length}" }
    }

    operator fun get(cell: Cell): Char? = get(cell.row, cell.col)

    operator fun get(row: Int, col: Int): Char? {
        val c = cells[index(row, col)]
        return if (c == EMPTY) null else c
    }

    fun isEmpty(cell: Cell): Boolean = get(cell) == null

    fun isInside(cell: Cell): Boolean = cell.row in 0 until size && cell.col in 0 until size

    fun with(cell: Cell, letter: Char): Board {
        require(isInside(cell)) { "Клетка $cell вне поля" }
        require(Alphabet.isRussianLetter(letter)) { "Недопустимая буква: $letter" }
        val sb = StringBuilder(cells)
        sb.setCharAt(index(cell.row, cell.col), letter.lowercaseChar())
        return copy(cells = sb.toString())
    }

    fun without(cell: Cell): Board {
        val sb = StringBuilder(cells)
        sb.setCharAt(index(cell.row, cell.col), EMPTY)
        return copy(cells = sb.toString())
    }

    val isFull: Boolean get() = cells.indexOf(EMPTY) < 0

    val emptyCount: Int get() = cells.count { it == EMPTY }

    fun emptyCells(): List<Cell> = allCells().filter { isEmpty(it) }

    fun filledCells(): List<Cell> = allCells().filter { !isEmpty(it) }

    fun allCells(): List<Cell> {
        val n = size // внутри buildList имя size относится к списку
        return buildList(n * n) {
            for (r in 0 until n) for (c in 0 until n) add(Cell(r, c))
        }
    }

    fun neighbors(cell: Cell): List<Cell> {
        val n = size
        return buildList(4) {
            if (cell.row > 0) add(Cell(cell.row - 1, cell.col))
            if (cell.row < n - 1) add(Cell(cell.row + 1, cell.col))
            if (cell.col > 0) add(Cell(cell.row, cell.col - 1))
            if (cell.col < n - 1) add(Cell(cell.row, cell.col + 1))
        }
    }

    /** Слово, читаемое по пути (буквы как на поле). Пустые клетки дают '?'. */
    fun wordAlong(path: List<Cell>): String {
        val sb = StringBuilder(path.size)
        for (cell in path) sb.append(get(cell) ?: '?')
        return sb.toString()
    }

    private fun index(row: Int, col: Int): Int = row * size + col

    companion object {
        const val EMPTY = '.'
        const val MIN_SIZE = 3
        const val MAX_SIZE = 9
        val SUPPORTED_SIZES = listOf(5, 6, 7)

        /** Поле со стартовым словом в центральной строке. */
        fun withStartWord(size: Int, word: String): Board {
            require(word.length == size) { "Длина стартового слова должна быть $size" }
            require(Alphabet.isValidWord(word)) { "Стартовое слово содержит недопустимые символы" }
            val sb = StringBuilder(size * size)
            val middle = size / 2
            for (r in 0 until size) {
                if (r == middle) sb.append(word.lowercase()) else repeat(size) { sb.append(EMPTY) }
            }
            return Board(size, sb.toString())
        }

        fun empty(size: Int): Board = Board(size, EMPTY.toString().repeat(size * size))
    }
}
