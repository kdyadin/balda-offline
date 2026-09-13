package com.kdyadin.balda.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.core.GameState
import com.kdyadin.balda.core.LogEntry
import com.kdyadin.balda.data.appContainer
import com.kdyadin.balda.ui.activityGameViewModel
import com.kdyadin.balda.ui.game.BoardView
import com.kdyadin.balda.ui.game.GameEvent
import com.kdyadin.balda.ui.game.GamePhase
import com.kdyadin.balda.ui.game.GameUiState
import com.kdyadin.balda.ui.game.LetterKeyboard
import com.kdyadin.balda.ui.pointsWord
import com.kdyadin.balda.ui.toMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(onExitToMenu: () -> Unit, onFinished: () -> Unit) {
    val vm = activityGameViewModel()
    val state by vm.state.collectAsState()
    val container = LocalContext.current.appContainer
    val appSettings by container.settings.settings.collectAsState(initial = null)
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var showLog by remember { mutableStateOf(false) }
    var definitionWord by remember { mutableStateOf<String?>(null) }
    var confirmPass by remember { mutableStateOf(false) }
    var confirmSurrender by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler { onExitToMenu() }

    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.FINISHED) onFinished()
        if (state.phase == GamePhase.NO_GAME) onExitToMenu()
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (appSettings?.hapticsEnabled == false) return@collect
            when (event) {
                GameEvent.LETTER_PLACED -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                GameEvent.MOVE_CONFIRMED -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                GameEvent.ERROR -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                GameEvent.TIMER_EXPIRED -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    LaunchedEffect(state.messageId) {
        val text = state.message ?: return@LaunchedEffect
        val id = state.messageId
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text, withDismissAction = true)
        vm.consumeMessage(id)
    }

    val game = state.game
    if (game == null || state.phase == GamePhase.LOADING) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (state.phase == GamePhase.HANDOFF) {
        HandoffScreen(name = game.current.name, onReady = vm::handoffReady)
        return
    }

    definitionWord?.let { WordDefinitionDialog(word = it, onDismiss = { definitionWord = null }) }

    if (confirmPass) {
        AlertDialog(
            onDismissRequest = { confirmPass = false },
            title = { Text("Пропустить ход?") },
            text = { Text("${game.current.name} пропускает ход. Пасов подряд: ${game.consecutivePasses}. Партия закончится после шести подряд.") },
            confirmButton = { TextButton(onClick = { confirmPass = false; vm.pass() }) { Text("Пас") } },
            dismissButton = { TextButton(onClick = { confirmPass = false }) { Text("Отмена") } },
        )
    }
    if (confirmSurrender) {
        AlertDialog(
            onDismissRequest = { confirmSurrender = false },
            title = { Text("Сдаться?") },
            text = { Text("Счёт игрока ${game.current.name} обнулится, победа достанется сопернику.") },
            confirmButton = { TextButton(onClick = { confirmSurrender = false; vm.surrender() }) { Text("Сдаться") } },
            dismissButton = { TextButton(onClick = { confirmSurrender = false }) { Text("Отмена") } },
        )
    }
    if (showLog) {
        GameLogDialog(game = game, onWord = { definitionWord = it }, onDismiss = { showLog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ход ${game.moveNumber}") },
                navigationIcon = {
                    IconButton(onClick = onExitToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "В меню (партия сохранится)")
                    }
                },
                actions = {
                    TextButton(onClick = { showLog = true }) { Text("Журнал") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Ещё") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Пропустить ход") }, onClick = { menuOpen = false; confirmPass = true })
                        DropdownMenuItem(text = { Text("Сдаться") }, onClick = { menuOpen = false; confirmSurrender = true })
                        DropdownMenuItem(text = { Text("В меню") }, onClick = { menuOpen = false; onExitToMenu() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            // Клавиатура нужна только в момент выбора буквы для пустой клетки; закреплена внизу, чтобы всегда была видна целиком.
            AnimatedVisibility(
                visible = state.phase == GamePhase.PLAYING && state.selectedCell != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LetterKeyboard(
                        enabled = true,
                        onLetter = vm::onLetter,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScoreBoard(game = game, remainingSeconds = state.remainingSeconds)
            Spacer(Modifier.height(8.dp))
            WordLine(state = state)
            Spacer(Modifier.height(8.dp))
            val board = state.boardWithPending ?: game.board
            val lastMove = (game.log.lastOrNull { it is LogEntry.Word } as? LogEntry.Word)?.entry?.path.orEmpty()
            // Поле не выше ~42% экрана, чтобы клавиатура и кнопки помещались без прокрутки.
            val boardMax = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
            BoardView(
                modifier = Modifier.widthIn(max = boardMax),
                board = board,
                path = state.path,
                pendingCell = state.pendingCell,
                selectedCell = state.selectedCell,
                lastMovePath = if (state.path.isEmpty() && !state.hasPendingLetter) lastMove else emptyList(),
                enabled = state.phase == GamePhase.PLAYING,
                onCellTap = vm::onCellTap,
                onCellDrag = vm::onCellDrag,
            )
            Spacer(Modifier.height(10.dp))
            HintBanner(state = state, onHint = vm::requestHint)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = vm::clearInput,
                    enabled = state.hasPendingLetter || state.selectedCell != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("Сбросить") }
                Button(
                    onClick = vm::confirmMove,
                    enabled = state.canConfirm,
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp),
                ) { Text("Подтвердить") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScoreBoard(game: GameState, remainingSeconds: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerScore(game, 0, Modifier.weight(1f), alignEnd = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
            if (remainingSeconds != null) {
                Text(
                    text = formatTime(remainingSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (remainingSeconds <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (game.consecutivePasses > 0) {
                Text(
                    "пасов: ${game.consecutivePasses}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlayerScore(game, 1, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun PlayerScore(game: GameState, index: Int, modifier: Modifier, alignEnd: Boolean) {
    val player = game.players[index]
    val active = game.currentPlayer == index
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = (if (active && !alignEnd) "▶ " else "") + player.name + (if (active && alignEnd) " ◀" else ""),
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            text = player.score.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WordLine(state: GameUiState) {
    val word = state.currentWord
    val error = state.liveError
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            word.isNotEmpty() -> {
                Text(
                    text = word,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (error == null && state.path.size >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                val hintText = when {
                    state.path.size < 2 -> "Продолжайте выделять слово"
                    error != null -> error.toMessage()
                    else -> "${pointsWord(word.length)} — можно подтверждать"
                }
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null && state.path.size >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            state.hasPendingLetter -> Text(
                "Теперь выделите слово: тапайте по буквам по порядку или ведите пальцем",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            state.selectedCell != null -> Text(
                "Выберите букву на клавиатуре",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                "Тапните по пустой клетке, чтобы поставить букву",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HintBanner(state: GameUiState, onHint: () -> Unit) {
    val game = state.game ?: return
    val hint = game.activeHint
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hint != null) {
            Text(
                text = "Подсказка: ${hint.word.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        } else if (game.settings.hintLimit != 0) {
            val left = state.hintsLeft
            val label = when {
                state.hintSearching -> "Ищем…"
                left == null -> "Подсказка (−${game.settings.hintPenalty})"
                else -> "Подсказка (−${game.settings.hintPenalty}), осталось $left"
            }
            TextButton(onClick = onHint, enabled = state.canUseHint) { Text(label) }
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HandoffScreen(name: String, onReady: () -> Unit) {
    BackHandler { /* поле скрыто намеренно; выход — через «Готов» и меню */ }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = "Передай телефон",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Ходит",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onReady,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Готов", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun GameLogDialog(game: GameState, onWord: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Журнал партии") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Стартовое слово: ${game.settings.startWord.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable { onWord(game.settings.startWord) },
                )
                if (game.log.isEmpty()) Text("Ходов ещё не было", color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (entry in game.log) {
                    val name = game.players[entry.playerIndex].name
                    when (entry) {
                        is LogEntry.Word -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWord(entry.entry.word) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text("${entry.moveNumber}. $name", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(entry.entry.word.uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("  +${entry.entry.points}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        is LogEntry.Pass -> LogLine("${entry.moveNumber}. $name — " + if (entry.byTimer) "время вышло, пас" else "пас")
                        is LogEntry.Hint -> LogLine("${entry.moveNumber}. $name — подсказка «${entry.word.uppercase()}», −${entry.penalty}")
                        is LogEntry.Surrender -> LogLine("${entry.moveNumber}. $name сдаётся")
                    }
                }
                Text(
                    "Тап по слову — толкование",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun LogLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

private fun formatTime(seconds: Int): String = if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else seconds.toString()
