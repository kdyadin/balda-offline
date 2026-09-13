package com.kdyadin.balda.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.data.DefinitionsStatus
import com.kdyadin.balda.data.appContainer

/** Карточка слова: толкование из локальной базы либо предложение её загрузить. */
@Composable
fun WordDefinitionDialog(word: String, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val definitions = container.definitions
    val status by definitions.status.collectAsState()
    var result by remember(word, status) { mutableStateOf<List<String>?>(null) }
    var loaded by remember(word, status) { mutableStateOf(false) }

    LaunchedEffect(word, status) {
        result = if (status is DefinitionsStatus.Ready) definitions.lookup(word) else null
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word.uppercase(), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                when (val s = status) {
                    is DefinitionsStatus.Ready -> {
                        val defs = result
                        when {
                            !loaded -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            defs.isNullOrEmpty() -> Text(
                                "Толкования этого слова в базе нет.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> defs.forEachIndexed { i, def ->
                                Text(
                                    text = if (defs.size > 1) "${i + 1}. $def" else def,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    is DefinitionsStatus.Downloading -> {
                        Text("Загружается словарь толкований…")
                        val f = s.fraction
                        if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.padding(top = 12.dp))
                        else LinearProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                    }
                    is DefinitionsStatus.Error -> {
                        Text("Толкование не загружено.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    DefinitionsStatus.NotDownloaded -> Text(
                        "Толкование не загружено. Словарь толкований можно скачать один раз — после этого интернет не нужен.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (status is DefinitionsStatus.Ready) {
                    Text(
                        "Источник: Русский Викисловарь, CC BY-SA 4.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            when (status) {
                is DefinitionsStatus.NotDownloaded, is DefinitionsStatus.Error ->
                    if (definitions.isConfigured) {
                        TextButton(onClick = { definitions.startDownload() }) { Text("Загрузить словарь толкований") }
                    }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
