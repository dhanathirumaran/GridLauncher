package tgo1014.gridlauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
import tgo1014.gridlauncher.domain.usecases.GetAppListUseCase
import tgo1014.gridlauncher.domain.usecases.ItemGridSizeChangeUseCase
import tgo1014.gridlauncher.domain.usecases.MoveGridItemUseCase
import tgo1014.gridlauncher.domain.usecases.OpenNotificationShadeUseCase
import tgo1014.gridlauncher.domain.usecases.RemoveFromGridUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.OnSystemThemeChangedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.RemoveWallpaperUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.StoreWallpaperPickedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.UpdateWallpaperBasedOnThemeUseCase
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val getAppListUseCase: GetAppListUseCase,
    private val openNotificationShadeUseCase: OpenNotificationShadeUseCase,
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
) : ViewModel() {

    private var fullAppList: List<App> = emptyList()

    private val _stateFlow = MutableStateFlow(HomeState())
    val stateFlow = combine(_stateFlow, settingsRepository.tileSettingsFlow) { state, settings ->
        state.copy(tileSettings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        init()
    }

    fun onGoToHome() {
        _stateFlow.update { it.copy(goToHome = true) }
    }

    fun onOpenApp(app: App) {
        viewModelScope.launch {
            delay(200)
            _stateFlow.update { it.copy(goToHome = true, closeSearchFab = true) }
            resetState()
        }
        viewModelScope.launch { appsManager.openApp(app) }
    }

    fun onGridItemClicked(gridItem: GridItem) = viewModelScope.launch {
        when {
            !_stateFlow.value.isEditMode -> {
                if (gridItem.itemType == GridItemType.APP) {
                    onOpenApp(gridItem.app)
                } else if (gridItem.itemType == GridItemType.SHORTCUT) {
                    delay(200)
                    _stateFlow.update { it.copy(goToHome = true, closeSearchFab = true) }
                    resetState()
                    appsManager.openShortcut(gridItem)
                }
            }
            else -> _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
        }
    }

    fun onGridItemLongClicked(gridItem: GridItem) {
        _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
    }

    fun openNotificationShade() {
        openNotificationShadeUseCase()
    }

    fun onSwitchedToHome() {
        _stateFlow.update { it.copy(goToHome = false) }
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

    fun onFabClosed() {
        _stateFlow.update { it.copy(closeSearchFab = false) }
    }

    fun onSettingsEvent(event: SettingsEvent) = viewModelScope.launch {
        when (event) {
            SettingsEvent.OnSettingsIconClicked -> _stateFlow.update {
                it.copy(
                    isSettingsSheetShowing = true
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
        }
    }

    fun onTileEvent(event: TileEvent) = viewModelScope.launch {
        when (event) {
            is TileEvent.OnTilePositionChanged -> {
                moveGridItemUseCase(event.itemId, event.targetX, event.targetY)
            }
            is TileEvent.OnTileDimensionChanged -> {
                itemGridSizeChangeUseCase(event.itemId, event.newWidth, event.newHeight)
            }
            is TileEvent.OnShortcutRemoved -> {
                removeFromGridUseCase(event.item).onSuccess {
                    _stateFlow.update { it.copy(itemBeingEdited = null) }
                }
            }
            is TileEvent.OnAppUninstalled -> {
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
        observeSystemTheme()
        getAppListUseCase()
            .onEach { appList ->
                fullAppList = appList
                _stateFlow.update { it.copy(appList = appList) }
            }
            .launchIn(this)
        appsManager.homeGridFlow
            .onEach { grid -> _stateFlow.update { it.copy(grid = grid) } }
            .launchIn(this)
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