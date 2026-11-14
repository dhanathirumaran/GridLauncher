package tgo1014.gridlauncher.data.smarttile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatteryTileState(
    val phoneLevel: Int = 100,
    val isCharging: Boolean = false,
    val chargingWatts: Int = 0,
    val bluetoothDeviceName: String? = null,
    val bluetoothDeviceBattery: Int? = null,
    val isConnectedToBluetoothDevice: Boolean = false
)

@Singleton
class BatteryTileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(BatteryTileState())
    val state = _state.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            updateBattery(intent)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val stickyIntent = context.registerReceiver(batteryReceiver, filter)
        updateBattery(stickyIntent)
    }

    @SuppressLint("MissingPermission")
    fun updateBattery(intent: Intent? = null) {
        val batteryIntent = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else _state.value.phoneLevel

        var watts = 0
        if (isCharging) {
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            if (voltageMv > 0 && currentUa != 0) {
                val voltageV = voltageMv / 1000f
                val currentA = if (abs(currentUa) > 50_000) abs(currentUa) / 1_000_000f else abs(currentUa) / 1_000f
                val calculatedWatts = voltageV * currentA
                if (calculatedWatts > 0) {
                    watts = calculatedWatts.roundToInt().coerceIn(1, 120)
                }
            }
        }

        // Check for connected Bluetooth devices (headphones, keyboards, etc.)
        var btName: String? = null
        var btBattery: Int? = null
        var isConnectedBt = false

        runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter
            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices.orEmpty()
                for (device in bonded) {
                    val batteryLevel = getDeviceBattery(device)
                    if (batteryLevel != null && batteryLevel >= 0) {
                        btName = device.alias ?: device.name ?: "Accessory"
                        btBattery = batteryLevel
                        isConnectedBt = true
                        break
                    }
                }
            }
        }

        _state.value = BatteryTileState(
            phoneLevel = percent,
            isCharging = isCharging,
            chargingWatts = watts,
            bluetoothDeviceName = btName,
            bluetoothDeviceBattery = btBattery,
            isConnectedToBluetoothDevice = isConnectedBt
        )
    }

    private fun getDeviceBattery(device: BluetoothDevice): Int? {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val result = method.invoke(device) as? Int
            if (result != null && result in 0..100) result else null
        }.getOrNull()
    }
}
