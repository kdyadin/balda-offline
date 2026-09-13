package com.kdyadin.balda.core

/** Поиск слов на поле обходом в глубину по соседним (ортогонально) заполненным клеткам без повторов. */
object BoardWords {

    /**
     * Есть ли на поле допустимый путь, читающийся как [word] (сравнение с нормализацией Ё→Е).
     * Используется правилом «слово уже читается на поле»: проверка идёт по полю без новой буквы.
     */
    fun canRead(board: Board, word: String): Boolean {
        val target = Alphabet.normalize(word)
        if (target.isEmpty()) return false
        val visited = BooleanArray(board.size * board.size)
        for (cell in board.filledCells()) {
            if (Alphabet.normalizeChar(board[cell]!!) == target[0]) {
                if (dfs(board, cell, target, 1, visited)) return true
            }
        }
        return false
    }

    /** Найти любой путь, читающийся как [word], либо null. */
    fun findPath(board: Board, word: String): List<Cell>? {
        val target = Alphabet.normalize(word)
        if (target.isEmpty()) return null
        val visited = BooleanArray(board.size * board.size)
        val path = ArrayList<Cell>(target.length)
        for (cell in board.filledCells()) {
            if (Alphabet.normalizeChar(board[cell]!!) == target[0]) {
                path.clear()
                if (dfsPath(board, cell, target, 1, visited, path)) return path.toList()
            }
        }
        return null
    }

    private fun dfs(board: Board, cell: Cell, target: String, pos: Int, visited: BooleanArray): Boolean {
        if (pos == target.length) return true
        val idx = cell.row * board.size + cell.col
        visited[idx] = true
        try {
            for (next in board.neighbors(cell)) {
                val nIdx = next.row * board.size + next.col
                if (visited[nIdx]) continue
                val letter = board[next] ?: continue
                if (Alphabet.normalizeChar(letter) != target[pos]) continue
                if (dfs(board, next, target, pos + 1, visited)) return true
            }
            return false
        } finally {
            visited[idx] = false
        }
    }

    private fun dfsPath(
        board: Board, cell: Cell, target: String, pos: Int, visited: BooleanArray, path: ArrayList<Cell>,
    ): Boolean {
        path.add(cell)
        if (pos == target.length) return true
        val idx = cell.row * board.size + cell.col
        visited[idx] = true
        try {
            for (next in board.neighbors(cell)) {
                val nIdx = next.row * board.size + next.col
                if (visited[nIdx]) continue
                val letter = board[next] ?: continue
                if (Alphabet.normalizeChar(letter) != target[pos]) continue
                if (dfsPath(board, next, target, pos + 1, visited, path)) return true
            }
            path.removeAt(path.size - 1)
            return false
        } finally {
            visited[idx] = false
        }
    }
}
