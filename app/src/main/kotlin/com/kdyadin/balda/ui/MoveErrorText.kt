package com.kdyadin.balda.ui

import com.kdyadin.balda.core.MoveError

/** Понятные пользователю формулировки причин отклонения хода. */
fun MoveError.toMessage(): String = when (this) {
    MoveError.NoLetterPlaced -> "Сначала поставьте букву в пустую клетку"
    MoveError.CellOccupied -> "Букву можно поставить только в пустую клетку"
    MoveError.PathTooShort -> "Слово должно быть не короче двух букв"
    MoveError.Diagonal -> "Нельзя ходить по диагонали"
    MoveError.NotAdjacent -> "Буквы слова должны стоять в соседних клетках"
    MoveError.CellRepeated -> "Каждую клетку можно использовать только один раз"
    MoveError.EmptyCellInPath -> "Слово не может проходить через пустую клетку"
    MoveError.NewLetterNotUsed -> "Слово должно проходить через новую букву"
    is MoveError.NotInDictionary -> "Такого слова нет в словаре"
    MoveError.IsStartWord -> "Это стартовое слово, его составлять нельзя"
    is MoveError.AlreadyUsed -> "Слово уже составлено игроком $playerName"
    is MoveError.AlreadyOnBoard -> "Слово уже читается на поле"
}

fun pluralize(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

fun lettersWord(n: Int): String = "$n ${pluralize(n, "буква", "буквы", "букв")}"
fun pointsWord(n: Int): String = "$n ${pluralize(n, "очко", "очка", "очков")}"
