package tgo1014.gridlauncher

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test
import tgo1014.gridlauncher.data.smarttile.BatteryTileManager

class BatteryTileManagerTest {

    @Test
    fun calculateChargingWatts_whenBatteryStatusFull_returnsZeroWatts() {
        // Even if on the charger with voltage and current, full battery should report 0 watts
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_FULL,
            voltageMv = 4200,
            currentUa = 30_000
        )
        assertEquals(0, watts)
    }

    @Test
    fun calculateChargingWatts_whenTrickleCharging_doesNotMisinterpretAsAmperes() {
        // Trickle charging reading: 30,000 µA (30 mA) at 4.2V = 0.126W (< 1W)
        // Previously misinterpreted as 30,000 mA = 30 A (120W clamp)
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 4200,
            currentUa = 30_000
        )
        assertEquals(0, watts)
    }

    @Test
    fun calculateChargingWatts_whenFastCharging_calculatesCorrectWattage() {
        // 2,000,000 µA (2.0 A) at 9V (9000 mV) = 18W
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 9000,
            currentUa = 2_000_000
        )
        assertEquals(18, watts)
    }

    @Test
    fun calculateChargingWatts_whenStandardUsbCharging_calculatesCorrectWattage() {
        // 1,000,000 µA (1.0 A) at 5V (5000 mV) = 5W
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 5000,
            currentUa = 1_000_000
        )
        assertEquals(5, watts)
    }

    @Test
    fun calculateChargingWatts_whenNegativeCurrentSign_handlesAbsoluteValue() {
        // Some drivers report negative current while charging: -1,500,000 µA (1.5 A) at 4.0V = 6W
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 4000,
            currentUa = -1_500_000
        )
        assertEquals(6, watts)
    }

    @Test
    fun calculateChargingWatts_whenOemDriverReportsInMilliAmperes_calculatesCorrectWattage() {
        // Some OEM kernels (MediaTek, Xiaomi, etc.) report current in mA: 2000 mA (2.0 A) at 4.0V = 8W
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 4000,
            currentUa = 2000
        )
        assertEquals(8, watts)
    }

    @Test
    fun calculateChargingWatts_whenOemFastChargingInMilliAmperes_calculatesCorrectWattage() {
        // Fast charging in mA: 3000 mA (3.0 A) at 9.0V = 27W
        val watts = BatteryTileManager.calculateChargingWatts(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltageMv = 9000,
            currentUa = 3000
        )
        assertEquals(27, watts)
    }

    @Test
    fun evaluateBluetoothState_whenDisconnectedAction_clearsAccessoryState() {
        val result = BatteryTileManager.evaluateBluetoothState(
            action = "android.bluetooth.device.action.ACL_DISCONNECTED",
            extraBatteryLevel = -1,
            deviceName = "Sony WH-1000XM4",
            isAudioConnected = false,
            cachedBatteryLevel = 85,
            currentIsConnected = true,
            currentName = "Sony WH-1000XM4",
            currentBattery = 85
        )
        assertEquals(false, result.isConnected)
        assertEquals(null, result.name)
        assertEquals(null, result.battery)
    }

    @Test
    fun evaluateBluetoothState_whenBatteryLevelChangedBroadcast_readsBroadcastExtraWithoutReflection() {
        val result = BatteryTileManager.evaluateBluetoothState(
            action = BatteryTileManager.ACTION_BATTERY_LEVEL_CHANGED,
            extraBatteryLevel = 75,
            deviceName = "Pixel Buds Pro",
            isAudioConnected = true,
            cachedBatteryLevel = null,
            currentIsConnected = false,
            currentName = null,
            currentBattery = null
        )
        assertEquals(true, result.isConnected)
        assertEquals("Pixel Buds Pro", result.name)
        assertEquals(75, result.battery)
    }

    @Test
    fun evaluateBluetoothState_whenNotAudioConnected_rejectsOfflineBondedDevices() {
        // Earbuds are in the case (offline). Audio profile is disconnected.
        // It should NOT treat cached battery level from bonded history as connected!
        val result = BatteryTileManager.evaluateBluetoothState(
            action = null,
            extraBatteryLevel = -1,
            deviceName = "Offline Earbuds",
            isAudioConnected = false,
            cachedBatteryLevel = 90,
            currentIsConnected = false,
            currentName = null,
            currentBattery = null
        )
        assertEquals(false, result.isConnected)
        assertEquals(null, result.name)
        assertEquals(null, result.battery)
    }

    @Test
    fun evaluateBluetoothState_whenAudioConnected_usesConnectedDeviceBattery() {
        val result = BatteryTileManager.evaluateBluetoothState(
            action = null,
            extraBatteryLevel = -1,
            deviceName = "Galaxy Buds2",
            isAudioConnected = true,
            cachedBatteryLevel = 80,
            currentIsConnected = false,
            currentName = null,
            currentBattery = null
        )
        assertEquals(true, result.isConnected)
        assertEquals("Galaxy Buds2", result.name)
        assertEquals(80, result.battery)
    }
}
