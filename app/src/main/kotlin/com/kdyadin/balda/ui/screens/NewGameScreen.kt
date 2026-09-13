package com.kdyadin.balda.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.core.Board
import com.kdyadin.balda.core.GameSettings
import com.kdyadin.balda.data.AppSettings
import com.kdyadin.balda.data.appContainer
import com.kdyadin.balda.ui.activityGameViewModel
import com.kdyadin.balda.ui.game.GamePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class FirstPlayerChoice { PLAYER_1, PLAYER_2, TOSS }

@Composable
fun NewGameScreen(onBack: () -> Unit, onGameStarted: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val gameVm = activityGameViewModel()
    val gameState by gameVm.state.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<AppSettings?>(null) }
    var boardSize by remember { mutableStateOf(5) }
    var startWord by remember { mutableStateOf("") }
    var name1 by remember { mutableStateOf(AppSettings.DEFAULT_NAME_1) }
    var name2 by remember { mutableStateOf(AppSettings.DEFAULT_NAME_2) }
    var firstChoice by remember { mutableStateOf(FirstPlayerChoice.TOSS) }
    var timer by remember { mutableStateOf(0) }
    var hintLimit by remember { mutableStateOf(1) }
    var hintPenalty by remember { mutableStateOf(5) }
    var handoff by remember { mutableStateOf(true) }
    var tossing by remember { mutableStateOf(false) }
    var confirmOverwrite by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = container.settings.current()
        boardSize = s.boardSize.takeIf { it in Board.SUPPORTED_SIZES } ?: 5
        name1 = s.playerNames[0]
        name2 = s.playerNames[1]
        timer = s.timerSeconds
        hintLimit = s.hintLimit
        hintPenalty = s.hintPenalty
        handoff = s.handoffScreen
        loaded = s
    }
    LaunchedEffect(boardSize, loaded) {
        if (loaded != null) startWord = container.startWords().pick(boardSize) ?: ""
    }

    fun buildSettings(first: Int) = GameSettings(
        boardSize = boardSize,
        startWord = startWord,
        playerNames = listOf(name1.trim().ifBlank { AppSettings.DEFAULT_NAME_1 }, name2.trim().ifBlank { AppSettings.DEFAULT_NAME_2 }),
        firstPlayer = first,
        timerSeconds = timer,
        hintLimit = hintLimit,
        hintPenalty = hintPenalty,
        handoffScreen = handoff,
    )

    fun start(first: Int) {
        val settings = buildSettings(first)
        scope.launch {
            container.settings.rememberGameSettings(settings)
            gameVm.startNewGame(settings)
            onGameStarted()
        }
    }

    fun onStartClicked() {
        val unfinished = gameState.phase == GamePhase.PLAYING || gameState.phase == GamePhase.HANDOFF
        if (unfinished && !confirmOverwrite) {
            confirmOverwrite = true
            return
        }
        if (firstChoice == FirstPlayerChoice.TOSS) tossing = true
        else start(if (firstChoice == FirstPlayerChoice.PLAYER_1) 0 else 1)
    }

    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("Незавершённая партия") },
            text = { Text("Текущая партия будет потеряна. Начать новую?") },
            confirmButton = { TextButton(onClick = { onStartClicked() }) { Text("Начать новую") } },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Отмена") } },
        )
    }

    if (tossing) {
        TossScreen(
            names = listOf(name1.ifBlank { AppSettings.DEFAULT_NAME_1 }, name2.ifBlank { AppSettings.DEFAULT_NAME_2 }),
            onStart = { start(it) },
            onCancel = { tossing = false },
        )
        return
    }

    SimpleScreen(title = "Новая игра", onBack = onBack) {
        SectionTitle("Поле")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Board.SUPPORTED_SIZES.forEachIndexed { i, size ->
                SegmentedButton(
                    selected = boardSize == size,
                    onClick = { boardSize = size },
                    shape = SegmentedButtonDefaults.itemShape(i, Board.SUPPORTED_SIZES.size),
                ) { Text("$size×$size") }
            }
        }

        SectionTitle("Стартовое слово")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = startWord.uppercase().ifBlank { "…" },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                scope.launch { startWord = container.startWords().pick(boardSize, exclude = startWord) ?: startWord }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Другое слово")
            }
        }

        SectionTitle("Игроки")
        OutlinedTextField(
            value = name1,
            onValueChange = { if (it.length <= 20) name1 = it },
            label = { Text("Первый игрок") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name2,
            onValueChange = { if (it.length <= 20) name2 = it },
            label = { Text("Второй игрок") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Кто ходит первым")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                FirstPlayerChoice.PLAYER_1 to name1.ifBlank { AppSettings.DEFAULT_NAME_1 },
                FirstPlayerChoice.PLAYER_2 to name2.ifBlank { AppSettings.DEFAULT_NAME_2 },
                FirstPlayerChoice.TOSS to "Жребий",
            )
            options.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = firstChoice == choice,
                    onClick = { firstChoice = choice },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }

        SectionTitle("Таймер на ход")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GameSettings.TIMER_OPTIONS.forEachIndexed { i, sec ->
                SegmentedButton(
                    selected = timer == sec,
                    onClick = { timer = sec },
                    shape = SegmentedButtonDefaults.itemShape(i, GameSettings.TIMER_OPTIONS.size),
                ) { Text(if (sec == 0) "Выкл" else "$sec с") }
            }
        }

        SectionTitle("Подсказки на игрока")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GameSettings.HINT_LIMIT_OPTIONS.forEachIndexed { i, n ->
                SegmentedButton(
                    selected = hintLimit == n,
                    onClick = { hintLimit = n },
                    shape = SegmentedButtonDefaults.itemShape(i, GameSettings.HINT_LIMIT_OPTIONS.size),
                ) { Text(if (n == GameSettings.UNLIMITED_HINTS) "∞" else n.toString()) }
            }
        }
        if (hintLimit != 0) {
            Text(
                "Штраф за подсказку",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GameSettings.HINT_PENALTY_OPTIONS.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = hintPenalty == p,
                        onClick = { hintPenalty = p },
                        shape = SegmentedButtonDefaults.itemShape(i, GameSettings.HINT_PENALTY_OPTIONS.size),
                    ) { Text(if (p == 0) "Нет" else "−$p") }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Экран передачи хода", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Скрывать поле между ходами",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = handoff, onCheckedChange = { handoff = it })
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStartClicked() },
            enabled = startWord.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (firstChoice == FirstPlayerChoice.TOSS) "Бросить жребий" else "Начать игру")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Жребий: имена быстро сменяют друг друга, затем останавливаются на выбранном. */
@Composable
private fun TossScreen(names: List<String>, onStart: (Int) -> Unit, onCancel: () -> Unit) {
    var shown by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Int?>(null) }
    val scale by animateFloatAsState(if (result != null) 1.15f else 1f, animationSpec = tween(300), label = "toss")

    LaunchedEffect(Unit) {
        val winner = Random.nextInt(2)
        var delayMs = 60L
        var i = 0
        // 13–16 переключений с замедлением (около 3 секунд), заканчиваем на победителе.
        val steps = 13 + Random.nextInt(4)
        while (i < steps) {
            shown = 1 - shown
            delay(delayMs)
            delayMs = (delayMs * 1.13).toLong()
            i++
        }
        shown = winner
        result = winner
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = if (result == null) "Бросаем жребий…" else "Первым ходит",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = names[shown],
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(scale),
            )
            Spacer(Modifier.height(40.dp))
            val r = result
            if (r != null) {
                Button(
                    onClick = { onStart(r) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Начать игру") }
                TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Назад к настройкам") }
            }
        }
    }
}
