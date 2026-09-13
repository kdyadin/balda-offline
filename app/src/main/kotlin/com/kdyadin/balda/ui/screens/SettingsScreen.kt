package com.kdyadin.balda.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.data.AppSettings
import com.kdyadin.balda.data.DefinitionsStatus
import com.kdyadin.balda.data.ThemeMode
import com.kdyadin.balda.data.appContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val settingsRepo = container.settings
    val definitions = container.definitions
    val settings by settingsRepo.settings.collectAsState(initial = AppSettings())
    val status by definitions.status.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить базу толкований?") },
            text = { Text("Её можно будет скачать снова в любой момент.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { definitions.delete() } }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    SimpleScreen(title = "Настройки", onBack = onBack) {
        SectionTitle("Оформление")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(ThemeMode.SYSTEM to "Как в системе", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная")
            options.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { scope.launch { settingsRepo.setThemeMode(mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }

        SectionTitle("Игра")
        SwitchRow(
            title = "Экран передачи хода",
            subtitle = "Скрывать поле между ходами, чтобы соперник не подсматривал",
            checked = settings.handoffScreen,
            onChange = { scope.launch { settingsRepo.setHandoffScreen(it) } },
        )
        SwitchRow(
            title = "Вибрация",
            subtitle = "Отклик при постановке буквы и подтверждении слова",
            checked = settings.hapticsEnabled,
            onChange = { scope.launch { settingsRepo.setHapticsEnabled(it) } },
        )
        SwitchRow(
            title = "Звуки",
            subtitle = "Короткие сигналы при ходе и ошибке",
            checked = settings.soundEnabled,
            onChange = { scope.launch { settingsRepo.setSoundEnabled(it) } },
        )

        SectionTitle("Словарь толкований")
        Text(
            "Толкования слов из Русского Викисловаря. Загружаются один раз, после этого интернет не нужен. " +
                "Игра полностью работает и без этой базы.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when (val s = status) {
            is DefinitionsStatus.Ready -> {
                Text("Загружено: ${formatSize(s.sizeBytes)}, слов с толкованиями: ${s.entries}")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Удалить базу толкований (${formatSize(s.sizeBytes)})") }
            }
            is DefinitionsStatus.Downloading -> {
                val f = s.fraction
                Text(
                    if (s.totalBytes > 0) "Загрузка: ${formatSize(s.downloadedBytes)} из ${formatSize(s.totalBytes)}"
                    else "Загрузка: ${formatSize(s.downloadedBytes)}",
                )
                Spacer(Modifier.height(8.dp))
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { definitions.cancelDownload() }) { Text("Отменить") }
            }
            is DefinitionsStatus.Error -> {
                Text("Не загружено. ${s.message}", color = MaterialTheme.colorScheme.error)
                if (s.partialBytes > 0) {
                    Text(
                        "Частично загружено ${formatSize(s.partialBytes)} — загрузка продолжится с этого места",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                DownloadButton(definitions.isConfigured) { definitions.startDownload() }
            }
            DefinitionsStatus.NotDownloaded -> {
                Text("Не загружено (около 9 МБ)")
                Spacer(Modifier.height(8.dp))
                DownloadButton(definitions.isConfigured) { definitions.startDownload() }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DownloadButton(configured: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = configured) { Text("Загрузить словарь толкований") }
    if (!configured) {
        Text(
            "В этой сборке не указан адрес базы толкований",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}
