package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import tgo1014.gridlauncher.domain.AppsManagerDataSource
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class AppsManagerDataSourceImpl @Inject constructor(
    private val json: Json,
    private val dataStore: DataStore<Preferences>,
) : AppsManagerDataSource {

    private val appListKey = stringPreferencesKey("appListKey")
    private val gridKey = stringPreferencesKey("gridKey")
    private var lastValidGrid: List<GridItem>? = null

    override var installedAppsList: Flow<List<App>> = dataStore.data
        .map {
            runCatching {
                json.decodeFromString<List<App>>(it[appListKey]!!)
            }.getOrDefault(emptyList())
        }

    override val homeGridFlow: Flow<List<GridItem>> = dataStore.data
        .map { preferences ->
            val rawJson = preferences[gridKey]
            if (rawJson.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val decoded = json.decodeFromString<List<GridItem>>(rawJson)
                    lastValidGrid = decoded
                    decoded
                } catch (e: Exception) {
                    android.util.Log.e("AppsManagerDataSource", "Failed to decode home grid from JSON, attempting recovery", e)
                    val recovered = recoverGridItems(rawJson, json)
                    if (recovered.isNotEmpty()) {
                        lastValidGrid = recovered
                        recovered
                    } else {
                        lastValidGrid ?: emptyList()
                    }
                }
            }
        }

    override suspend fun setAppList(appList: List<App>) {
        dataStore.edit {
            it[appListKey] = json.encodeToString(appList)
        }
    }

    override suspend fun setGrid(grid: List<GridItem>) {
        lastValidGrid = grid
        dataStore.edit {
            it[gridKey] = json.encodeToString(grid)
        }
    }

    companion object {
        fun recoverGridItems(rawJson: String, json: Json): List<GridItem> {
            return runCatching {
                val element = json.parseToJsonElement(rawJson)
                val array = element as? JsonArray ?: return emptyList()
                val recovered = mutableListOf<GridItem>()
                for (itemElem in array) {
                    runCatching {
                        json.decodeFromJsonElement<GridItem>(itemElem)
                    }.getOrNull()?.let { recovered.add(it) }
                }
                recovered
            }.getOrDefault(emptyList())
        }
    }
}