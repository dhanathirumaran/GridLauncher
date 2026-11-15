package tgo1014.gridlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import tgo1014.gridlauncher.data.smarttile.StepCounterManager

class StepCounterManagerTest {

    @Test
    fun computeSteps_onFirstInstall_calibratesZeroSteps() {
        val result = StepCounterManager.computeStepsOnSensorEvent(
            raw = 10_000,
            lastRaw = -1,
            currentBaseline = 0,
            currentPreReboot = 0,
            isNewDay = false,
            isInitialized = false
        )
        assertEquals(0, result.todaySteps)
        assertEquals(10_000, result.baseline)
        assertEquals(0, result.preReboot)
    }

    @Test
    fun computeSteps_onNewDayWithoutReboot_preservesEarlyMorningSteps() {
        // Yesterday closed at lastRaw = 10,000.
        // Today user walked from 6 AM to 8 AM (2,000 steps). First event at 8 AM arrives with raw = 12,000.
        // Early morning steps should NOT be discarded; todaySteps should be 2,000!
        val result = StepCounterManager.computeStepsOnSensorEvent(
            raw = 12_000,
            lastRaw = 10_000,
            currentBaseline = 5_000,
            currentPreReboot = 0,
            isNewDay = true,
            isInitialized = true
        )
        assertEquals(2_000, result.todaySteps)
        assertEquals(10_000, result.baseline)
        assertEquals(0, result.preReboot)
    }

    @Test
    fun computeSteps_onNewDayWithOvernightReboot_preservesEarlyMorningSteps() {
        // Device rebooted overnight, resetting sensor to 0.
        // User walks 150 steps in the morning before first event (raw = 150).
        // Since raw < lastRaw, baseline should be 0, yielding 150 steps.
        val result = StepCounterManager.computeStepsOnSensorEvent(
            raw = 150,
            lastRaw = 10_000,
            currentBaseline = 5_000,
            currentPreReboot = 0,
            isNewDay = true,
            isInitialized = true
        )
        assertEquals(150, result.todaySteps)
        assertEquals(0, result.baseline)
        assertEquals(0, result.preReboot)
    }

    @Test
    fun computeSteps_onSameDayReboot_preservesStepsTakenBeforeFirstEvent() {
        // On the same day, user had walked 5,000 steps (baseline = 2,000, lastRaw = 7,000).
        // Device reboots. User walks 50 steps before GridLauncher receives an event (raw = 50).
        // baseline must be set to 0 (not 50) so the 50 steps walked since boot are not dropped!
        val result = StepCounterManager.computeStepsOnSensorEvent(
            raw = 50,
            lastRaw = 7_000,
            currentBaseline = 2_000,
            currentPreReboot = 0,
            isNewDay = false,
            isInitialized = true
        )
        // preReboot should be 5,000 (7,000 - 2,000)
        assertEquals(5_000, result.preReboot)
        // baseline should be 0 for the new boot cycle
        assertEquals(0, result.baseline)
        // total steps = 5,000 (pre-reboot) + 50 (current boot) = 5,050
        assertEquals(5_050, result.todaySteps)
    }

    @Test
    fun computeSteps_normalStepIncrement_accumulatesCorrectly() {
        val result = StepCounterManager.computeStepsOnSensorEvent(
            raw = 7_010,
            lastRaw = 7_000,
            currentBaseline = 2_000,
            currentPreReboot = 0,
            isNewDay = false,
            isInitialized = true
        )
        assertEquals(5_010, result.todaySteps)
        assertEquals(2_000, result.baseline)
        assertEquals(0, result.preReboot)
    }
}
