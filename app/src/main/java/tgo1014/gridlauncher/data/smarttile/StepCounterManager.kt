package tgo1014.gridlauncher.data.smarttile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class StepCounterState(
    val steps: Int = 0,
    val isSupported: Boolean = true,
    val hasPermission: Boolean = true
)

@Singleton
class StepCounterManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val prefs = context.getSharedPreferences("STEP_COUNTER_PREFS", Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _stepCounterState = MutableStateFlow(
        StepCounterState(
            isSupported = stepSensor != null,
            hasPermission = checkPermission()
        )
    )
    val stepCounterState = _stepCounterState.asStateFlow()

    private val _todaySteps = MutableStateFlow(0)
    val todaySteps = _todaySteps.asStateFlow()

    private val midnightReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    refresh()
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(midnightReceiver, filter)
        } catch (_: Exception) {}

        refresh()
        startListening()
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun startListening() {
        val hasPerm = checkPermission()
        val isSupported = stepSensor != null

        if (!isSupported) {
            _stepCounterState.value = _stepCounterState.value.copy(isSupported = false, hasPermission = hasPerm)
            return
        }
        if (!hasPerm) {
            _stepCounterState.value = _stepCounterState.value.copy(isSupported = true, hasPermission = false)
            return
        }

        stepSensor?.let {
            sensorManager?.unregisterListener(this)
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        _stepCounterState.value = _stepCounterState.value.copy(
            isSupported = true,
            hasPermission = true
        )
        refresh()
    }

    fun stopListening() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val raw = event.values[0].toInt()
            updateSteps(raw)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Synchronized
    fun refresh() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDay = prefs.getString("LAST_DAY", "")
        if (savedDay != todayStr) {
            _todaySteps.value = 0
            _stepCounterState.value = _stepCounterState.value.copy(steps = 0)
            return
        }
        val isInitialized = prefs.getBoolean("INITIALIZED", false)
        if (!isInitialized) {
            _todaySteps.value = 0
            _stepCounterState.value = _stepCounterState.value.copy(steps = 0)
            return
        }
        val lastRaw = prefs.getInt("LAST_RAW_STEPS", 0)
        val baseline = prefs.getInt("BASELINE_STEPS", lastRaw)
        val preReboot = prefs.getInt("PRE_REBOOT_TODAY_STEPS", 0)
        val currentBootSteps = (lastRaw - baseline).coerceAtLeast(0)
        val total = preReboot + currentBootSteps
        _todaySteps.value = total
        _stepCounterState.value = _stepCounterState.value.copy(steps = total)
    }

    @Synchronized
    private fun updateSteps(raw: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDay = prefs.getString("LAST_DAY", "")
        val lastRaw = prefs.getInt("LAST_RAW_STEPS", -1)
        val isInitialized = prefs.getBoolean("INITIALIZED", false)

        if (!isInitialized || savedDay != todayStr) {
            // First install or new day: calibrate baseline so today's steps start at 0
            prefs.edit()
                .putString("LAST_DAY", todayStr)
                .putInt("BASELINE_STEPS", raw)
                .putInt("PRE_REBOOT_TODAY_STEPS", 0)
                .putInt("LAST_RAW_STEPS", raw)
                .putBoolean("INITIALIZED", true)
                .apply()
            _todaySteps.value = 0
            _stepCounterState.value = _stepCounterState.value.copy(steps = 0)
            return
        }

        // Reboot detection on same day:
        // Hardware sensor resets to 0 upon device reboot.
        var preReboot = prefs.getInt("PRE_REBOOT_TODAY_STEPS", 0)
        var baseline = prefs.getInt("BASELINE_STEPS", raw)
        if (lastRaw != -1 && raw < lastRaw) {
            val stepsBeforeReboot = (lastRaw - baseline).coerceAtLeast(0)
            preReboot += stepsBeforeReboot
            baseline = raw
            prefs.edit()
                .putInt("PRE_REBOOT_TODAY_STEPS", preReboot)
                .putInt("BASELINE_STEPS", baseline)
                .apply()
        }

        prefs.edit().putInt("LAST_RAW_STEPS", raw).apply()
        val currentBootSteps = (raw - baseline).coerceAtLeast(0)
        val total = preReboot + currentBootSteps
        _todaySteps.value = total
        _stepCounterState.value = _stepCounterState.value.copy(steps = total)
    }
}
