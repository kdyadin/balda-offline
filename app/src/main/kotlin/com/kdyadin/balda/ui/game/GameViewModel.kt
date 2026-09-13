package com.kdyadin.balda.ui.game

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kdyadin.balda.core.Board
import com.kdyadin.balda.core.Cell
import com.kdyadin.balda.core.GameEngine
import com.kdyadin.balda.core.GameSettings
import com.kdyadin.balda.core.GameState
import com.kdyadin.balda.core.Hint
import com.kdyadin.balda.core.MoveError
import com.kdyadin.balda.core.MoveProposal
import com.kdyadin.balda.core.MoveResult
import com.kdyadin.balda.core.MoveValidator
import com.kdyadin.balda.core.StartWordPicker
import com.kdyadin.balda.core.dictionary.WordDictionary
import com.kdyadin.balda.data.AppContainer
import com.kdyadin.balda.data.SavedGame
import com.kdyadin.balda.data.SoundPlayer
import com.kdyadin.balda.data.appContainer
import com.kdyadin.balda.ui.toMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GamePhase { LOADING, NO_GAME, HANDOFF, PLAYING, FINISHED }

/** Однократные события для тактильного/звукового отклика. */
enum class GameEvent { LETTER_PLACED, MOVE_CONFIRMED, ERROR, TIMER_EXPIRED }

data class GameUiState(
    val game: GameState? = null,
    val phase: GamePhase = GamePhase.LOADING,
    /** Пустая клетка, выбранная для новой буквы (ждём нажатия на клавиатуре). */
    val selectedCell: Cell? = null,
    val pendingCell: Cell? = null,
    val pendingLetter: Char? = null,
    val path: List<Cell> = emptyList(),
    /** Ошибка проверки текущего слова (обновляется на лету), null — слово допустимо. */
    val liveError: MoveError? = null,
    /** Сообщение пользователю (ошибка ввода и т. п.). [messageId] меняется при каждом новом сообщении. */
    val message: String? = null,
    val messageId: Long = 0,
    val remainingSeconds: Int? = null,
    val hintSearching: Boolean = false,
    val dictionaryReady: Boolean = false,
) {
    /** Поле с учётом ещё не подтверждённой буквы. */
    val boardWithPending: Board?
        get() {
            val board = game?.board ?: return null
            val cell = pendingCell ?: return board
            val letter = pendingLetter ?: return board
            return board.with(cell, letter)
        }

    val currentWord: String
        get() = boardWithPending?.takeIf { path.isNotEmpty() }?.wordAlong(path)?.uppercase() ?: ""

    val hasPendingLetter: Boolean get() = pendingCell != null && pendingLetter != null

    val canConfirm: Boolean get() = hasPendingLetter && path.size >= 2 && liveError == null && dictionaryReady

    val hintsLeft: Int?
        get() {
            val g = game ?: return null
            val limit = g.settings.hintLimit
            return if (limit == GameSettings.UNLIMITED_HINTS) null else (limit - g.current.hintsUsed).coerceAtLeast(0)
        }

    val canUseHint: Boolean
        get() {
            val g = game ?: return false
            return phase == GamePhase.PLAYING && !hintSearching && g.activeHint == null && g.settings.hintsAllowed(g.current.hintsUsed)
        }
}

class GameViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var dictionary: WordDictionary? = null
    private var startWords: StartWordPicker? = null
    private var turnDeadline: Long? = null
    private var timerJob: Job? = null
    private var messageCounter = 0L

    init {
        viewModelScope.launch {
            val saved = container.games.load()
            dictionary = container.dictionary()
            startWords = container.startWords()
            restore(saved)
        }
    }

    // ---------- запуск и восстановление ----------

    private fun restore(saved: SavedGame?) {
        if (saved == null) {
            _state.update { it.copy(phase = GamePhase.NO_GAME, dictionaryReady = true) }
            return
        }
        var game = saved.state
        var phase = when {
            game.isFinished -> GamePhase.FINISHED
            saved.handoffPending -> GamePhase.HANDOFF
            else -> GamePhase.PLAYING
        }
        turnDeadline = saved.turnDeadlineMillis
        // Таймер истёк, пока приложение было закрыто — пас.
        if (phase == GamePhase.PLAYING && turnDeadline != null && turnDeadline!! <= System.currentTimeMillis()) {
            game = GameEngine.timerExpired(game)
            turnDeadline = null
            phase = if (game.isFinished) GamePhase.FINISHED else if (game.settings.handoffScreen) GamePhase.HANDOFF else GamePhase.PLAYING
            if (phase == GamePhase.PLAYING) startTurnTimer(game)
        }
        _state.update {
            it.copy(
                game = game,
                phase = phase,
                pendingCell = saved.pendingCell,
                pendingLetter = saved.pendingLetter,
                path = saved.pendingPath,
                dictionaryReady = true,
            )
        }
        revalidate()
        if (phase == GamePhase.PLAYING) ensureTimer(game)
        persist()
    }

    fun startNewGame(settings: GameSettings) {
        timerJob?.cancel()
        val game = GameEngine.newGame(settings)
        turnDeadline = null
        _state.update {
            GameUiState(game = game, phase = GamePhase.PLAYING, dictionaryReady = it.dictionaryReady)
        }
        startTurnTimer(game)
        persist()
    }

    /** Новая партия с теми же настройками: новое стартовое слово, первым ходит другой игрок. */
    fun playAgain() {
        val previous = _state.value.game ?: return
        val word = startWords?.pick(previous.settings.boardSize, exclude = previous.settings.startWord)
            ?: previous.settings.startWord
        startNewGame(previous.settings.copy(startWord = word, firstPlayer = 1 - previous.settings.firstPlayer))
    }

    fun abandonGame() {
        timerJob?.cancel()
        turnDeadline = null
        _state.update { GameUiState(phase = GamePhase.NO_GAME, dictionaryReady = it.dictionaryReady) }
        viewModelScope.launch { container.games.clear() }
    }

    // ---------- ввод хода ----------

    fun onCellTap(cell: Cell) {
        val s = _state.value
        val game = s.game ?: return
        if (s.phase != GamePhase.PLAYING) return
        val boardBefore = game.board
        if (boardBefore.isEmpty(cell) && cell != s.pendingCell) {
            if (s.path.isNotEmpty()) {
                showError(MoveError.EmptyCellInPath)
                return
            }
            // выбор (или перенос) клетки для новой буквы
            _state.update { it.copy(selectedCell = cell, pendingCell = null, pendingLetter = null, path = emptyList(), liveError = null) }
            persist()
            return
        }
        val board = s.boardWithPending ?: return
        if (!s.hasPendingLetter) {
            showError(MoveError.NoLetterPlaced)
            return
        }
        if (s.path.lastOrNull() == cell) {
            // повторный тап по последней клетке — убрать её из слова
            setPath(s.path.dropLast(1))
            return
        }
        val err = MoveValidator.checkPathStep(board, s.path, cell)
        if (err != null) {
            showError(err)
            return
        }
        setPath(s.path + cell)
    }

    fun onCellDrag(cell: Cell) {
        val s = _state.value
        if (s.phase != GamePhase.PLAYING || !s.hasPendingLetter) return
        val board = s.boardWithPending ?: return
        if (s.path.lastOrNull() == cell) return
        // Возврат на предпоследнюю клетку — откат шага (как в «змейке»).
        if (s.path.size >= 2 && s.path[s.path.size - 2] == cell) {
            setPath(s.path.dropLast(1))
            return
        }
        val err = MoveValidator.checkPathStep(board, s.path, cell)
        if (err != null) {
            if (err is MoveError.Diagonal) showError(err)
            return
        }
        setPath(s.path + cell)
    }

    fun onLetter(letter: Char) {
        val s = _state.value
        if (s.phase != GamePhase.PLAYING) return
        val cell = s.selectedCell ?: s.pendingCell ?: run {
            showMessage("Сначала выберите пустую клетку")
            return
        }
        _state.update {
            it.copy(selectedCell = null, pendingCell = cell, pendingLetter = letter.lowercaseChar(), path = emptyList(), liveError = null)
        }
        _events.tryEmit(GameEvent.LETTER_PLACED)
        container.sounds.play(SoundPlayer.Sound.LETTER)
        persist()
    }

    fun clearInput() {
        _state.update { it.copy(selectedCell = null, pendingCell = null, pendingLetter = null, path = emptyList(), liveError = null) }
        persist()
    }

    fun confirmMove() {
        val s = _state.value
        val game = s.game ?: return
        val dict = dictionary ?: return
        if (s.phase != GamePhase.PLAYING) return
        val cell = s.pendingCell
        val letter = s.pendingLetter
        if (cell == null || letter == null) {
            showError(MoveError.NoLetterPlaced)
            return
        }
        when (val result = GameEngine.applyMove(game, MoveProposal(cell, letter, s.path), dict)) {
            is MoveResult.Rejected -> {
                showError(result.error)
                _state.update { it.copy(selectedCell = null, pendingCell = null, pendingLetter = null, path = emptyList(), liveError = null) }
                persist()
            }
            is MoveResult.Accepted -> {
                _events.tryEmit(GameEvent.MOVE_CONFIRMED)
                container.sounds.play(SoundPlayer.Sound.CONFIRM)
                finishTurn(result.state)
            }
        }
    }

    fun pass() {
        val game = _state.value.game ?: return
        if (_state.value.phase != GamePhase.PLAYING) return
        finishTurn(GameEngine.pass(game))
    }

    fun surrender() {
        val game = _state.value.game ?: return
        if (_state.value.phase != GamePhase.PLAYING) return
        finishTurn(GameEngine.surrender(game))
    }

    fun requestHint() {
        val s = _state.value
        val game = s.game ?: return
        if (!s.canUseHint) return
        _state.update { it.copy(hintSearching = true) }
        viewModelScope.launch {
            val hint: Hint? = withContext(Dispatchers.Default) { container.hintFinder().findHint(game) }
            val current = _state.value
            if (current.game !== game || current.phase != GamePhase.PLAYING) {
                _state.update { it.copy(hintSearching = false) }
                return@launch
            }
            if (hint == null) {
                _state.update { it.copy(hintSearching = false) }
                showMessage("Подсказок нет: на этом поле не найдено ни одного слова. Можно пропустить ход")
                return@launch
            }
            val updated = GameEngine.useHint(game, hint)
            _state.update { it.copy(game = updated, hintSearching = false) }
            persist()
        }
    }

    fun handoffReady() {
        val game = _state.value.game ?: return
        if (_state.value.phase != GamePhase.HANDOFF) return
        _state.update { it.copy(phase = GamePhase.PLAYING) }
        startTurnTimer(game)
        persist()
    }

    fun consumeMessage(id: Long) {
        _state.update { if (it.messageId == id) it.copy(message = null) else it }
    }

    // ---------- внутреннее ----------

    private fun finishTurn(next: GameState) {
        timerJob?.cancel()
        turnDeadline = null
        val phase = when {
            next.isFinished -> GamePhase.FINISHED
            next.settings.handoffScreen -> GamePhase.HANDOFF
            else -> GamePhase.PLAYING
        }
        _state.update {
            it.copy(
                game = next,
                phase = phase,
                selectedCell = null,
                pendingCell = null,
                pendingLetter = null,
                path = emptyList(),
                liveError = null,
                remainingSeconds = null,
                hintSearching = false,
            )
        }
        if (phase == GamePhase.PLAYING) startTurnTimer(next)
        persist()
    }

    private fun setPath(path: List<Cell>) {
        _state.update { it.copy(path = path) }
        revalidate()
        persist()
    }

    private fun revalidate() {
        val s = _state.value
        val game = s.game
        val dict = dictionary
        val cell = s.pendingCell
        val letter = s.pendingLetter
        val error = if (game != null && dict != null && cell != null && letter != null && s.path.size >= 2) {
            MoveValidator.validate(game, dict, MoveProposal(cell, letter, s.path))
        } else null
        _state.update { it.copy(liveError = error) }
    }

    private fun showError(error: MoveError) {
        _events.tryEmit(GameEvent.ERROR)
        container.sounds.play(SoundPlayer.Sound.ERROR)
        showMessage(error.toMessage())
    }

    private fun showMessage(text: String) {
        messageCounter++
        _state.update { it.copy(message = text, messageId = messageCounter) }
    }

    private fun startTurnTimer(game: GameState) {
        val seconds = game.settings.timerSeconds
        if (seconds <= 0) {
            turnDeadline = null
            _state.update { it.copy(remainingSeconds = null) }
            return
        }
        turnDeadline = System.currentTimeMillis() + seconds * 1000L
        ensureTimer(game)
    }

    private fun ensureTimer(game: GameState) {
        timerJob?.cancel()
        val deadline = turnDeadline ?: return
        if (game.settings.timerSeconds <= 0) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) {
                    onTimerExpired()
                    return@launch
                }
                _state.update { it.copy(remainingSeconds = ((left + 999) / 1000).toInt()) }
                delay(250)
            }
        }
    }

    private fun onTimerExpired() {
        val game = _state.value.game ?: return
        if (_state.value.phase != GamePhase.PLAYING || game.isFinished) return
        _events.tryEmit(GameEvent.TIMER_EXPIRED)
        container.sounds.play(SoundPlayer.Sound.TIMER_END)
        showMessage("Время вышло — ход засчитан как пас")
        finishTurn(GameEngine.timerExpired(game))
    }

    private fun persist() {
        val s = _state.value
        val game = s.game ?: return
        val saved = SavedGame(
            state = game,
            handoffPending = s.phase == GamePhase.HANDOFF,
            turnDeadlineMillis = if (s.phase == GamePhase.PLAYING) turnDeadline else null,
            pendingCell = s.pendingCell,
            pendingLetter = s.pendingLetter,
            pendingPath = s.path,
        )
        viewModelScope.launch { container.games.save(saved) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(app.appContainer) }
        }
    }
}
