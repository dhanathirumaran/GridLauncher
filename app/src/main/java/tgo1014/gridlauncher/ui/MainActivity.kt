package tgo1014.gridlauncher.ui

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.CompositionLocalProvider
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost
import tgo1014.gridlauncher.domain.ThemeRepository
import tgo1014.gridlauncher.domain.usecases.UpdateAppListUseCase
import tgo1014.gridlauncher.ui.composables.LocalAppWidgetHost
import tgo1014.gridlauncher.ui.composables.LocalAppWidgetManager
import tgo1014.gridlauncher.ui.home.HomeScreen
import tgo1014.gridlauncher.ui.home.HomeScreenViewModel
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var updateAppListUseCase: UpdateAppListUseCase

    @Inject
    lateinit var appWidgetHost: LauncherAppWidgetHost

    @Inject
    lateinit var appWidgetManager: AppWidgetManager

    private val homeScreenViewModel: HomeScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(
                LocalAppWidgetHost provides appWidgetHost,
                LocalAppWidgetManager provides appWidgetManager,
            ) {
                SetupStatusBarIconsColorEffect()
                val mainColor by themeRepository.currentTheme.collectAsState()
                GridLauncherTheme(mainColor) {
                    HomeScreen(
                        viewModel = homeScreenViewModel,
                        onWidgetSelected = { info -> handleWidgetSelected(info) }
                    )
                }
            }
        }
        askToBeDefaultLauncher()
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateAppListUseCase() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            val alreadyOnHome = intent.flags and
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            if (!alreadyOnHome) {
                homeScreenViewModel.onGoToHome()
            }
        }
    }

    private fun askToBeDefaultLauncher() {
        if (isDefaultLauncher()) return
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    private fun isDefaultLauncher(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            val componentName = ComponentName(this, MainActivity::class.java)
            val intentFilter = IntentFilter(Intent.ACTION_MAIN)
            intentFilter.addCategory(Intent.CATEGORY_HOME)
            val activities = ArrayList<ComponentName>()
            // has to be mutableList of it crashes
            packageManager.getPreferredActivities(mutableListOf(intentFilter), activities, null)
            activities.contains(componentName)
        }
    }

    @Composable
    private fun SetupStatusBarIconsColorEffect() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // TODO figure this for older versions where WallpaperManager is not available
            enableEdgeToEdge()
            return
        }
        var useDarkIcons by remember { mutableStateOf(false) }
        val context = LocalContext.current
        LaunchedEffect(useDarkIcons) {
            // For some reason these two lines are needed
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            val isDarkIcons = (getWallpaperColor(context)?.luminance() ?: 0f) <= 0.5f
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { isDarkIcons },
                navigationBarStyle = SystemBarStyle.auto(
                    lightScrim,
                    darkScrim,
                ) { isDarkIcons },
            )
        }
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                useDarkIcons = (getWallpaperColor(context)?.luminance() ?: 0f) <= 0.5f
                WallpaperManager.getInstance(context)
                    .addOnColorsChangedListener(
                        (WallpaperManager.OnColorsChangedListener { colors, _ ->
                            useDarkIcons = (colors?.primaryColor?.luminance() ?: 0f) > 0.5f
                        }),
                        Handler(Looper.getMainLooper())
                    )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun getWallpaperColor(context: Context): android.graphics.Color? {
        return WallpaperManager.getInstance(context)
            .getWallpaperColors(FLAG_SYSTEM)
            ?.primaryColor
    }

    /**
     * The default light scrim, as defined by androidx and the platform:
     * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=35-38;drc=27e7d52e8604a080133e8b842db10c89b4482598
     */
    private val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

    /**
     * The default dark scrim, as defined by androidx and the platform:
     * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=40-44;drc=27e7d52e8604a080133e8b842db10c89b4482598
     */
    private val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingWidgetId: Int = -1

    private fun handleWidgetSelected(info: AppWidgetProviderInfo) {
        try {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            pendingWidgetId = appWidgetId
            pendingWidgetInfo = info

            val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            if (canBind) {
                checkConfigureAndAddWidget(appWidgetId, info)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                }
                startActivityForResult(bindIntent, REQUEST_BIND_APPWIDGET)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (pendingWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = -1
                pendingWidgetInfo = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND_APPWIDGET -> {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                    ?.takeIf { it != -1 } ?: pendingWidgetId
                if (resultCode == RESULT_OK && appWidgetId != -1) {
                    val info = pendingWidgetInfo ?: appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null) {
                        checkConfigureAndAddWidget(appWidgetId, info)
                    } else {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                        pendingWidgetId = -1
                        pendingWidgetInfo = null
                    }
                } else {
                    if (appWidgetId != -1) {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                    }
                    pendingWidgetId = -1
                    pendingWidgetInfo = null
                }
            }
            REQUEST_CREATE_APPWIDGET -> {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                    ?.takeIf { it != -1 } ?: pendingWidgetId
                if (resultCode == RESULT_OK && appWidgetId != -1) {
                    val info = pendingWidgetInfo ?: appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null) {
                        addWidgetToGrid(appWidgetId, info)
                    }
                } else {
                    if (appWidgetId != -1) {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                    }
                }
                pendingWidgetId = -1
                pendingWidgetInfo = null
            }
        }
    }

    private fun checkConfigureAndAddWidget(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        if (appWidgetInfo.configure != null) {
            try {
                pendingWidgetId = appWidgetId
                pendingWidgetInfo = appWidgetInfo
                appWidgetHost.startAppWidgetConfigureActivityForResult(
                    this,
                    appWidgetId,
                    0,
                    REQUEST_CREATE_APPWIDGET,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                addWidgetToGrid(appWidgetId, appWidgetInfo)
                pendingWidgetId = -1
                pendingWidgetInfo = null
            }
        } else {
            addWidgetToGrid(appWidgetId, appWidgetInfo)
            pendingWidgetId = -1
            pendingWidgetInfo = null
        }
    }

    private fun addWidgetToGrid(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        val (defaultW, defaultH) = calculateWidgetSpan(appWidgetInfo)
        homeScreenViewModel.onAddWidgetToGrid(
            appWidgetId = appWidgetId,
            packageName = appWidgetInfo.provider.packageName,
            className = appWidgetInfo.provider.className,
            width = defaultW,
            height = defaultH
        )
    }

    private fun calculateWidgetSpan(info: AppWidgetProviderInfo): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
                return Pair(
                    info.targetCellWidth.coerceIn(2, Constants.gridColumns),
                    info.targetCellHeight.coerceAtLeast(1)
                )
            }
        }
        val spanX = ((info.minWidth + 30) / 70).coerceIn(2, Constants.gridColumns)
        val spanY = ((info.minHeight + 30) / 70).coerceIn(1, 4)
        return Pair(spanX, spanY)
    }

    companion object {
        private const val REQUEST_CREATE_APPWIDGET = 2002
        private const val REQUEST_BIND_APPWIDGET = 2003
    }

}