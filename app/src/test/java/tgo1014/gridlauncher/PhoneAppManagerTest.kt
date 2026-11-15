package tgo1014.gridlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.data.phone.PhoneAppManager

class PhoneAppManagerTest {

    @Test
    fun isPhoneApp_detectsKnownDialerPackages() {
        assertTrue(PhoneAppManager.isPhoneApp("com.google.android.dialer"))
        assertTrue(PhoneAppManager.isPhoneApp("com.samsung.android.dialer"))
        assertTrue(PhoneAppManager.isPhoneApp("com.android.dialer"))
        assertTrue(PhoneAppManager.isPhoneApp("com.android.phone"))
        assertTrue(PhoneAppManager.isPhoneApp("com.oneplus.dialer"))
    }

    @Test
    fun isPhoneApp_rejectsNonPhonePackages() {
        assertFalse(PhoneAppManager.isPhoneApp("com.whatsapp"))
        assertFalse(PhoneAppManager.isPhoneApp("com.google.android.calendar"))
        assertFalse(PhoneAppManager.isPhoneApp("com.spotify.music"))
    }

    @Test
    fun extractCallerName_handlesVariousFormats() {
        // Title = "Missed call", Text = caller name
        assertEquals("Mom", PhoneAppManager.extractCallerName("Missed call", "Mom"))

        // Title = caller name, Text = "Missed call"
        assertEquals("Mom", PhoneAppManager.extractCallerName("Mom", "Missed call"))

        // Title with "Missed call from <name>"
        assertEquals("John Doe", PhoneAppManager.extractCallerName("Missed call from John Doe", "Mobile"))

        // Title = "2 missed calls", Text = caller name
        assertEquals("Alice", PhoneAppManager.extractCallerName("2 missed calls", "Alice"))

        // Phone number as caller
        assertEquals("+91 98765 43210", PhoneAppManager.extractCallerName("+91 98765 43210", "Missed call"))

        // Missing caller details
        assertEquals("Unknown Caller", PhoneAppManager.extractCallerName(null, null))
        assertEquals("Unknown Caller", PhoneAppManager.extractCallerName("Missed call", null))
        assertEquals("Unknown Caller", PhoneAppManager.extractCallerName("1 missed call", ""))
    }
}
