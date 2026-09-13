package com.kdyadin.balda.core

import com.kdyadin.balda.core.dictionary.WordDictionary
import kotlin.random.Random

/**
 * Поиск подсказки: любой допустимый ход в текущей позиции.
 * Перебирает пустые клетки и буквы, для каждой пары ищет слова обходом в глубину
 * с отсечением по префиксам словаря. Предпочитает более длинные слова; при равной длине — случайное.
 */
class HintFinder(
    private val dictionary: WordDictionary,
    private val random: Random = Random.Default,
) {

    /**
     * @param timeBudgetMillis ограничение по времени; по истечении возвращается лучшее из найденного.
     */
    fun findHint(state: GameState, timeBudgetMillis: Long = 1500): Hint? {
        if (state.isFinished) return null
        val board = state.board
        val forbidden = state.usedWordsNormalized()
        val deadline = System.currentTimeMillis() + timeBudgetMillis

        var best: Hint? = null
        var bestCount = 0
        val emptyCells = board.emptyCells().shuffled(random)
        val letters = Alphabet.NORMALIZED.toList().shuffled(random)
        val readableCache = HashMap<String, Boolean>()

        search@ for (cell in emptyCells) {
            for (letter in letters) {
                val candidate = board.with(cell, letter)
                val found = wordsThrough(candidate, cell)
                for ((word, path) in found) {
                    if (word in forbidden) continue
                    val readable = readableCache.getOrPut(word) { BoardWords.canRead(board, word) }
                    if (readable) continue
                    val current = best
                    if (current == null || word.length > current.word.length) {
                        best = Hint(cell, letter, path, word)
                        bestCount = 1
                    } else if (word.length == current.word.length) {
                        // Резервуарная выборка среди слов максимальной длины.
                        bestCount++
                        if (random.nextInt(bestCount) == 0) best = Hint(cell, letter, path, word)
                    }
                }
                if (System.currentTimeMillis() > deadline) break@search
            }
        }
        return best
    }

    /** Есть ли вообще хоть один допустимый ход. */
    fun hasAnyMove(state: GameState, timeBudgetMillis: Long = 1500): Boolean =
        findHint(state, timeBudgetMillis) != null

    /** Все слова из словаря, читаемые на поле [board] через клетку [through]. */
    private fun wordsThrough(board: Board, through: Cell): List<Pair<String, List<Cell>>> {
        val result = ArrayList<Pair<String, List<Cell>>>()
        val visited = BooleanArray(board.size * board.size)
        val path = ArrayList<Cell>(board.size * board.size)
        val sb = StringBuilder()
        val throughIdx = through.row * board.size + through.col
        for (start in board.filledCells()) {
            dfs(board, start, visited, path, sb, throughIdx, result)
        }
        return result
    }

    private fun dfs(
        board: Board,
        cell: Cell,
        visited: BooleanArray,
        path: ArrayList<Cell>,
        sb: StringBuilder,
        throughIdx: Int,
        out: MutableList<Pair<String, List<Cell>>>,
    ) {
        val idx = cell.row * board.size + cell.col
        sb.append(Alphabet.normalizeChar(board[cell]!!))
        if (!dictionary.hasPrefix(sb.toString())) {
            sb.setLength(sb.length - 1)
            return
        }
        visited[idx] = true
        path.add(cell)
        if (path.size >= 2 && visited[throughIdx] && dictionary.contains(sb.toString())) {
            out.add(sb.toString() to path.toList())
        }
        for (next in board.neighbors(cell)) {
            val nIdx = next.row * board.size + next.col
            if (visited[nIdx] || board[next] == null) continue
            dfs(board, next, visited, path, sb, throughIdx, out)
        }
        path.removeAt(path.size - 1)
        visited[idx] = false
        sb.setLength(sb.length - 1)
    }
}
