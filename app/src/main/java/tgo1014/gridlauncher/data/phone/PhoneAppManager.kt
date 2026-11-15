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
        val title = rawTitle?.trim().orEmpty()
        val text = rawText?.trim().orEmpty()
        val titleLower = title.lowercase()
        val textLower = text.lowercase()

        fun isGenericMissedCallPhrase(s: String): Boolean {
            val lower = s.lowercase().trim()
            return lower.isEmpty() ||
                   lower == "missed call" ||
                   lower == "missed calls" ||
                   lower.matches(Regex("\\d+\\s+missed\\s+calls?")) ||
                   lower.matches(Regex("missed\\s+call(s)?"))
        }

        if (titleLower.startsWith("missed call from ")) {
            val candidate = title.substring(17).trim()
            if (candidate.isNotBlank()) return candidate
        }

        // If title is a genuine caller/contact name (and not a generic "Missed calls" string)
        if (title.isNotBlank() && !isGenericMissedCallPhrase(title) && !titleLower.contains("missed call")) {
            return title
        }

        // If title is generic (e.g. "Missed calls" or "2 missed calls"), check text for the contact name
        if (text.isNotBlank() && !isGenericMissedCallPhrase(text) && !textLower.contains("missed call")) {
            return text
        }

        // Try extracting phone number or name from text like "John Doe - 2:30 PM" or "John Doe • Mobile"
        if (text.isNotBlank() && !isGenericMissedCallPhrase(text)) {
            val cleaned = text.substringBefore(" - ").substringBefore(" • ").trim()
            if (cleaned.isNotBlank() && !isGenericMissedCallPhrase(cleaned)) {
                return cleaned
            }
        }

        return "Unknown Caller"
    }
}
