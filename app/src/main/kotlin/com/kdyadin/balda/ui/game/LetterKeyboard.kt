package com.kdyadin.balda.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdyadin.balda.core.Alphabet

/** Экранная клавиатура русского алфавита (включая Ё), четыре ряда по алфавиту. */
@Composable
fun LetterKeyboard(
    enabled: Boolean,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = Alphabet.KEYBOARD_ROWS
    val maxKeys = rows.maxOf { it.length }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 4.dp
        val keyWidth = ((maxWidth - gap * (maxKeys + 1)) / maxKeys).coerceAtMost(56.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (ch in row) {
                        val bg = if (enabled) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainer
                        val fg = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        Box(
                            modifier = Modifier
                                .width(keyWidth)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable(enabled = enabled) { onLetter(ch) }
                                .semantics { contentDescription = "Буква ${ch.uppercaseChar()}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ch.uppercaseChar().toString(),
                                color = fg,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
