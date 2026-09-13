package com.kdyadin.balda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.data.AppSettings
import com.kdyadin.balda.data.DefinitionsStatus
import com.kdyadin.balda.data.appContainer
import com.kdyadin.balda.ui.activityGameViewModel
import com.kdyadin.balda.ui.game.GamePhase
import kotlinx.coroutines.launch

@Composable
fun MenuScreen(
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val gameVm = activityGameViewModel()
    val gameState by gameVm.state.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = null)
    val definitionsStatus by container.definitions.status.collectAsState()
    val scope = rememberCoroutineScope()

    val hasUnfinished = gameState.phase == GamePhase.PLAYING || gameState.phase == GamePhase.HANDOFF

    // Предложение загрузить базу толкований — один раз при первом запуске.
    val current = settings
    val showPrompt = current != null && !current.definitionsPromptShown &&
        definitionsStatus is DefinitionsStatus.NotDownloaded && container.definitions.isConfigured
    if (showPrompt) {
        DefinitionsPromptDialog(
            onDownload = {
                container.definitions.startDownload()
                scope.launch { container.settings.setDefinitionsPromptShown() }
            },
            onLater = { scope.launch { container.settings.setDefinitionsPromptShown() } },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "БАЛДА",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "игра в слова для двоих",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            val buttonModifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(52.dp)
            if (hasUnfinished) {
                val g = gameState.game
                Button(onClick = onContinue, modifier = buttonModifier) {
                    Text("Продолжить партию")
                }
                if (g != null) {
                    Text(
                        text = "${g.players[0].name} ${g.players[0].score} : ${g.players[1].score} ${g.players[1].name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onNewGame, modifier = buttonModifier) { Text("Новая игра") }
            } else {
                Button(onClick = onNewGame, modifier = buttonModifier) { Text("Новая игра") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRules, modifier = buttonModifier) { Text("Правила") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSettings, modifier = buttonModifier) { Text("Настройки") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAbout, modifier = buttonModifier) { Text("О программе") }

            val status = definitionsStatus
            if (status is DefinitionsStatus.Downloading) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Загрузка словаря толкований… ${status.fraction?.let { "${(it * 100).toInt()}%" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefinitionsPromptDialog(onDownload: () -> Unit, onLater: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Словарь толкований") },
        text = {
            Text(
                "Игра полностью работает без интернета. Если хотите видеть толкования составленных слов, " +
                    "можно один раз загрузить базу толкований (около 9 МБ). Позже это можно сделать в настройках.",
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDownload) { Text("Загрузить") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onLater) { Text("Позже") } },
    )
}
