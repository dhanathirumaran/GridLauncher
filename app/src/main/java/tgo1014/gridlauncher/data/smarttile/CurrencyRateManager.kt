package tgo1014.gridlauncher.data.smarttile

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("CURRENCY_PREFS", Context.MODE_PRIVATE)
    private val _usdInrRate = MutableStateFlow(prefs.getString("LAST_USD_INR", "84.40") ?: "84.40")
    val usdInrRate = _usdInrRate.asStateFlow()

    init {
        scope.launch { fetchRateIfNeeded() }
    }

    suspend fun fetchRateIfNeeded(force: Boolean = false) = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastFetchDay = prefs.getString("LAST_FETCH_DAY", "")
        if (!force && lastFetchDay == todayStr && prefs.contains("LAST_USD_INR")) {
            return@withContext
        }
        runCatching {
            val url = URL("https://open.er-api.com/v6/latest/USD")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "GridLauncher/1.0")
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val rates = json.getJSONObject("rates")
            val inr = rates.getDouble("INR")
            val formatted = String.format(Locale.US, "%.2f", inr)
            prefs.edit()
                .putString("LAST_USD_INR", formatted)
                .putString("LAST_FETCH_DAY", todayStr)
                .apply()
            _usdInrRate.value = formatted
        }
    }
}
