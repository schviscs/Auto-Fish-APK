package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.bot.BotAction
import com.example.bot.GestureCommand
import com.example.bot.GestureResult
import java.util.ArrayDeque

class BotAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingGestures = ArrayDeque<Pair<GestureCommand, (GestureResult) -> Unit>>()
    private var isDispatchingGesture = false

    companion object {
        private const val TAG = "BotAccessibilityService"

        var instance: BotAccessibilityService? = null
            private set

        val isReady: Boolean
            get() = instance != null

        fun submit(command: GestureCommand, onResult: (GestureResult) -> Unit = {}) {
            val service = instance
            if (service == null) {
                onResult(
                    GestureResult(
                        command = command,
                        success = false,
                        message = "El servicio de accesibilidad no está activo."
                    )
                )
                return
            }
            service.enqueue(command, onResult)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != null) {
            Log.v(TAG, "event=${event.eventType} package=${event.packageName}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
        pendingGestures.clear()
        isDispatchingGesture = false
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pendingGestures.clear()
        isDispatchingGesture = false
        instance = null
        return super.onUnbind(intent)
    }

    private fun enqueue(command: GestureCommand, onResult: (GestureResult) -> Unit) {
        mainHandler.post {
            pendingGestures.add(command to onResult)
            drainQueue()
        }
    }

    private fun drainQueue() {
        if (isDispatchingGesture || pendingGestures.isEmpty()) return
        val (command, callback) = pendingGestures.removeFirst()
        val action = command.action
        if (action is BotAction.Wait || action is BotAction.None) {
            callback(GestureResult(command, true, "No se requiere gesto físico."))
            mainHandler.postDelayed({ drainQueue() }, waitDuration(action))
            return
        }

        val gesture = buildGesture(action)
        if (gesture == null) {
            callback(GestureResult(command, false, "Acción no válida para construir GestureDescription."))
            drainQueue()
            return
        }

        isDispatchingGesture = true
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    isDispatchingGesture = false
                    callback(GestureResult(command, true, "Gesto completado."))
                    drainQueue()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    isDispatchingGesture = false
                    callback(GestureResult(command, false, "Gesto cancelado por el sistema."))
                    drainQueue()
                }
            },
            mainHandler
        )

        if (!accepted) {
            isDispatchingGesture = false
            callback(GestureResult(command, false, "Android rechazó el gesto."))
            drainQueue()
        }
    }

    private fun buildGesture(action: BotAction): GestureDescription? {
        val metrics = currentDisplayMetrics()
        val builder = GestureDescription.Builder()
        return when (action) {
            is BotAction.Tap -> {
                val path = Path().apply {
                    moveTo(action.xNorm.coerceIn(0f, 1f) * metrics.widthPixels, action.yNorm.coerceIn(0f, 1f) * metrics.heightPixels)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0L, action.durationMs.coerceAtLeast(40L)))
                builder.build()
            }
            is BotAction.Swipe -> {
                val path = Path().apply {
                    moveTo(action.startXNorm.coerceIn(0f, 1f) * metrics.widthPixels, action.startYNorm.coerceIn(0f, 1f) * metrics.heightPixels)
                    lineTo(action.endXNorm.coerceIn(0f, 1f) * metrics.widthPixels, action.endYNorm.coerceIn(0f, 1f) * metrics.heightPixels)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0L, action.durationMs.coerceAtLeast(80L)))
                builder.build()
            }
            is BotAction.None,
            is BotAction.Wait -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun waitDuration(action: BotAction): Long = when (action) {
        is BotAction.Wait -> action.durationMs
        else -> 0L
    }
}
