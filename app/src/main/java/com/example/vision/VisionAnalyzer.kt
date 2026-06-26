package com.example.vision

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.bot.BotAction
import com.example.bot.BotConfig
import com.example.bot.BotState
import com.example.bot.FrameSnapshot
import com.example.bot.VisionResult

interface VisionAnalyzer {
    suspend fun analyze(frame: FrameSnapshot, config: BotConfig): VisionResult
}

class PythonVisionAnalyzer(private val context: Context) : VisionAnalyzer {
    private val python: Python by lazy {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        Python.getInstance()
    }

    override suspend fun analyze(frame: FrameSnapshot, config: BotConfig): VisionResult {
        if (!config.enablePythonVision) {
            return VisionResult(
                state = BotState.WAITING_BITE,
                confidence = 0.0,
                action = BotAction.Wait(config.activeFrameDelayMs, "python_disabled"),
                debug = mapOf("source" to "kotlin_fallback")
            )
        }

        val module = python.getModule("vision_engine")
        val result = module.callAttr(
            "analyze_rgba",
            frame.bytes,
            frame.width,
            frame.height,
            config.toPythonMap()
        )
        return result.toVisionResult()
    }
}

class FallbackVisionAnalyzer : VisionAnalyzer {
    override suspend fun analyze(frame: FrameSnapshot, config: BotConfig): VisionResult {
        return VisionResult(
            state = BotState.WAITING_BITE,
            confidence = 0.0,
            action = BotAction.Wait(config.activeFrameDelayMs, "fallback_wait"),
            debug = mapOf("source" to "fallback")
        )
    }
}

private fun BotConfig.toPythonMap(): Map<String, Any> = mapOf(
    "bite_color_rgb" to biteColorRgb,
    "color_tolerance" to colorTolerance,
    "bite_threshold" to biteThreshold,
    "hook_x_norm" to hookXNorm,
    "hook_y_norm" to hookYNorm,
    "cast_x_norm" to castXNorm,
    "cast_y_norm" to castYNorm,
)

private fun PyObject.toVisionResult(): VisionResult {
    val state = this.get("state")?.toString()?.toBotState() ?: BotState.WAITING_FRAME
    val confidence = this.get("confidence")?.toDoubleOrNullSafe() ?: 0.0
    val actionObject = this.get("action")
    val debugObject = this.get("debug")
    return VisionResult(
        state = state,
        confidence = confidence,
        action = actionObject.toBotAction(),
        debug = debugObject.toStringMap(),
    )
}

private fun PyObject?.toBotAction(): BotAction {
    if (this == null) return BotAction.None("missing_action")
    return when (val type = this.get("type")?.toString()?.lowercase()) {
        "tap" -> BotAction.Tap(
            xNorm = this.get("x_norm")?.toFloatOrNullSafe() ?: 0.5f,
            yNorm = this.get("y_norm")?.toFloatOrNullSafe() ?: 0.5f,
            durationMs = this.get("duration_ms")?.toLongOrNullSafe() ?: 80L,
            reason = this.get("reason")?.toString() ?: "python_tap"
        )
        "swipe" -> BotAction.Swipe(
            startXNorm = this.get("start_x_norm")?.toFloatOrNullSafe() ?: 0.5f,
            startYNorm = this.get("start_y_norm")?.toFloatOrNullSafe() ?: 0.5f,
            endXNorm = this.get("end_x_norm")?.toFloatOrNullSafe() ?: 0.5f,
            endYNorm = this.get("end_y_norm")?.toFloatOrNullSafe() ?: 0.5f,
            durationMs = this.get("duration_ms")?.toLongOrNullSafe() ?: 280L,
            reason = this.get("reason")?.toString() ?: "python_swipe"
        )
        "wait" -> BotAction.Wait(
            durationMs = this.get("duration_ms")?.toLongOrNullSafe() ?: 120L,
            reason = this.get("reason")?.toString() ?: "python_wait"
        )
        else -> BotAction.None(type ?: "unknown_action")
    }
}

private fun PyObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return runCatching {
        val map = mutableMapOf<String, String>()
        val keys = callAttr("keys").asList()
        keys.forEach { key ->
            map[key.toString()] = get(key.toString())?.toString() ?: ""
        }
        map.toMap()
    }.getOrElse { emptyMap() }
}

private fun String.toBotState(): BotState = runCatching { BotState.valueOf(this) }.getOrDefault(BotState.WAITING_FRAME)
private fun PyObject.toDoubleOrNullSafe(): Double? = runCatching { toDouble() }.getOrNull() ?: toString().toDoubleOrNull()
private fun PyObject.toFloatOrNullSafe(): Float? = runCatching { toFloat() }.getOrNull() ?: toString().toFloatOrNull()
private fun PyObject.toLongOrNullSafe(): Long? = runCatching { toLong() }.getOrNull() ?: toString().toLongOrNull()
