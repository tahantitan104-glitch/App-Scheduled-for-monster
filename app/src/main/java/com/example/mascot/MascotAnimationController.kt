package com.example.mascot

import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusMessageItem
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotMicroAnimation
import com.example.domain.model.MascotType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MascotControllerConfig(
    val activeMascot: MascotType = MascotType.BABY_CLOUD,
    val focusMessagesEnabled: Boolean = true,
    val messageFrequency: FocusMessageFrequency = FocusMessageFrequency.EVERY_3_MIN,
    val reducedMotionEnabled: Boolean = false,
    val messages: List<FocusMessageItem> = emptyList(),
    val isPaused: Boolean = false
)

data class MascotControllerUiState(
    val activeMascot: MascotType = MascotType.BABY_CLOUD,
    val animationState: MascotAnimationState = MascotAnimationState.IDLE,
    val microAnimation: MascotMicroAnimation = MascotMicroAnimation.NONE,
    val isBlinking: Boolean = false,
    val peekProgress: Float = 1f,
    val bubbleMessage: String? = null,
    val isBubbleVisible: Boolean = false,
    val isCelebrating: Boolean = false,
    val celebrationMessage: String? = null
)

/**
 * Centralized lifecycle-aware mascot animation coordinator.
 * Completely decoupled from LockSessionEngine and timer logic so mascot animations
 * never delay lock transitions, countdown updates, or emergency recovery.
 */
