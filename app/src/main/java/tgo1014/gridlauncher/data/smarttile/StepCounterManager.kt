package tgo1014.gridlauncher.data.smarttile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val prefs = context.getSharedPreferences("STEP_COUNTER_PREFS", Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _todaySteps = MutableStateFlow(0)
    val todaySteps = _todaySteps.asStateFlow()

    init {
        val lastRaw = prefs.getInt("LAST_RAW_STEPS", 0)
        checkDailyReset(lastRaw)
        _todaySteps.value = calculateTodaySteps(lastRaw)
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val raw = event.values[0].toInt()
            checkDailyReset(raw)
            prefs.edit().putInt("LAST_RAW_STEPS", raw).apply()
            _todaySteps.value = calculateTodaySteps(raw)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun refresh() {
        val lastRaw = prefs.getInt("LAST_RAW_STEPS", 0)
        checkDailyReset(lastRaw)
        _todaySteps.value = calculateTodaySteps(lastRaw)
    }

    private fun checkDailyReset(currentRaw: Int = prefs.getInt("LAST_RAW_STEPS", 0)) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDay = prefs.getString("LAST_DAY", "")
        if (savedDay != todayStr) {
            // New day has arrived: reset baseline to current cumulative raw steps
            prefs.edit()
                .putString("LAST_DAY", todayStr)
                .putInt("BASELINE_STEPS", currentRaw)
                .apply()
        }
    }

    private fun calculateTodaySteps(currentRaw: Int): Int {
        checkDailyReset(currentRaw)
        val baseline = prefs.getInt("BASELINE_STEPS", currentRaw)
        return (currentRaw - baseline).coerceAtLeast(0)
    }
}
