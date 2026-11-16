package tgo1014.gridlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import tgo1014.gridlauncher.domain.AppsManagerDataSource
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class AppsManagerDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val dataStore: DataStore<Preferences>,
) : AppsManagerDataSource {

    private val appListKey = stringPreferencesKey("appListKey")
    private val gridKey = stringPreferencesKey("gridKey")
    private val gridBackupKey = stringPreferencesKey("gridBackupKey")
    private val smartTilesInitializedKey = booleanPreferencesKey("smartTilesInitializedKey")

    private val gridTransformMutex = Mutex()
    private var lastValidGrid: List<GridItem>? = null

    override var installedAppsList: Flow<List<App>> = dataStore.data
        .map {
            runCatching {
                json.decodeFromString<List<App>>(it[appListKey]!!)
            }.getOrDefault(emptyList())
        }

    // Two-tier resilience strategy:
    // 1. In-memory `lastValidGrid` keeps the last known-good grid active across transient parse failures.
    // 2. Persistent `gridBackupKey` stores the last non-empty valid grid in DataStore, ensuring cold-start
    //    crashes or corrupt DataStore files recover automatically instead of wiping the user's home screen.
    override val homeGridFlow: Flow<List<GridItem>> = dataStore.data
        .map { preferences ->
            decodeGridFromPreferences(preferences)
        }

    private fun decodeGridFromPreferences(preferences: Preferences): List<GridItem> {
        val rawJson = preferences[gridKey]
        return if (rawJson.isNullOrBlank()) {
            val backup = preferences[gridBackupKey]
            if (!backup.isNullOrBlank()) {
                val recovered = runCatching { json.decodeFromString<List<GridItem>>(backup) }.getOrNull()
                if (recovered != null) {
                    lastValidGrid = recovered
                    return recovered
                }
            }
            emptyList()
        } else {
            try {
                val decoded = json.decodeFromString<List<GridItem>>(rawJson)
                lastValidGrid = decoded
                decoded
            } catch (e: Exception) {
                android.util.Log.e("AppsManagerDataSource", "Failed to decode home grid from JSON, attempting recovery", e)
                val backup = preferences[gridBackupKey]
                val backupDecoded = if (!backup.isNullOrBlank()) {
                    runCatching { json.decodeFromString<List<GridItem>>(backup) }.getOrNull()
                } else null

                if (backupDecoded != null && backupDecoded.isNotEmpty()) {
                    lastValidGrid = backupDecoded
                    backupDecoded
                } else {
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
    }

    override suspend fun setAppList(appList: List<App>) {
        dataStore.edit {
            it[appListKey] = json.encodeToString(appList)
        }
    }

    override suspend fun setGrid(grid: List<GridItem>) {
        updateGrid { grid }
    }

    /**
     * ATOMIC GRID TRANSFORM:
     * Eliminates read-modify-write lost update race conditions.
     * When a user drags a tile while a background resume scan is running,
     * mutations are serialized sequentially under [gridTransformMutex] and DataStore's edit lock,
     * guaranteeing that the latest grid state is always transformed and saved without overwriting concurrent edits.
     */
    override suspend fun updateGrid(transform: (currentGrid: List<GridItem>) -> List<GridItem>): List<GridItem> {
        return gridTransformMutex.withLock {
            var updatedGrid: List<GridItem> = emptyList()
            dataStore.edit { preferences ->
                val currentGrid = decodeGridFromPreferences(preferences)
                val newGrid = transform(currentGrid)
                updatedGrid = newGrid
                lastValidGrid = newGrid
                val encoded = json.encodeToString(newGrid)
                preferences[gridKey] = encoded
                if (newGrid.isNotEmpty()) {
                    preferences[gridBackupKey] = encoded
                }
            }
            updatedGrid
        }
    }

    /**
     * ATOMIC GRID & SMART TILES INITIALIZATION:
     * Commits the home grid and the [smartTilesInitializedKey] in the exact same DataStore transaction.
     *
     * BACKUP & RESTORE RESILIENCE:
     * Previously, the SMART_TILES_INITIALIZED flag was stored in SharedPreferences (SMART_TILES_PREFS.xml),
     * which was excluded from Google Drive cloud backups. After a backup restore, the grid was restored
     * but the flag was missing, causing all 7 smart tiles to be appended again.
     * Storing the flag in DataStore guarantees it is backed up and restored alongside the grid.
     */
    override suspend fun updateGridAndSmartTiles(
        transform: (currentGrid: List<GridItem>, wasInitialized: Boolean) -> Pair<List<GridItem>, Boolean>
    ): List<GridItem> {
        return gridTransformMutex.withLock {
            var updatedGrid: List<GridItem> = emptyList()
            dataStore.edit { preferences ->
                val currentGrid = decodeGridFromPreferences(preferences)
                val legacyPrefs = context.getSharedPreferences("SMART_TILES_PREFS", Context.MODE_PRIVATE)
                val wasInit = preferences[smartTilesInitializedKey]
                    ?: legacyPrefs.getBoolean("SMART_TILES_INITIALIZED", false)

                val (newGrid, newInit) = transform(currentGrid, wasInit)
                updatedGrid = newGrid
                lastValidGrid = newGrid
                val encoded = json.encodeToString(newGrid)
                preferences[gridKey] = encoded
                if (newGrid.isNotEmpty()) {
                    preferences[gridBackupKey] = encoded
                }
                preferences[smartTilesInitializedKey] = newInit
            }
            updatedGrid
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