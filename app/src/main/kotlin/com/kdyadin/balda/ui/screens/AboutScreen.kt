package com.kdyadin.balda.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdyadin.balda.BuildConfig

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SimpleScreen(title = "О программе", onBack = onBack) {
        Text("Балда", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Версия ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Paragraph(
            "Игра в слова для двоих на одном устройстве. Работает полностью офлайн: без аккаунтов, рекламы, " +
                "аналитики и сбора данных. Единственное разрешение — доступ в интернет — используется только для " +
                "одноразовой загрузки словаря толкований по вашему желанию.",
        )

        SectionTitle("Словарь проверки слов")
        Paragraph(
            "Список существительных — проект «Russian-Nouns» Антона Сергиенко (github.com/Harrix/Russian-Nouns), " +
                "лицензия MIT. Список получен из морфологического словаря OpenCorpora (opencorpora.org), " +
                "лицензия Creative Commons Attribution-ShareAlike 4.0.",
        )
        Paragraph(
            "Для отбора стартовых слов использован частотный список проекта FrequencyWords " +
                "(github.com/hermitdave/FrequencyWords), лицензия CC BY-SA 4.0.",
        )

        SectionTitle("Словарь толкований")
        Paragraph(
            "Толкования слов взяты из Русского Викисловаря (ru.wiktionary.org). Текст доступен по лицензии " +
                "Creative Commons Attribution-ShareAlike 4.0 (creativecommons.org/licenses/by-sa/4.0/). " +
                "База сформирована автоматически и может содержать неточности.",
        )

        SectionTitle("Правила")
        Paragraph(
            "Правила игры сверены с описаниями в русской Википедии («Балда (игра)») и на сайтах pravilaigr.ru, " +
                "minigames.mail.ru и add-hobby.ru. Принятые варианты: без диагоналей, клетка используется в слове " +
                "один раз, три паса подряд у каждого завершают партию.",
        )

        SectionTitle("Код")
        Paragraph("Kotlin, Jetpack Compose, Material 3. Исходный код: github.com/kdyadin/balda-offline.")
        Spacer(Modifier.height(24.dp))
    }
}