class MascotAnimationController(
    private val scope: CoroutineScope,
    private val messageManager: FocusMessageManager,
    private val random: Random = Random.Default
) {
    private val _uiState = MutableStateFlow(MascotControllerUiState())
    val uiState: StateFlow<MascotControllerUiState> = _uiState.asStateFlow()

    private var config = MascotControllerConfig()
    private var lastMessageId: String? = null
    private var lastCelebrationMsg: String? = null

    private var blinkJob: Job? = null
    private var expressionJob: Job? = null
    private var messageJob: Job? = null
    private var sequenceJob: Job? = null

    fun updateConfig(newConfig: MascotControllerConfig) {
        val wasPaused = config.isPaused
        val oldMascot = config.activeMascot
        val oldReduced = config.reducedMotionEnabled
        val oldFreq = config.messageFrequency
        val oldEnabled = config.focusMessagesEnabled
        val oldMessages = config.messages

        config = newConfig
        if (_uiState.value.activeMascot != newConfig.activeMascot) {
            sequenceJob?.cancel()
            sequenceJob = null
            _uiState.update {
                it.copy(
                    activeMascot = newConfig.activeMascot,
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    peekProgress = 1f
                )
            }
        }

        val shouldRestartLoops = wasPaused != newConfig.isPaused ||
            oldMascot != newConfig.activeMascot ||
            oldReduced != newConfig.reducedMotionEnabled ||
            oldFreq != newConfig.messageFrequency ||
            oldEnabled != newConfig.focusMessagesEnabled ||
            oldMessages != newConfig.messages

        if (newConfig.isPaused) {
            pauseAllLoops()
        } else if (shouldRestartLoops || (blinkJob == null && !newConfig.reducedMotionEnabled)) {
            restartLoops()
        }
    }

    fun start() {
        if (!config.isPaused) {
            restartLoops()
        }
    }

    fun stop() {
        pauseAllLoops()
        sequenceJob?.cancel()
        sequenceJob = null
    }

    /**
     * Plays the 8-stage Signature Peek-a-Boo Entrance for RESOLVE Bunny when intentionally
     * triggered (first appearance / explicit preview). Immediately settles to IDLE when
     * reducedMotion is enabled or paused. Never blocks lock timers or exits.
     */
    fun triggerPeekEntrance(onFinished: () -> Unit = {}): Boolean {
        val current = _uiState.value
        if (config.isPaused || current.isCelebrating) return false
        if (config.activeMascot != MascotType.RESOLVE_BUNNY) return false

        sequenceJob?.cancel()
        if (config.reducedMotionEnabled) {
            _uiState.update {
                it.copy(
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    peekProgress = 1f
                )
            }
            onFinished()
            return true
        }

        sequenceJob = scope.launch {
            _uiState.update {
                it.copy(
                    animationState = MascotAnimationState.PEEK_ENTRANCE,
                    microAnimation = MascotMicroAnimation.NONE,
                    isBubbleVisible = false,
                    bubbleMessage = null,
                    peekProgress = 0f
                )
            }

            val totalMs = PEEK_ENTRANCE_DURATION_MS
            val stepMs = 16L
            var elapsed = 0L
            while (isActive && elapsed < totalMs && !config.isPaused) {
                delay(stepMs)
                elapsed += stepMs
                val progress = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                _uiState.update { it.copy(peekProgress = progress) }
            }

            _uiState.update { state ->
                if (!state.isCelebrating) {
                    state.copy(
                        animationState = MascotAnimationState.IDLE,
                        microAnimation = MascotMicroAnimation.NONE,
                        peekProgress = 1f
                    )
                } else state
            }
            onFinished()
        }
        return true
    }

    /**
     * Triggers any core animation state briefly for explicit interactive previews or contextual
     * reactions (e.g. Curious, Turning, Happy Dance, Blink).
     */
    fun triggerCoreAnimation(
        state: MascotAnimationState,
        micro: MascotMicroAnimation = MascotMicroAnimation.NONE,
        durationMs: Long = DEFAULT_REACTION_DURATION_MS
    ): Boolean {
        if (config.isPaused || _uiState.value.isCelebrating) return false
        if (state == MascotAnimationState.PEEK_ENTRANCE) {
            return triggerPeekEntrance()
        }
        sequenceJob?.cancel()
        if (config.reducedMotionEnabled) {
            _uiState.update {
                it.copy(
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    peekProgress = 1f
                )
            }
            return true
        }

        sequenceJob = scope.launch {
            val triggerBlink = state == MascotAnimationState.BLINK
            _uiState.update {
                it.copy(
                    animationState = if (triggerBlink) MascotAnimationState.IDLE else state,
                    microAnimation = micro,
                    isBlinking = triggerBlink || it.isBlinking,
                    peekProgress = 1f
                )
            }
            delay(if (triggerBlink) BLINK_HOLD_MS * 2 else durationMs)
            _uiState.update { ui ->
                if (!ui.isCelebrating) {
                    ui.copy(
                        animationState = MascotAnimationState.IDLE,
                        microAnimation = MascotMicroAnimation.NONE,
                        isBlinking = false
                    )
                } else ui
            }
        }
        return true
    }

    /**
     * Triggers the signature pointing + speech bubble sequence on demand (also invoked by the
     * periodic focus-message loop). Safe no-op if celebration is active or no valid message exists.
     */
    fun triggerPointingCueNow(customMessage: String? = null): Boolean {
        val currentUi = _uiState.value
        if (currentUi.isCelebrating || config.isPaused) return false
        val msgText = if (customMessage != null) {
            messageManager.sanitizeMessage(customMessage)
        } else {
            val item = messageManager.selectNextFocusMessage(config.messages, lastMessageId)
                ?.also { lastMessageId = it.id }
            item?.text
        } ?: return false

        sequenceJob?.cancel()
        sequenceJob = scope.launch {
            runPointingSequence(messageText = msgText)
        }
        return true
    }

    /**
     * Runs a short ~2.5s happy celebration sequence ONLY when invoked upon an actual
     * restricted session reaching completion. Never blocks lock exit or unlock.
     */
    suspend fun playSessionCompletionCelebration(onFinished: () -> Unit = {}) {
        pauseAllLoops()
        sequenceJob?.cancel()
        sequenceJob = null

        val msg = messageManager.selectCompletionMessage(lastCelebrationMsg)
        lastCelebrationMsg = msg

        _uiState.update {
            it.copy(
                activeMascot = config.activeMascot,
                animationState = MascotAnimationState.CELEBRATING,
                microAnimation = MascotMicroAnimation.NONE,
                isBlinking = false,
                peekProgress = 1f,
                isCelebrating = true,
                celebrationMessage = msg,
                bubbleMessage = msg,
                isBubbleVisible = true
            )
        }

        val durationMs = if (config.reducedMotionEnabled) {
            REDUCED_MOTION_CELEBRATION_MS
        } else {
            COMPLETION_CELEBRATION_MS
        }
        delay(durationMs)

        _uiState.update {
            it.copy(
                animationState = MascotAnimationState.IDLE,
                microAnimation = MascotMicroAnimation.NONE,
                isCelebrating = false,
                isBubbleVisible = false,
                bubbleMessage = null
            )
        }
        onFinished()
    }

    private fun pauseAllLoops() {
        blinkJob?.cancel()
        blinkJob = null
        expressionJob?.cancel()
        expressionJob = null
        messageJob?.cancel()
        messageJob = null
        sequenceJob?.cancel()
        sequenceJob = null

        if (!_uiState.value.isCelebrating) {
            _uiState.update {
                it.copy(
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    isBlinking = false,
                    peekProgress = 1f,
                    isBubbleVisible = false,
                    bubbleMessage = null
                )
            }
        }
    }

    private fun restartLoops() {
        pauseAllLoops()
        if (config.isPaused || _uiState.value.isCelebrating) return

        if (!config.reducedMotionEnabled) {
            startBlinkLoop()
            startNaturalExpressionLoop()
        }
        startFocusMessageLoop()
    }

    private fun startBlinkLoop() {
        blinkJob = scope.launch {
            while (isActive && !config.isPaused && !config.reducedMotionEnabled) {
                val pauseMs = random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS)
                delay(pauseMs)
                if (_uiState.value.isCelebrating || config.isPaused) continue

                _uiState.update { it.copy(isBlinking = true) }
                delay(BLINK_HOLD_MS)
                _uiState.update { it.copy(isBlinking = false) }

                // Occasional natural double blink
                if (random.nextFloat() < 0.25f) {
                    delay(140L)
                    _uiState.update { it.copy(isBlinking = true) }
                    delay(BLINK_HOLD_MS)
                    _uiState.update { it.copy(isBlinking = false) }
                }
            }
        }
    }

    private fun startNaturalExpressionLoop() {
        expressionJob = scope.launch {
            while (isActive && !config.isPaused && !config.reducedMotionEnabled) {
                val waitMs = random.nextLong(EXPRESSION_MIN_GAP_MS, EXPRESSION_MAX_GAP_MS)
                delay(waitMs)
                val current = _uiState.value
                if (current.isCelebrating ||
                    current.animationState != MascotAnimationState.IDLE ||
                    config.isPaused
                ) {
                    continue
                }

                // Cycle gently among organic idle reactions without disrupting focus
                val nextPose = if (config.activeMascot == MascotType.BABY_CLOUD) {
                    when (random.nextInt(5)) {
                        0 -> MascotAnimationState.GENTLE_FLOAT to MascotMicroAnimation.NONE
                        1 -> MascotAnimationState.CURIOUS_SWAY to MascotMicroAnimation.NONE
                        2 -> MascotAnimationState.GENTLE_DRIZZLE to MascotMicroAnimation.NONE
                        3 -> MascotAnimationState.CURIOUS to MascotMicroAnimation.NONE
                        else -> MascotAnimationState.HEAD_EAR_ATTENTION to MascotMicroAnimation.NONE
                    }
                } else {
                    when (random.nextInt(5)) {
                        0 -> MascotAnimationState.HEAD_EAR_ATTENTION to MascotMicroAnimation.EAR_WIGGLE
                        1 -> MascotAnimationState.CURIOUS to MascotMicroAnimation.NONE
                        2 -> MascotAnimationState.TURNING to MascotMicroAnimation.NONE
                        3 -> MascotAnimationState.IDLE to MascotMicroAnimation.GENTLE_WAVE
                        else -> MascotAnimationState.IDLE to MascotMicroAnimation.EAR_WIGGLE
                    }
                }

                _uiState.update {
                    it.copy(
                        animationState = nextPose.first,
                        microAnimation = nextPose.second
                    )
                }
                delay(EXPRESSION_DURATION_MS)
                _uiState.update { state ->
                    if (state.animationState == nextPose.first &&
                        state.microAnimation == nextPose.second &&
                        !state.isCelebrating
                    ) {
                        state.copy(
                            animationState = MascotAnimationState.IDLE,
                            microAnimation = MascotMicroAnimation.NONE
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun startFocusMessageLoop() {
        val intervalMin = config.messageFrequency.intervalMinutes
        if (!config.focusMessagesEnabled || intervalMin == null || config.messages.isEmpty()) {
            return
        }
        val intervalMs = intervalMin * 60_000L
        messageJob = scope.launch {
            val initialDelayMs = minOf(intervalMs, INITIAL_FIRST_CUE_CAP_MS)
            delay(initialDelayMs)
            while (isActive && !config.isPaused && config.focusMessagesEnabled) {
                val chosen = messageManager.selectNextFocusMessage(config.messages, lastMessageId)
                if (chosen != null && !_uiState.value.isCelebrating) {
                    lastMessageId = chosen.id
                    runPointingSequence(messageText = chosen.text)
                }
                delay(intervalMs)
            }
        }
    }

    private suspend fun runPointingSequence(messageText: String) {
        if (config.isPaused || _uiState.value.isCelebrating) return

        if (config.reducedMotionEnabled) {
            _uiState.update {
                it.copy(
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    bubbleMessage = messageText,
                    isBubbleVisible = true
                )
            }
            delay(MESSAGE_VISIBLE_DURATION_MS)
            _uiState.update { state ->
                if (!state.isCelebrating) {
                    state.copy(isBubbleVisible = false, bubbleMessage = null)
                } else state
            }
            return
        }

        // Step 1: gentle attentive head & ear cue
        _uiState.update {
            it.copy(
                animationState = MascotAnimationState.HEAD_EAR_ATTENTION,
                microAnimation = MascotMicroAnimation.EAR_WIGGLE
            )
        }
        delay(PRE_POINT_ATTENTION_MS)
        if (config.isPaused || _uiState.value.isCelebrating) return

        // Step 2: signature pointing pose toward bubble
        _uiState.update {
            it.copy(
                animationState = MascotAnimationState.POINTING,
                microAnimation = MascotMicroAnimation.NONE
            )
        }
        delay(PAW_RAISE_LEAD_MS)
        if (config.isPaused || _uiState.value.isCelebrating) return

        // Step 3: show encouragement speech bubble above mascot
        _uiState.update {
            it.copy(
                bubbleMessage = messageText,
                isBubbleVisible = true
            )
        }
        delay(MESSAGE_VISIBLE_DURATION_MS)

        // Step 4: gently fade bubble and settle back to IDLE
        _uiState.update { state ->
            if (!state.isCelebrating) {
                state.copy(isBubbleVisible = false)
            } else state
        }
        delay(BUBBLE_FADE_OUT_SETTLE_MS)

        _uiState.update { state ->
            if (!state.isCelebrating) {
                state.copy(
                    animationState = MascotAnimationState.IDLE,
                    microAnimation = MascotMicroAnimation.NONE,
                    bubbleMessage = null
                )
            } else state
        }
    }

    companion object {
        private const val BLINK_MIN_GAP_MS = 3_200L
        private const val BLINK_MAX_GAP_MS = 5_800L
        private const val BLINK_HOLD_MS = 160L
        private const val EXPRESSION_MIN_GAP_MS = 12_000L
        private const val EXPRESSION_MAX_GAP_MS = 20_000L
        private const val EXPRESSION_DURATION_MS = 1_300L
        private const val INITIAL_FIRST_CUE_CAP_MS = 22_000L
        private const val PRE_POINT_ATTENTION_MS = 450L
        private const val PAW_RAISE_LEAD_MS = 280L
        private const val MESSAGE_VISIBLE_DURATION_MS = 4_500L
        private const val BUBBLE_FADE_OUT_SETTLE_MS = 380L
        private const val COMPLETION_CELEBRATION_MS = 2_500L
        private const val REDUCED_MOTION_CELEBRATION_MS = 1_400L
        const val PEEK_ENTRANCE_DURATION_MS = 2_800L
        private const val DEFAULT_REACTION_DURATION_MS = 1_800L
    }
}
