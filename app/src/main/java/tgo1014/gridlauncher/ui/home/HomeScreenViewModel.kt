package tgo1014.gridlauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.data.withoutAccents
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.SettingsRepository
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.AddToGridUseCase
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.data.calendar.CalendarEventManager
import tgo1014.gridlauncher.data.smarttile.BatteryTileManager
import tgo1014.gridlauncher.data.smarttile.CurrencyRateManager
import tgo1014.gridlauncher.data.smarttile.FlashlightManager
import tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager
import tgo1014.gridlauncher.data.smarttile.SamsungFinderHelper
import tgo1014.gridlauncher.data.smarttile.StepCounterManager
import tgo1014.gridlauncher.data.smarttile.WeatherManager
import tgo1014.gridlauncher.data.notification.NotificationRepository
import tgo1014.gridlauncher.domain.DynamicGridTransformer
import tgo1014.gridlauncher.domain.usecases.GetAppListUseCase
import tgo1014.gridlauncher.domain.usecases.ItemGridSizeChangeUseCase
import tgo1014.gridlauncher.domain.usecases.MoveGridItemUseCase
import tgo1014.gridlauncher.domain.usecases.RemoveFromGridUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.OnSystemThemeChangedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.RemoveWallpaperUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.StoreWallpaperPickedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.UpdateWallpaperBasedOnThemeUseCase
import tgo1014.gridlauncher.ui.composables.SmartTileData
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.SmartTileType
import tgo1014.gridlauncher.ui.models.TileEvent
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAppListUseCase: GetAppListUseCase,
    private val addToGridUseCase: AddToGridUseCase,
    private val moveGridItemUseCase: MoveGridItemUseCase,
    private val removeFromGridUseCase: RemoveFromGridUseCase,
    private val itemGridSizeChangeUseCase: ItemGridSizeChangeUseCase,
    private val appsManager: AppsManager,
    private val settingsRepository: SettingsRepository,
    private val storeWallpaperPickedUseCase: StoreWallpaperPickedUseCase,
    private val onRemoveWallpaperUseCase: RemoveWallpaperUseCase,
    private val onSystemThemeChangedUseCase: OnSystemThemeChangedUseCase,
    private val updateWallpaperBasedOnThemeUseCase: UpdateWallpaperBasedOnThemeUseCase,
    private val flashlightManager: FlashlightManager,
    private val batteryTileManager: BatteryTileManager,
    private val stepCounterManager: StepCounterManager,
    private val currencyRateManager: CurrencyRateManager,
    private val weatherManager: WeatherManager,
    private val notificationRepository: NotificationRepository,
    private val calendarEventManager: CalendarEventManager,
    private val mediaPlaybackManager: MediaPlaybackManager,
    private val alarmClockManager: tgo1014.gridlauncher.data.clock.AlarmClockManager,
) : ViewModel() {

    private var fullAppList: List<App> = emptyList()

    val notificationsFlow = notificationRepository.notifications

    private val _stateFlow = MutableStateFlow(HomeState())
    val stateFlow = combine(_stateFlow, settingsRepository.tileSettingsFlow) { state, settings ->
        state.copy(tileSettings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val smartTileData = combine(
        flashlightManager.isFlashlightOn,
        flashlightManager.isAvailable,
        stepCounterManager.stepCounterState,
        batteryTileManager.state,
        currencyRateManager.currencyState
    ) { torch, torchAvailable, steps, battery, currency ->
        SmartTileData(
            flashlightOn = torch,
            flashlightAvailable = torchAvailable,
            stepCounterState = steps,
            batteryState = battery,
            currencyState = currency
        )
    }.combine(weatherManager.weatherState) { data, weather ->
        data.copy(weatherState = weather)
    }.combine(calendarEventManager.events) { data, events ->
        data.copy(calendarEvents = events)
    }.combine(calendarEventManager.hasPermission) { data, hasPerm ->
        data.copy(isCalendarPermissionGranted = hasPerm)
    }.combine(mediaPlaybackManager.mediaState) { data, media ->
        data.copy(mediaPlaybackState = media)
    }.combine(alarmClockManager.nextAlarm) { data, alarm ->
        data.copy(nextAlarm = alarm)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartTileData())

    init {
        init()
    }

    fun onGoToHome() {
        _stateFlow.update { it.copy(goToHome = true, itemBeingEdited = null) }
    }

    fun onOpenApp(app: App) {
        viewModelScope.launch {
            delay(200)
            _stateFlow.update { it.copy(goToHome = true) }
            resetState()
        }
        viewModelScope.launch { appsManager.openApp(app) }
    }

    fun onGridItemClicked(gridItem: GridItem) = viewModelScope.launch {
        if (gridItem.id == DynamicGridTransformer.MEDIA_TILE_ID) {
            MediaPlaybackManager.openPlayerApp(context)
            return@launch
        }
        when {
            !_stateFlow.value.isEditMode -> {
                when (gridItem.itemType) {
                    GridItemType.APP -> {
                        onOpenApp(gridItem.app)
                    }
                    GridItemType.SHORTCUT -> {
                        delay(200)
                        _stateFlow.update { it.copy(goToHome = true) }
                        resetState()
                        appsManager.openShortcut(gridItem)
                    }
                    GridItemType.SMART_TILE -> handleSmartTileClick(gridItem)
                    else -> {}
                }
            }
            else -> _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
        }
    }

    private fun handleSmartTileClick(gridItem: GridItem) {
        when (gridItem.smartTileType) {
            SmartTileType.FINDER -> SamsungFinderHelper.launch(context)
            SmartTileType.FLASHLIGHT -> {
                if (!flashlightManager.isAvailable.value) {
                    Toast.makeText(context, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
                } else {
                    flashlightManager.toggle()
                }
            }
            @Suppress("DEPRECATION")
            SmartTileType.LOCK_SCREEN -> {
                // Deprecated: AccessibilityService was removed to ensure full compatibility
                // with banking and UPI apps (e.g. BHIM, Google Pay, PhonePe).
            }
            SmartTileType.STEP_COUNTER -> {
                stepCounterManager.startListening()
                if (!stepCounterManager.stepCounterState.value.hasPermission) {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Please grant Physical Activity permission for Step Counter", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                val healthIntent = context.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
                    ?: context.packageManager.getLaunchIntentForPackage("com.google.android.apps.fitness")
                if (healthIntent != null) {
                    healthIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(healthIntent)
                }
            }
            SmartTileType.BATTERY -> {
                batteryTileManager.updateBattery()
                runCatching {
                    val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            SmartTileType.CURRENCY -> {
                viewModelScope.launch { currencyRateManager.fetchRateIfNeeded(force = true) }
            }
            SmartTileType.WEATHER -> {
                viewModelScope.launch { weatherManager.fetchWeather(force = true) }
            }
            SmartTileType.CALENDAR -> {
                if (!calendarEventManager.hasPermission()) {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Please grant Calendar permission", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                openCalendarApp()
            }
            SmartTileType.MEDIA_PLAYER -> {
                MediaPlaybackManager.openPlayerApp(context)
            }
            null -> {}
        }
    }

    fun openCalendarApp() {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.recoverCatching {
            val calendarIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                ?: context.packageManager.getLaunchIntentForPackage("com.samsung.android.calendar")
            if (calendarIntent != null) {
                calendarIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(calendarIntent)
            }
        }
    }

    fun onGridItemLongClicked(gridItem: GridItem) {
        if (gridItem.id == DynamicGridTransformer.MEDIA_TILE_ID) return
        _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
    }

    fun onSwitchedToHome() {
        _stateFlow.update { it.copy(goToHome = false) }
        calendarEventManager.refresh()
    }

    fun onAddToGrid(app: App) = viewModelScope.launch {
        addToGridUseCase(app)
    }

    fun uninstallApp(app: App) {
        appsManager.uninstallApp(app)
    }

    fun onFilterTextChanged(filter: String) {
        if (filter.isBlank()) {
            onFilterCleared()
            return
        }
        val appList = fullAppList.filter {
            it.name.withoutAccents.contains(filter.withoutAccents.trim(), true)
        }
        _stateFlow.update { it.copy(filterString = filter, appList = appList) }
    }

    fun onFilterCleared() {
        _stateFlow.update { it.copy(filterString = "", appList = fullAppList) }
    }

    fun isDefaultLauncher(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
        } else {
            val componentName = ComponentName(context, MainActivity::class.java)
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val activities = ArrayList<ComponentName>()
            context.packageManager.getPreferredActivities(mutableListOf(intentFilter), activities, null)
            activities.contains(componentName)
        }
    }

    fun onSettingsEvent(event: SettingsEvent) = viewModelScope.launch {
        when (event) {
            SettingsEvent.OnSettingsIconClicked -> _stateFlow.update {
                it.copy(
                    isSettingsSheetShowing = true,
                    isDefaultLauncher = isDefaultLauncher(),
                    isNotificationAccessGranted = notificationRepository.isAccessGranted(context),
                    isCalendarPermissionGranted = calendarEventManager.hasPermission()
                )
            }

            SettingsEvent.OnSettingsSheetDismissed -> _stateFlow.update {
                it.copy(
                    isSettingsSheetShowing = false
                )
            }

            is SettingsEvent.OnSettingsUpdated -> settingsRepository.updateSettings(event.tileSettings)
            is SettingsEvent.OnWallpaperPicked -> storeWallpaperPickedUseCase(event.uri)
            SettingsEvent.OnWallpaperRemoved -> onRemoveWallpaperUseCase()
            SettingsEvent.OnOpenDefaultLauncherSettings -> {
                runCatching {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            SettingsEvent.OnOpenNotificationAccessSettings -> {
                runCatching {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            SettingsEvent.OnCalendarPermissionGranted -> {
                calendarEventManager.refresh()
                _stateFlow.update { it.copy(isCalendarPermissionGranted = true) }
            }
            SettingsEvent.OnOpenAppSettings -> {
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    fun onTileEvent(event: TileEvent) = viewModelScope.launch {
        when (event) {
            is TileEvent.OnTilePositionChanged -> {
                if (event.itemId == DynamicGridTransformer.MEDIA_TILE_ID) return@launch
                val isMediaActive = mediaPlaybackManager.mediaState.value.isActive
                val baseGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()
                val targetY = DynamicGridTransformer.toBaseGridY(event.targetY, baseGrid, isMediaActive)
                moveGridItemUseCase(event.itemId, event.targetX, targetY)
            }
            is TileEvent.OnTileDimensionChanged -> {
                if (event.itemId == DynamicGridTransformer.MEDIA_TILE_ID) return@launch
                itemGridSizeChangeUseCase(event.itemId, event.newWidth, event.newHeight)
            }
            is TileEvent.OnShortcutRemoved -> {
                if (event.item.id == DynamicGridTransformer.MEDIA_TILE_ID) return@launch
                removeFromGridUseCase(event.item).onSuccess {
                    _stateFlow.update { it.copy(itemBeingEdited = null) }
                }
            }
            is TileEvent.OnAppUninstalled -> {
                if (event.item.id == DynamicGridTransformer.MEDIA_TILE_ID) return@launch
                uninstallApp(event.item.app)
                _stateFlow.update { it.copy(itemBeingEdited = null) }
            }
            TileEvent.OnDismissEditMode -> {
                _stateFlow.update { it.copy(itemBeingEdited = null) }
            }
            is TileEvent.OnTileMoved -> {
                val item = _stateFlow.value.itemBeingEdited ?: return@launch
                moveGridItemUseCase(item.id, event.direction)
            }
            is TileEvent.OnSizeChange -> {
                val item = _stateFlow.value.itemBeingEdited ?: return@launch
                itemGridSizeChangeUseCase(item.id, event.tileSize)
            }
            TileEvent.OnTileSettingsSheetDismissed -> _stateFlow.update { it.copy(itemBeingEdited = null) }
            TileEvent.OnRemoveClicked -> {
                val item = _stateFlow.value.itemBeingEdited ?: return@launch
                removeFromGridUseCase(item).onSuccess {
                    _stateFlow.update { it.copy(itemBeingEdited = null) }
                }
            }
        }
    }

    private fun init() = viewModelScope.launch {
        ensureBlurredWallpaper()
        observeSystemTheme()
        getAppListUseCase()
            .onEach { appList ->
                fullAppList = appList
                _stateFlow.update { it.copy(appList = appList) }
            }
            .launchIn(this)
        combine(
            appsManager.homeGridFlow,
            mediaPlaybackManager.mediaState
        ) { baseGrid, media ->
            DynamicGridTransformer.computeEffectiveGrid(baseGrid, media.isActive)
        }
            .onEach { grid -> _stateFlow.update { it.copy(grid = grid) } }
            .launchIn(this)
    }

    private fun ensureBlurredWallpaper() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            updateWallpaperBasedOnThemeUseCase()
        }
    }

    private fun observeSystemTheme() {
        onSystemThemeChangedUseCase()
            .onEach { updateWallpaperBasedOnThemeUseCase() }
            .launchIn(viewModelScope)
    }

    private fun resetState() {
        _stateFlow.update { it.copy(itemBeingEdited = null) }
        onFilterCleared()
    }

}