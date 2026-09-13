package com.kdyadin.balda.data

import android.content.Context
import com.kdyadin.balda.core.HintFinder
import com.kdyadin.balda.core.StartWordPicker
import com.kdyadin.balda.core.dictionary.DictionaryFormat
import com.kdyadin.balda.core.dictionary.WordDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Простой контейнер зависимостей уровня приложения (без DI-фреймворков). */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val games: GameRepository by lazy { GameRepository(context) }
    val definitions: DefinitionsRepository by lazy { DefinitionsRepository(context, appScope) }
    val sounds: SoundPlayer by lazy { SoundPlayer(settings) }

    private val dictionaryDeferred: Deferred<WordDictionary> = appScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
        context.assets.open("words.bin").use { DictionaryFormat.read(it) }
    }

    private val startWordsDeferred: Deferred<StartWordPicker> = appScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
        val words = context.assets.open("start_words.txt").bufferedReader().use { it.readLines() }
        StartWordPicker(words)
    }

    suspend fun dictionary(): WordDictionary = dictionaryDeferred.await()

    suspend fun startWords(): StartWordPicker = startWordsDeferred.await()

    suspend fun hintFinder(): HintFinder = HintFinder(dictionary())

    /** Начать загрузку словаря заранее, чтобы к первому ходу он уже был в памяти. */
    fun warmUp() {
        dictionaryDeferred.start()
        startWordsDeferred.start()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as com.kdyadin.balda.BaldaApp).container
