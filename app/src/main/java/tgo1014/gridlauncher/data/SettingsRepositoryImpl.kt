package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import tgo1014.gridlauncher.domain.SettingsRepository
import tgo1014.gridlauncher.domain.models.TileSettings
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val json: Json,
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val key = stringPreferencesKey("settingsKey")
    private var lastValidSettings: TileSettings? = null

    override val tileSettingsFlow = dataStore.data.map { preferences ->
        val rawJson = preferences[key]
        val settings = if (rawJson.isNullOrBlank()) {
            TileSettings()
        } else {
            try {
                val decoded = json.decodeFromString<TileSettings>(rawJson)
                lastValidSettings = decoded
                decoded
            } catch (e: Exception) {
                android.util.Log.e("SettingsRepository", "Failed to decode TileSettings from JSON: $rawJson", e)
                val recovered = recoverTileSettings(rawJson, json, lastValidSettings)
                lastValidSettings = recovered
                recovered
            }
        }
        settings
    }

    override suspend fun updateSettings(tileSettings: TileSettings) {
        lastValidSettings = tileSettings
        dataStore.edit {
            it[key] = json.encodeToString(tileSettings)
        }
    }

    companion object {
        fun recoverTileSettings(rawJson: String, json: Json, fallback: TileSettings?): TileSettings {
            val base = fallback ?: TileSettings()
            return runCatching {
                val element = json.parseToJsonElement(rawJson)
                val obj = element as? JsonObject ?: return base
                base.copy(
                    isTileFlipEnabled = obj["isTileFlipEnabled"]?.jsonPrimitive?.booleanOrNull ?: base.isTileFlipEnabled,
                    cornerRadius = obj["cornerRadius"]?.jsonPrimitive?.intOrNull ?: base.cornerRadius,
                    isAppLabelsHidden = obj["isAppLabelsHidden"]?.jsonPrimitive?.booleanOrNull ?: base.isAppLabelsHidden,
                    wallpaperPath = obj["wallpaperPath"]?.jsonPrimitive?.contentOrNull ?: base.wallpaperPath,
                    wallpaperTimestamp = obj["wallpaperTimestamp"]?.jsonPrimitive?.longOrNull ?: base.wallpaperTimestamp,
                    blurRadius = obj["blurRadius"]?.jsonPrimitive?.intOrNull ?: base.blurRadius,
                    tintAlpha = obj["tintAlpha"]?.jsonPrimitive?.floatOrNull ?: base.tintAlpha,
                )
            }.getOrDefault(base)
        }
    }
}