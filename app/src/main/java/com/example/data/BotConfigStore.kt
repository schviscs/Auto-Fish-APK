package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bot.BotConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.botPreferencesDataStore by preferencesDataStore(name = "bot_config")

class BotConfigStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val configFlow: Flow<BotConfig> = context.botPreferencesDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            val raw = preferences[CONFIG_KEY]
            if (raw.isNullOrBlank()) {
                BotConfig()
            } else {
                runCatching { json.decodeFromString<BotConfig>(raw) }.getOrElse { BotConfig() }
            }
        }

    suspend fun save(config: BotConfig) {
        context.botPreferencesDataStore.edit { preferences ->
            preferences[CONFIG_KEY] = json.encodeToString(config)
        }
    }

    suspend fun update(transform: (BotConfig) -> BotConfig) {
        context.botPreferencesDataStore.edit { preferences ->
            val current = preferences[CONFIG_KEY]
                ?.let { raw -> runCatching { json.decodeFromString<BotConfig>(raw) }.getOrNull() }
                ?: BotConfig()
            preferences[CONFIG_KEY] = json.encodeToString(transform(current))
        }
    }

    companion object {
        private val CONFIG_KEY = stringPreferencesKey("config_json")
    }
}
