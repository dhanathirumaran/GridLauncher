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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.domain.ThemeRepository
import tgo1014.gridlauncher.domain.usecases.UpdateAppListUseCase
import tgo1014.gridlauncher.ui.home.HomeScreen
import tgo1014.gridlauncher.ui.home.HomeScreenViewModel
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import tgo1014.gridlauncher.data.smarttile.StepCounterManager
import tgo1014.gridlauncher.data.smarttile.WeatherManager
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var updateAppListUseCase: UpdateAppListUseCase

    @Inject
    lateinit var stepCounterManager: StepCounterManager

    @Inject
    lateinit var weatherManager: WeatherManager

    @Inject
    lateinit var calendarEventManager: tgo1014.gridlauncher.data.calendar.CalendarEventManager

    private val homeScreenViewModel: HomeScreenViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
            stepCounterManager.startListening()
        }
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        ) {
            weatherManager.fetchWeatherAsync()
        }
        if (permissions[Manifest.permission.READ_CALENDAR] == true) {
            calendarEventManager.refresh()
        }
    }

    private val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User answered or dismissed the default launcher prompt
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetupStatusBarIconsColorEffect()
            val mainColor by themeRepository.currentTheme.collectAsState()
            GridLauncherTheme(mainColor) {
                HomeScreen(viewModel = homeScreenViewModel)
            }
        }
        askToBeDefaultLauncher()
        checkAndRequestSmartTilePermissions()
    }

    private var updateAppListJob: kotlinx.coroutines.Job? = null

    override fun onResume() {
        super.onResume()
        updateAppListJob?.cancel()
        updateAppListJob = lifecycleScope.launch { updateAppListUseCase() }
        calendarEventManager.refresh()
        tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager.sync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            homeScreenViewModel.onGoToHome()
        }
    }

    private fun askToBeDefaultLauncher() {
        if (isDefaultLauncher()) return
        val prefs = getSharedPreferences("LAUNCHER_PREFS", Context.MODE_PRIVATE)
        if (prefs.getBoolean("HAS_ASKED_DEFAULT_LAUNCHER", false)) return

        val launched = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    defaultLauncherRoleLauncher.launch(intent)
                    return@runCatching true
                }
            }
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
            true
        }.getOrDefault(false)

        if (launched) {
            prefs.edit().putBoolean("HAS_ASKED_DEFAULT_LAUNCHER", true).apply()
        }
    }

    private fun checkAndRequestSmartTilePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                stepCounterManager.startListening()
            }
        } else {
            stepCounterManager.startListening()
        }

        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            weatherManager.fetchWeatherAsync()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
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
            enableEdgeToEdge()
            return
        }
        val context = LocalContext.current
        var isWallpaperDark by remember { mutableStateOf(false) }

        DisposableEffect(context) {
            isWallpaperDark = (getWallpaperColor(context)?.luminance() ?: 0f) <= 0.5f
            val wm = WallpaperManager.getInstance(context)
            val listener = WallpaperManager.OnColorsChangedListener { colors, _ ->
                isWallpaperDark = (colors?.primaryColor?.luminance() ?: 0f) <= 0.5f
            }
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            onDispose {
                wm.removeOnColorsChangedListener(listener)
            }
        }

        LaunchedEffect(isWallpaperDark) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { isWallpaperDark },
                navigationBarStyle = SystemBarStyle.auto(
                    lightScrim,
                    darkScrim,
                ) { isWallpaperDark },
            )
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

}