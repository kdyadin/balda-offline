package com.kdyadin.balda.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import com.kdyadin.balda.core.Board
import com.kdyadin.balda.core.Cell
import com.kdyadin.balda.ui.theme.LocalBoardColors

/**
 * Игровое поле. Масштабируется под доступную ширину, 7×7 помещается без прокрутки.
 * Ввод: тап по клетке и ведение пальцем по клеткам (для выделения слова).
 */
@Composable
fun BoardView(
    board: Board,
    path: List<Cell>,
    pendingCell: Cell?,
    selectedCell: Cell?,
    lastMovePath: List<Cell> = emptyList(),
    enabled: Boolean = true,
    onCellTap: (Cell) -> Unit,
    onCellDrag: (Cell) -> Unit,
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 3.dp
        val available = minOf(maxWidth, maxHeight.takeIf { it.isFinite } ?: maxWidth)
        val cellSize: Dp = ((available - gap * (board.size + 1)) / board.size).coerceAtLeast(40.dp)
        val boardSize = cellSize * board.size + gap * (board.size + 1)
        val density = LocalDensity.current
        val cellPx = with(density) { cellSize.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val pathIndex = remember(path) { path.withIndex().associate { (i, c) -> c to i } }
        val lastMoveSet = remember(lastMovePath) { lastMovePath.toSet() }

        fun cellAt(offset: Offset): Cell? {
            val stride = cellPx + gapPx
            val col = ((offset.x - gapPx) / stride).toInt()
            val row = ((offset.y - gapPx) / stride).toInt()
            if (row !in 0 until board.size || col !in 0 until board.size) return null
            // игнорируем попадание в зазор между клетками
            val inCellX = offset.x - gapPx - col * stride
            val inCellY = offset.y - gapPx - row * stride
            if (inCellX < 0 || inCellY < 0 || inCellX > cellPx || inCellY > cellPx) return null
            return Cell(row, col)
        }

        Box(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.gridLine)
                .pointerInput(board, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> cellAt(offset)?.let(onCellTap) }
                }
                .pointerInput(board, enabled) {
                    if (!enabled) return@pointerInput
                    var last: Cell? = null
                    detectDragGestures(
                        onDragStart = { offset ->
                            last = cellAt(offset)
                            last?.let(onCellDrag)
                        },
                        onDragEnd = { last = null; onDragEnd() },
                        onDragCancel = { last = null; onDragEnd() },
                    ) { change, _ ->
                        change.consume()
                        val cell = cellAt(change.position) ?: return@detectDragGestures
                        if (cell != last) {
                            last = cell
                            onCellDrag(cell)
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.padding(gap)) {
                for (row in 0 until board.size) {
                    Row {
                        for (col in 0 until board.size) {
                            val cell = Cell(row, col)
                            val letter = board[cell]
                            val inPath = pathIndex[cell]
                            val background = when {
                                inPath != null -> colors.pathCell
                                cell == pendingCell -> colors.pendingCell
                                cell == selectedCell -> colors.selectedCell
                                cell in lastMoveSet -> colors.hintCell
                                letter == null -> colors.emptyCell
                                row == board.size / 2 -> colors.startWordCell
                                else -> colors.filledCell
                            }
                            val textColor = if (inPath != null) colors.pathText else colors.cellText
                            BoardCell(
                                letter = letter,
                                size = cellSize,
                                background = background,
                                textColor = textColor,
                                pathNumber = inPath?.plus(1),
                                description = cellDescription(cell, letter, inPath, cell == pendingCell),
                                onClick = { if (enabled) onCellTap(cell) },
                                modifier = Modifier.padding(
                                    end = if (col < board.size - 1) gap else 0.dp,
                                    bottom = if (row < board.size - 1) gap else 0.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun cellDescription(cell: Cell, letter: Char?, pathIndex: Int?, pending: Boolean): String {
    val position = "строка ${cell.row + 1}, столбец ${cell.col + 1}"
    val content = when {
        letter == null -> "пустая клетка"
        pending -> "новая буква ${letter.uppercaseChar()}"
        else -> "буква ${letter.uppercaseChar()}"
    }
    val path = pathIndex?.let { ", в слове позиция ${it + 1}" } ?: ""
    return "$content, $position$path"
}

@Composable
private fun BoardCell(
    letter: Char?,
    size: Dp,
    background: Color,
    textColor: Color,
    pathNumber: Int?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedBackground by animateColorAsState(background, label = "cell")
    val fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBackground)
            .then(
                if (pathNumber != null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) {
            Text(
                text = letter.uppercaseChar().toString(),
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = fontSize,
            )
        }
        if (pathNumber != null) {
            Text(
                text = pathNumber.toString(),
                color = textColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 3.dp, top = 1.dp),
            )
        }
    }
}
