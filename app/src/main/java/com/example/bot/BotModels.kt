package com.example.bot

import kotlinx.serialization.Serializable

@Serializable
data class BotConfig(
    val targetPackageName: String = "com.neverness.everness",
    val biteColorRgb: List<Int> = listOf(80, 180, 255),
    val colorTolerance: Int = 42,
    val biteThreshold: Double = 0.015,
    val idleFrameDelayMs: Long = 650L,
    val activeFrameDelayMs: Long = 140L,
    val actionCooldownMs: Long = 420L,
    val castXNorm: Float = 0.50f,
    val castYNorm: Float = 0.82f,
    val hookXNorm: Float = 0.50f,
    val hookYNorm: Float = 0.82f,
    val restockEveryFish: Int = 95,
    val sellEveryFish: Int = 50,
    val enablePythonVision: Boolean = true,
    val enableDebugLogs: Boolean = true,
)

@Serializable
data class BotRuntimeSnapshot(
    val isRunning: Boolean = false,
    val state: BotState = BotState.IDLE,
    val fishCaught: Int = 0,
    val lastConfidence: Double = 0.0,
    val lastVisionScore: Double = 0.0,
    val lastAction: String = "none",
    val logs: List<BotLogEntry> = listOf(
        BotLogEntry(message = "Sistema inicializado. Activa accesibilidad y calibración para empezar.")
    ),
)

@Serializable
data class BotLogEntry(
    val message: String,
    val level: BotLogLevel = BotLogLevel.INFO,
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Serializable
enum class BotLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
}

@Serializable
enum class BotState {
    IDLE,
    WAITING_FRAME,
    FINDING_FISHING_UI,
    CASTING,
    WAITING_BITE,
    BITE_DETECTED,
    HOOKING,
    MINIGAME_TRACKING,
    RECOVERING,
    RESTOCKING,
    SELLING,
    PAUSED,
    ERROR,
}

@Serializable
data class VisionResult(
    val state: BotState = BotState.WAITING_FRAME,
    val confidence: Double = 0.0,
    val action: BotAction = BotAction.Wait(),
    val debug: Map<String, String> = emptyMap(),
)

@Serializable
sealed class BotAction {
    @Serializable
    data class Tap(
        val xNorm: Float,
        val yNorm: Float,
        val durationMs: Long = 80L,
        val reason: String = "tap",
    ) : BotAction()

    @Serializable
    data class Swipe(
        val startXNorm: Float,
        val startYNorm: Float,
        val endXNorm: Float,
        val endYNorm: Float,
        val durationMs: Long = 280L,
        val reason: String = "swipe",
    ) : BotAction()

    @Serializable
    data class Wait(
        val durationMs: Long = 120L,
        val reason: String = "wait",
    ) : BotAction()

    @Serializable
    data class None(
        val reason: String = "none",
    ) : BotAction()
}

data class GestureCommand(
    val action: BotAction,
    val requestedAtMillis: Long = System.currentTimeMillis(),
)

data class GestureResult(
    val command: GestureCommand,
    val success: Boolean,
    val message: String,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

data class FrameSnapshot(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameSnapshot
        if (!bytes.contentEquals(other.bytes)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (rotationDegrees != other.rotationDegrees) return false
        return capturedAtMillis == other.capturedAtMillis
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rotationDegrees
        result = 31 * result + capturedAtMillis.hashCode()
        return result
    }
}
