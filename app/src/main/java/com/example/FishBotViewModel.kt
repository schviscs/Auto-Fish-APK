package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bot.BotConfig
import com.example.bot.BotRuntimeSnapshot
import com.example.bot.FishBotEngine
import com.example.capture.NoOpFrameSource
import com.example.data.BotConfigStore
import com.example.vision.FallbackVisionAnalyzer
import com.example.vision.PythonVisionAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FishBotViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = BotConfigStore(application.applicationContext)
    private val analyzer = runCatching { PythonVisionAnalyzer(application.applicationContext) }
        .getOrElse { FallbackVisionAnalyzer() }
    private val _frameSource = MutableStateFlow<FrameSource>(NoOpFrameSource())
    private val engine = FishBotEngine(
        frameSource = _frameSource.value,
        visionAnalyzer = analyzer,
        scope = viewModelScope,
    )

    private val _config = MutableStateFlow(BotConfig())
    val config: StateFlow<BotConfig> = _config.asStateFlow()
    val runtime: StateFlow<BotRuntimeSnapshot> = engine.snapshot

    init {
        viewModelScope.launch {
            configStore.configFlow.collectLatest { loaded ->
                _config.value = loaded
                engine.updateConfig(loaded)
            }
        }
        viewModelScope.launch {
            _frameSource.collectLatest { newSource ->
                engine.updateFrameSource(newSource)
            }
        }
    }

    fun toggleBot() {
        if (runtime.value.isRunning) {
            engine.stop()
        } else {
            engine.start(config.value)
        }
    }

    fun updateConfig(transform: (BotConfig) -> BotConfig) {
        viewModelScope.launch {
            val updated = transform(config.value)
            _config.value = updated
            engine.updateConfig(updated)
            configStore.save(updated)
        }
    }

    fun stop() {
        engine.stop()
    }

    fun setFrameSource(frameSource: FrameSource) {
        _frameSource.value = frameSource
    }

    override fun onCleared() {
        engine.stop("Motor detenido al cerrar la pantalla.")
        _frameSource.value.close()
        super.onCleared()
    }
}
