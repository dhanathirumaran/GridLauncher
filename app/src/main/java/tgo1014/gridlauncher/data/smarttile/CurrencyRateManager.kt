package tgo1014.gridlauncher.data.smarttile

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CurrencyState(
    val rate: String = "--.--",
    val isError: Boolean = false,
    val isStale: Boolean = false
)

@Singleton
class CurrencyRateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("CURRENCY_PREFS", Context.MODE_PRIVATE)
    private val fetchMutex = Mutex()

    private val cachedRate = prefs.getString("LAST_USD_INR", null)
    private val _currencyState = MutableStateFlow(
        if (cachedRate != null) {
            CurrencyState(rate = cachedRate, isError = false, isStale = true)
        } else {
            CurrencyState(rate = "--.--", isError = false, isStale = false)
        }
    )
    val currencyState = _currencyState.asStateFlow()

    // Backward-compatible flow
    private val _usdInrRate = MutableStateFlow(cachedRate ?: "--.--")
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

        fetchMutex.withLock {
            try {
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

                _currencyState.value = CurrencyState(rate = formatted, isError = false, isStale = false)
                _usdInrRate.value = formatted
            } catch (e: Exception) {
                val saved = prefs.getString("LAST_USD_INR", null)
                if (saved != null) {
                    _currencyState.value = CurrencyState(rate = saved, isError = true, isStale = true)
                    _usdInrRate.value = saved
                } else {
                    _currencyState.value = CurrencyState(rate = "--.--", isError = true, isStale = false)
                    _usdInrRate.value = "--.--"
                }
            }
        }
    }
}
