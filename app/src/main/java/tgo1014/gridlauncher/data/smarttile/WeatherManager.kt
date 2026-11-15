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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherState(
    val temperature: String = "--°",
    val condition: String = "Clear",
    val weatherCode: Int = 0
)

@Singleton
class WeatherManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("WEATHER_PREFS", Context.MODE_PRIVATE)
    private val _weatherState = MutableStateFlow(
        WeatherState(
            temperature = prefs.getString("LAST_TEMP", "--°") ?: "--°",
            condition = prefs.getString("LAST_COND", "Sunny") ?: "Sunny",
            weatherCode = prefs.getInt("LAST_CODE", 0)
        )
    )
    val weatherState = _weatherState.asStateFlow()

    init {
        fetchWeatherAsync()
    }

    fun fetchWeatherAsync() {
        scope.launch { fetchWeather() }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchWeather() = withContext(Dispatchers.IO) {
        var lat = 28.6139
        var lon = 77.2090
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
            }
        }

        runCatching {
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

            _weatherState.value = WeatherState(tempStr, cond, code)
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
