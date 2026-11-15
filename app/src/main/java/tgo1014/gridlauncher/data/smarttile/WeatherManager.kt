package tgo1014.gridlauncher.data.smarttile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherState(
    val temperature: String = "--°",
    val condition: String = "No Data",
    val weatherCode: Int = 0,
    val isError: Boolean = false,
    val isLocationMissing: Boolean = false
)

@Singleton
class WeatherManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("WEATHER_PREFS", Context.MODE_PRIVATE)
    private val fetchMutex = Mutex()
    private var lastFetchTimeMs = 0L

    private val _weatherState = MutableStateFlow(
        WeatherState(
            temperature = prefs.getString("LAST_TEMP", "--°") ?: "--°",
            condition = prefs.getString("LAST_COND", "No Data") ?: "No Data",
            weatherCode = prefs.getInt("LAST_CODE", 0)
        )
    )
    val weatherState = _weatherState.asStateFlow()

    init {
        fetchWeatherAsync()
    }

    fun fetchWeatherAsync(force: Boolean = false) {
        scope.launch { fetchWeather(force) }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchWeather(force: Boolean = false) = withContext(Dispatchers.IO) {
        // Throttle automatic refreshes to every 15 minutes unless forced
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchTimeMs < 15 * 60 * 1000L && _weatherState.value.temperature != "--°") {
            return@withContext
        }

        fetchMutex.withLock {
            var lat: Double? = null
            var lon: Double? = null

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null) {
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                var loc: Location? = null
                if (hasFine) {
                    loc = runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
                        ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                }
                if (loc == null && (hasFine || hasCoarse)) {
                    loc = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
                }
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    prefs.edit()
                        .putFloat("LAST_LAT", loc.latitude.toFloat())
                        .putFloat("LAST_LON", loc.longitude.toFloat())
                        .apply()
                }
            }

            // If no fresh location, fallback to last known saved location
            if (lat == null || lon == null) {
                if (prefs.contains("LAST_LAT") && prefs.contains("LAST_LON")) {
                    lat = prefs.getFloat("LAST_LAT", 0f).toDouble()
                    lon = prefs.getFloat("LAST_LON", 0f).toDouble()
                }
            }

            // If still no location, show "No Location" and do NOT query Delhi
            if (lat == null || lon == null) {
                _weatherState.value = WeatherState(
                    temperature = "--°",
                    condition = "No Location",
                    isLocationMissing = true
                )
                return@withLock
            }

            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "GridLauncher/1.0")
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val cond = interpretWeatherCode(code)
                val tempStr = "${temp.toInt()}°"

                prefs.edit()
                    .putString("LAST_TEMP", tempStr)
                    .putString("LAST_COND", cond)
                    .putInt("LAST_CODE", code)
                    .apply()

                lastFetchTimeMs = System.currentTimeMillis()
                _weatherState.value = WeatherState(
                    temperature = tempStr,
                    condition = cond,
                    weatherCode = code,
                    isError = false,
                    isLocationMissing = false
                )
            } catch (e: Exception) {
                // If API fails, keep cached temp if available or show Offline
                val cachedTemp = prefs.getString("LAST_TEMP", null)
                val cachedCond = prefs.getString("LAST_COND", "Offline") ?: "Offline"
                val cachedCode = prefs.getInt("LAST_CODE", 0)
                if (cachedTemp != null) {
                    _weatherState.value = WeatherState(
                        temperature = cachedTemp,
                        condition = cachedCond,
                        weatherCode = cachedCode,
                        isError = true,
                        isLocationMissing = false
                    )
                } else {
                    _weatherState.value = WeatherState(
                        temperature = "--°",
                        condition = "Offline",
                        isError = true,
                        isLocationMissing = false
                    )
                }
            }
        }
    }

    private fun interpretWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55, 61, 63, 65 -> "Rainy"
            71, 73, 75, 77 -> "Snowy"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Sunny"
        }
    }
}
