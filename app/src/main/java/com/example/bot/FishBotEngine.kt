package com.example.bot

import com.example.BotAccessibilityService
import com.example.capture.FrameSource
import com.example.capture.NoOpFrameSource
import com.example.vision.VisionAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class FishBotEngine(
    private var frameSource: FrameSource = NoOpFrameSource(),
    private val visionAnalyzer: VisionAnalyzer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _snapshot = MutableStateFlow(BotRuntimeSnapshot())
    val snapshot: StateFlow<BotRuntimeSnapshot> = _snapshot.asStateFlow()

    private var engineJob: Job? = null
    private var config: BotConfig = BotConfig()
    private val lastActionAt = AtomicLong(0L)

    fun start(initialConfig: BotConfig = config) {
        config = initialConfig
        if (engineJob?.isActive == true) return
        appendLog("Motor iniciado con visión local Python=${config.enablePythonVision}.", BotLogLevel.SUCCESS)
        engineJob = scope.launch {
            _snapshot.value = _snapshot.value.copy(isRunning = true, state = BotState.FINDING_FISHING_UI)
            runLoop()
        }
    }

    fun stop(reason: String = "Bot detenido por el usuario.") {
        engineJob?.cancel()
        engineJob = null
        _snapshot.value = _snapshot.value.copy(isRunning = false, state = BotState.IDLE, lastAction = "none")
        appendLog(reason, BotLogLevel.INFO)
    }

    fun updateConfig(newConfig: BotConfig) {
        config = newConfig
        appendLog("Configuración actualizada: tolerancia=${newConfig.colorTolerance}, umbral=${newConfig.biteThreshold}.", BotLogLevel.DEBUG)
    }

    fun updateFrameSource(newFrameSource: FrameSource) {
        if (newFrameSource == frameSource) return
        frameSource.close()
        frameSource = newFrameSource
        appendLog("Fuente de frames actualizada a ${newFrameSource::class.simpleName}.", BotLogLevel.DEBUG)
    }

    private suspend fun runLoop() {
        while (engineJob?.isActive == true) {
            val frame = runCatching { frameSource.capture() }
                .onFailure { appendLog("No se pudo capturar frame: ${it.message}", BotLogLevel.WARNING) }
                .getOrNull()

            if (frame == null) {
                _snapshot.value = _snapshot.value.copy(state = BotState.WAITING_FRAME)
                appendDebug("Esperando frames reales. Conecta MediaProjection o Accessibility screenshots.")
                delay(config.idleFrameDelayMs)
                continue
            }

            val vision = runCatching { visionAnalyzer.analyze(frame, config) }
                .onFailure { appendLog("Error en visión local: ${it.message}", BotLogLevel.ERROR) }
                .getOrElse {
                    VisionResult(
                        state = BotState.RECOVERING,
                        confidence = 0.0,
                        action = BotAction.Wait(500L, "vision_error"),
                        debug = mapOf("error" to (it.message ?: "unknown"))
                    )
                }

            _snapshot.value = _snapshot.value.copy(
                state = vision.state,
                lastConfidence = vision.confidence,
                lastVisionScore = vision.debug["bite_score"]?.toDoubleOrNull() ?: _snapshot.value.lastVisionScore,
                lastAction = describeAction(vision.action),
            )

            maybeExecuteAction(vision.action)
            delay(nextDelay(vision))
        }
    }

    private suspend fun maybeExecuteAction(action: BotAction) {
        val now = System.currentTimeMillis()
        if (now - lastActionAt.get() < config.actionCooldownMs) return
        when (action) {
            is BotAction.None -> return
            is BotAction.Wait -> {
                delay(action.durationMs)
                return
            }
            is BotAction.Tap,
            is BotAction.Swipe -> {
                lastActionAt.set(now)
                withContext(Dispatchers.Main) {
                    BotAccessibilityService.submit(GestureCommand(action)) { result ->
                        if (result.success) {
                            appendLog("Acción ejecutada: ${describeAction(action)}", BotLogLevel.SUCCESS)
                            if (action is BotAction.Tap && action.reason.contains("hook", ignoreCase = true)) {
                                incrementFishCounter()
                            }
                        } else {
                            appendLog("No se pudo ejecutar gesto: ${result.message}", BotLogLevel.WARNING)
                        }
                    }
                }
            }
        }
    }

    private fun nextDelay(vision: VisionResult): Long = when (vision.state) {
        BotState.WAITING_BITE,
        BotState.BITE_DETECTED,
        BotState.MINIGAME_TRACKING -> config.activeFrameDelayMs
        else -> config.idleFrameDelayMs
    }

    private fun appendDebug(message: String) {
        if (config.enableDebugLogs) appendLog(message, BotLogLevel.DEBUG)
    }

    private fun appendLog(message: String, level: BotLogLevel) {
        val current = _snapshot.value
        val newLogs = (listOf(BotLogEntry(message = message, level = level)) + current.logs).take(12)
        _snapshot.value = current.copy(logs = newLogs)
    }

    private fun incrementFishCounter() {
        val current = _snapshot.value
        _snapshot.value = current.copy(fishCaught = current.fishCaught + 1)
    }

    private fun describeAction(action: BotAction): String = when (action) {
        is BotAction.Tap -> "tap(${action.xNorm}, ${action.yNorm}) ${action.reason}"
        is BotAction.Swipe -> "swipe ${action.reason}"
        is BotAction.Wait -> "wait ${action.durationMs}ms ${action.reason}"
        is BotAction.None -> "none ${action.reason}"
    }
}
