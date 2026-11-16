package tgo1014.gridlauncher.data.smarttile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.ContextCompat
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

    private var connectedDeviceAddress: String? = null

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
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // ANDROID 14+ (API 34+) RECEIVER REGISTRATION HARDENING:
        // Context.registerReceiver() without an export flag throws SecurityException on Android 14+
        // when intent filters include non-protected broadcasts (such as Bluetooth ACL events).
        // Using ContextCompat.registerReceiver with RECEIVER_NOT_EXPORTED ensures compliance across
        // all API levels, and wrapping in runCatching prevents startup crashes on restricted/sandbox environments.
        val stickyIntent = runCatching {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.getOrNull()
        updateBattery(stickyIntent)
    }

    @SuppressLint("MissingPermission")
    fun updateBattery(intent: Intent? = null) {
        val batteryIntent = if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            intent
        } else {
            runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
        }
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else _state.value.phoneLevel

        var watts = 0
        if (isCharging) {
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            watts = calculateChargingWatts(status, voltageMv, currentUa)
        }

        // Check for connected Bluetooth devices (headphones, keyboards, etc.)
        var btName = _state.value.bluetoothDeviceName
        var btBattery = _state.value.bluetoothDeviceBattery
        var isConnectedBt = _state.value.isConnectedToBluetoothDevice

        val action = intent?.action
        when (action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val disconnectedDevice = getDeviceFromIntent(intent)
                if (disconnectedDevice == null || disconnectedDevice.address == connectedDeviceAddress) {
                    connectedDeviceAddress = null
                    btName = null
                    btBattery = null
                    isConnectedBt = false
                }
            }
            ACTION_BATTERY_LEVEL_CHANGED -> {
                val batteryLevel = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                val device = getDeviceFromIntent(intent)
                if (batteryLevel in 0..100 && device != null) {
                    connectedDeviceAddress = device.address
                    btName = getDeviceName(device)
                    btBattery = batteryLevel
                    isConnectedBt = true
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = getDeviceFromIntent(intent)
                if (device != null) {
                    connectedDeviceAddress = device.address
                    btName = getDeviceName(device)
                    val batteryLevel = getDeviceBattery(device)
                    btBattery = if (batteryLevel in 0..100) batteryLevel else null
                    isConnectedBt = true
                }
            }
            else -> {
                // Periodic refresh or startup: check active audio profile connections
                runCatching {
                    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = bm?.adapter
                    if (adapter != null && adapter.isEnabled) {
                        val headsetConnected = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                        val a2dpConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        val isAudioConnected = headsetConnected || a2dpConnected

                        if (isAudioConnected) {
                            val bonded = adapter.bondedDevices.orEmpty()
                            val connectedDevices = bonded.filter { isDeviceConnected(it) }
                            val targetDevice = connectedDevices.find { it.address == connectedDeviceAddress && getDeviceBattery(it) != null }
                                ?: connectedDevices.find { getDeviceBattery(it) != null }
                                ?: connectedDevices.firstOrNull()

                            if (targetDevice != null) {
                                btName = getDeviceName(targetDevice)
                                btBattery = getDeviceBattery(targetDevice)
                                isConnectedBt = true
                                connectedDeviceAddress = targetDevice.address
                            } else {
                                btName = null
                                btBattery = null
                                isConnectedBt = false
                                connectedDeviceAddress = null
                            }
                        } else if (connectedDeviceAddress == null) {
                            btName = null
                            btBattery = null
                            isConnectedBt = false
                        }
                    } else {
                        btName = null
                        btBattery = null
                        isConnectedBt = false
                        connectedDeviceAddress = null
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

    private fun getDeviceFromIntent(intent: Intent?): BluetoothDevice? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun getDeviceName(device: BluetoothDevice): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            device.alias ?: device.name ?: "Accessory"
        } else {
            device.name ?: "Accessory"
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun getDeviceBattery(device: BluetoothDevice): Int? {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val result = method.invoke(device) as? Int
            if (result != null && result in 0..100) result else null
        }.getOrNull()
    }

    companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        data class BluetoothBatteryResult(
            val isConnected: Boolean,
            val name: String?,
            val battery: Int?
        )

        fun evaluateBluetoothState(
            action: String?,
            extraBatteryLevel: Int,
            deviceName: String?,
            isAudioConnected: Boolean,
            cachedBatteryLevel: Int?,
            currentIsConnected: Boolean,
            currentName: String?,
            currentBattery: Int?
        ): BluetoothBatteryResult {
            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                return BluetoothBatteryResult(isConnected = false, name = null, battery = null)
            }
            if (action == ACTION_BATTERY_LEVEL_CHANGED && extraBatteryLevel in 0..100) {
                return BluetoothBatteryResult(
                    isConnected = true,
                    name = deviceName ?: "Accessory",
                    battery = extraBatteryLevel
                )
            }
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                return BluetoothBatteryResult(
                    isConnected = true,
                    name = deviceName ?: "Accessory",
                    battery = if (extraBatteryLevel in 0..100) extraBatteryLevel else cachedBatteryLevel
                )
            }
            if (isAudioConnected) {
                return BluetoothBatteryResult(
                    isConnected = true,
                    name = deviceName ?: currentName ?: "Accessory",
                    battery = cachedBatteryLevel ?: currentBattery
                )
            }
            // If audio profile is not connected and no active ACL link, reject offline bonded cache
            return BluetoothBatteryResult(
                isConnected = false,
                name = null,
                battery = null
            )
        }

        fun calculateChargingWatts(status: Int, voltageMv: Int, currentUa: Int): Int {
            if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                return 0
            }
            if (voltageMv <= 0 || currentUa == 0) {
                return 0
            }
            val voltageV = voltageMv / 1000f
            // Android standard BATTERY_PROPERTY_CURRENT_NOW is in microamperes (µA).
            // Many OEM kernels (MediaTek, Xiaomi, Sony, etc.) report in milliamperes (mA).
            // Valid phone charge currents are 100mA to 15,000mA (0.1A to 15A).
            // Values in 100..15_000 are mA; values > 15_000 are µA.
            val rawAbs = abs(currentUa)
            val currentA = when {
                rawAbs in 100..15_000 -> rawAbs / 1_000f
                rawAbs > 15_000 -> rawAbs / 1_000_000f
                else -> 0f
            }
            val calculatedWatts = voltageV * currentA
            return if (calculatedWatts >= 1.0f) {
                calculatedWatts.roundToInt().coerceIn(1, 120)
            } else {
                0
            }
        }
    }
}
