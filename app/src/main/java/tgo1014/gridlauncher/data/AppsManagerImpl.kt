package tgo1014.gridlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.content.pm.PackageManager.MATCH_ALL
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.AppsManagerDataSource
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject


class AppsManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageManager: PackageManager,
    private val appIconManager: AppIconManager,
    private val appsManagerDataSource: AppsManagerDataSource,
    private val scope: CoroutineScope,
) : AppsManager {

    override val installedAppsFlow = appsManagerDataSource.installedAppsList
    override val homeGridFlow = appsManagerDataSource.homeGridFlow

    private val scanMutex = Mutex()
    @Volatile
    private var inFlightScan: Deferred<Unit>? = null

    // Startup scanning is centralized in UpdateAppListUseCase (invoked by MainActivity)
    // to avoid redundant concurrent package scanning on startup.

    override fun openApp(app: App) {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "App not found or disabled: ${app.name}", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { e ->
            e.printStackTrace()
            Toast.makeText(context, "Unable to launch ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openShortcut(gridItem: GridItem) {
        try {
            if (!gridItem.shortcutPackage.isNullOrEmpty() && !gridItem.shortcutId.isNullOrEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                    if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                        launcherApps.startShortcut(gridItem.shortcutPackage, gridItem.shortcutId, null, null, android.os.Process.myUserHandle())
                        return
                    }
                }
            }
            if (!gridItem.shortcutIntentUri.isNullOrEmpty()) {
                val intent = Intent.parseUri(gridItem.shortcutIntentUri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun setGrid(grid: List<GridItem>) = appsManagerDataSource.setGrid(grid)

    override suspend fun updateAppsList() {
        val job = scanMutex.withLock {
            val running = inFlightScan
            if (running != null && running.isActive) {
                running
            } else {
                scope.async {
                    try {
                        val apps = fetchAllApps()
                        appsManagerDataSource.setAppList(apps)
                    } finally {
                        scanMutex.withLock {
                            inFlightScan = null
                        }
                    }
                }.also { inFlightScan = it }
            }
        }
        job.await()
    }

    override fun uninstallApp(app: App) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private suspend fun fetchAllApps(): List<App> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(MATCH_ALL.toLong())
                )
            } else {
                packageManager.queryIntentActivities(intent, GET_META_DATA)
            }
        }.getOrDefault(emptyList())

        return resolveInfoList
            .filterNot { it.activityInfo.packageName == context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { resolveInfo ->
                runCatching {
                    val appInfo = resolveInfo.applicationInfo ?: return@mapNotNull null
                    val appName = resolveInfo.appName.takeIf { it.isNotBlank() } ?: resolveInfo.packageName
                    App(
                        name = appName,
                        packageName = resolveInfo.packageName,
                        icon = appIconManager.getIcon(resolveInfo.packageName),
                        isSystemApp = resolveInfo.isSystemApp
                    )
                }.getOrNull()
            }
    }

    private val ResolveInfo.packageName get() = activityInfo.packageName
    private val ResolveInfo.applicationInfo: ApplicationInfo?
        get() = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                packageManager.getApplicationInfo(packageName, GET_META_DATA)
            }
        }.getOrNull()

    private val ResolveInfo.appName: String
        get() = applicationInfo?.let {
            runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull()
        }.orEmpty()

    private val ResolveInfo.isSystemApp: Boolean
        get() = (applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
}