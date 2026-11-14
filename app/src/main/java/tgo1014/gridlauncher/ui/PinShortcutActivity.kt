package tgo1014.gridlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.ThemeRepository
import tgo1014.gridlauncher.domain.usecases.AddShortcutToGridUseCase
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import javax.inject.Inject

@AndroidEntryPoint
class PinShortcutActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var addShortcutToGridUseCase: AddShortcutToGridUseCase

    @Inject
    lateinit var appIconManager: AppIconManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        } else null

        val pinItemRequest: LauncherApps.PinItemRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            launcherApps?.getPinItemRequest(intent)
        } else null

        var label = "Shortcut"
        var packageName = ""
        var shortcutId = "shortcut_${System.currentTimeMillis()}"
        var intentUri: String? = null
        var shortcutDrawable: Drawable? = null
        var shortcutBitmap: Bitmap? = null

        if (pinItemRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutInfo: ShortcutInfo? = pinItemRequest.shortcutInfo
            if (shortcutInfo != null) {
                label = shortcutInfo.shortLabel?.toString()
                    ?: shortcutInfo.longLabel?.toString()
                    ?: "Shortcut"
                packageName = shortcutInfo.`package`
                shortcutId = shortcutInfo.id
                intentUri = shortcutInfo.intent?.toUri(Intent.URI_INTENT_SCHEME)
                shortcutDrawable = runCatching {
                    launcherApps?.getShortcutIconDrawable(shortcutInfo, resources.displayMetrics.densityDpi)
                        ?: launcherApps?.getShortcutBadgedIconDrawable(shortcutInfo, resources.displayMetrics.densityDpi)
                }.getOrNull() ?: runCatching {
                    packageManager.getApplicationIcon(packageName)
                }.getOrNull()
            }
        } else {
            // Legacy shortcut broadcast extra
            val legacyName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            val legacyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }
            val legacyBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
            }

            if (!legacyName.isNullOrEmpty()) {
                label = legacyName
                packageName = legacyIntent?.`package` ?: legacyIntent?.component?.packageName.orEmpty()
                shortcutId = "legacy_${System.currentTimeMillis()}"
                intentUri = legacyIntent?.toUri(Intent.URI_INTENT_SCHEME)
                shortcutBitmap = legacyBitmap
            } else {
                finish()
                return
            }
        }

        val previewBitmap: Bitmap? = shortcutBitmap ?: shortcutDrawable?.let {
            runCatching { it.toBitmap(128, 128) }.getOrNull()
        }

        setContent {
            val mainColor by themeRepository.currentTheme.collectAsState()
            GridLauncherTheme(mainColor) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = {
                            Text(
                                text = "Add to Home screen?",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = label,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    lifecycleScope.launch {
                                        val iconPath = when {
                                            shortcutDrawable != null -> appIconManager.saveShortcutIcon(shortcutId, shortcutDrawable)
                                            shortcutBitmap != null -> appIconManager.saveShortcutIcon(shortcutId, shortcutBitmap)
                                            previewBitmap != null -> appIconManager.saveShortcutIcon(shortcutId, previewBitmap)
                                            else -> null
                                        }
                                        addShortcutToGridUseCase(
                                            shortcutId = shortcutId,
                                            packageName = packageName,
                                            label = label,
                                            iconPath = iconPath,
                                            intentUri = intentUri
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pinItemRequest?.isValid == true) {
                                            pinItemRequest.accept()
                                        }
                                        finish()
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}
