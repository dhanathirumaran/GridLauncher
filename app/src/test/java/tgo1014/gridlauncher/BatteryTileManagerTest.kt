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
}
