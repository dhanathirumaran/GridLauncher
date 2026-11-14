package tgo1014.gridlauncher.domain

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import tgo1014.gridlauncher.domain.models.Icon

interface AppIconManager {
    suspend fun getIcon(packageName: String): Icon
    suspend fun saveShortcutIcon(key: String, drawable: Drawable): String
    suspend fun saveShortcutIcon(key: String, bitmap: Bitmap): String
}