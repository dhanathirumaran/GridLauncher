package tgo1014.gridlauncher.data.phone

import android.content.Context
import android.telecom.TelecomManager

object PhoneAppManager {
    val KNOWN_DIALER_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.oneplus.dialer",
        "com.coloros.dialer",
        "com.oppo.dialer",
        "com.vivo.dialer",
        "com.asus.dialer",
        "com.nothing.dialer",
        "com.motorola.dialer"
    )

    fun isPhoneApp(packageName: String, context: Context? = null): Boolean {
        if (packageName in KNOWN_DIALER_PACKAGES) return true
        val lower = packageName.lowercase()
        if (lower.contains("dialer")) return true
        if (lower.endsWith(".phone") || lower == "com.phone") return true
        if (context != null) {
            try {
                val tm = context.getSystemService(TelecomManager::class.java)
                if (tm?.defaultDialerPackage == packageName) return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun getAllDialerPackages(context: Context? = null): Set<String> {
        val result = KNOWN_DIALER_PACKAGES.toMutableSet()
        if (context != null) {
            try {
                val tm = context.getSystemService(TelecomManager::class.java)
                tm?.defaultDialerPackage?.let { result.add(it) }
            } catch (_: Exception) {}
        }
        return result
    }

    fun extractCallerName(rawTitle: String?, rawText: String?): String {
        val title = rawTitle?.trim()
        val text = rawText?.trim()
        val titleLower = title?.lowercase().orEmpty()
        val textLower = text?.lowercase().orEmpty()

        return when {
            titleLower.startsWith("missed call from ") -> {
                title!!.substring(17).trim()
            }
            titleLower.contains("missed") && !text.isNullOrBlank() && !textLower.contains("missed") -> {
                text
            }
            textLower.contains("missed") && !title.isNullOrBlank() && !titleLower.contains("missed") -> {
                title
            }
            !title.isNullOrBlank() && !titleLower.contains("missed") -> {
                title
            }
            !text.isNullOrBlank() && !textLower.contains("missed") -> {
                text
            }
            !title.isNullOrBlank() && titleLower != "missed call" && !titleLower.matches(Regex("\\d+ missed calls?")) -> {
                title
            }
            !text.isNullOrBlank() && textLower != "missed call" && !textLower.matches(Regex("\\d+ missed calls?")) -> {
                text
            }
            else -> "Unknown Caller"
        }
    }
}
