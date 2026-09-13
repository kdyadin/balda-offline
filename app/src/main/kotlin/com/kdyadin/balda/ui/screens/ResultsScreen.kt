package com.kdyadin.balda.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.core.FinishReason
import com.kdyadin.balda.core.GameStatus
import com.kdyadin.balda.core.PlayerState
import com.kdyadin.balda.ui.activityGameViewModel
import com.kdyadin.balda.ui.lettersWord
import com.kdyadin.balda.ui.pointsWord

@Composable
fun ResultsScreen(onPlayAgain: () -> Unit, onMenu: () -> Unit) {
    val vm = activityGameViewModel()
    val state by vm.state.collectAsState()
    var definitionWord by remember { mutableStateOf<String?>(null) }

    BackHandler { onMenu() }

    val game = state.game
    val status = game?.status as? GameStatus.Finished
    if (game == null || status == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    definitionWord?.let { WordDefinitionDialog(word = it, onDismiss = { definitionWord = null }) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val winner = status.winner
            Text(
                text = when (winner) {
                    null -> "Ничья"
                    else -> "Победа: ${game.players[winner].name}"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when (status.reason) {
                    FinishReason.BOARD_FULL -> "Поле заполнено"
                    FinishReason.PASSES -> "Шесть пасов подряд"
                    FinishReason.SURRENDER -> "${game.players.first { it.surrendered }.name} сдаётся"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (p in game.players) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            p.score.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (p.penalty > 0) {
                            Text(
                                "слова ${p.wordPoints}, подсказки −${p.penalty}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.surrendered) {
                            Text("сдача", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Стартовое слово: ${game.settings.startWord.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { definitionWord = game.settings.startWord },
            )
            for (p in game.players) {
                PlayerWords(p, onWord = { definitionWord = it })
            }
            Text(
                "Тап по слову — толкование",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.playAgain(); onPlayAgain() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Играть ещё раз с теми же настройками") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.abandonGame(); onMenu() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("В меню") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlayerWords(player: PlayerState, onWord: (String) -> Unit) {
    SectionTitle("${player.name}: ${player.words.size} ${pluralWords(player.words.size)}, ${pointsWord(player.wordPoints)}")
    if (player.words.isEmpty()) {
        Text("Ни одного слова", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (w in player.words) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onWord(w.word) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                w.word.uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                lettersWord(w.word.length),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (player.hintsUsed > 0) {
        Text(
            "Подсказок: ${player.hintsUsed} (−${player.penalty})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pluralWords(n: Int): String = com.kdyadin.balda.ui.pluralize(n, "слово", "слова", "слов")
