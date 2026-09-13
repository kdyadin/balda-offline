package com.kdyadin.balda.data

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Простые звуки без ассетов через [ToneGenerator]. Включаются в настройках, по умолчанию выключены. */
class SoundPlayer(private val settings: SettingsRepository) {
    private val scope = CoroutineScope(Dispatchers.Default)

    enum class Sound(val tone: Int, val durationMs: Int) {
        LETTER(ToneGenerator.TONE_PROP_BEEP, 60),
        CONFIRM(ToneGenerator.TONE_PROP_ACK, 150),
        ERROR(ToneGenerator.TONE_PROP_NACK, 150),
        TIMER_END(ToneGenerator.TONE_SUP_ERROR, 300),
    }

    fun play(sound: Sound) {
        scope.launch {
            if (!settings.settings.first().soundEnabled) return@launch
            runCatching {
                val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                generator.startTone(sound.tone, sound.durationMs)
                kotlinx.coroutines.delay(sound.durationMs + 50L)
                generator.release()
            }
        }
    }
}
