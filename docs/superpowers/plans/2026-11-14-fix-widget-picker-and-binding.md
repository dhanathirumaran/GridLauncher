# Widget Picker & Binding Permission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unstable legacy `ACTION_APPWIDGET_PICK` system activity with a robust built-in Jetpack Compose widget picker bottom sheet, and implement the proper Android `bindAppWidgetIdIfAllowed` + `ACTION_APPWIDGET_BIND` permission flow to prompt the user with the standard "Allow [GridLauncher] to create widgets and access their data" dialog before configuring and pinning widgets.

**Architecture:**
1. A dedicated Compose `WidgetPickerBottomSheet` lists all installed widgets directly from `AppWidgetManager.getInstalledProviders()`, organized by app with their icons, labels, and cell span dimensions.
2. In `MainActivity`, when a widget provider is selected:
   - Allocate `appWidgetId` via `appWidgetHost.allocateAppWidgetId()`.
   - Call `appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)`.
   - If not allowed, launch `Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)` via `startActivityForResult(..., REQUEST_BIND_APPWIDGET)` to trigger the Android system authorization prompt.
   - Once bound (either directly or via `onActivityResult`), launch the widget's configure activity if present (`providerInfo.configure != null`), or immediately place the widget on the grid.
   - If canceled or denied at any point, safely delete the allocated `appWidgetId`.
3. In `StorageModule`, configure `Json` to ignore unknown keys, coerce default values, and encode defaults to ensure reliable DataStore serialization of `GridItem` containing widget data.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `AppWidgetHost` / `AppWidgetManager`, Kotlinx Serialization, AndroidX DataStore, Hilt.

**Spec:** User request:
> "if i click + icon wigets are showing but its showing for a second but it disappears why also it always ask allow to app to show widget that also not came /plan"

---

## Global Constraints
- Target SDK 35, Min SDK 26, Kotlin 2.0+, Jetpack Compose with Material 3.
- No third-party dependencies outside the existing project dependencies (`dagger-hilt`, `compose`, `datastore`, `kotlinx-serialization`).
- Preserve existing grid tiling behavior, 2x1 tiles, auto-adding installed apps, and single-screen launcher structure.
- **CRITICAL CONSTRAINT:** Do not run local Gradle builds unless explicitly asked by the user (`dont build`).

---

## Review Focus
1. **Unprivileged Host Binding Failure:** `bindAppWidgetIdIfAllowed` returns `false` on non-system launchers until the user explicitly accepts the `ACTION_APPWIDGET_BIND` dialog.
2. **Widget Picker Crashing / Instant Dismissal:** The legacy `ACTION_APPWIDGET_PICK` activity in `com.android.settings` fails or dismisses itself when invoked without specific extras or on modern Android versions (12+).
3. **Widget Configuration Activity Handling:** Widgets that require configuration (e.g. Spotify, Google Tasks) must launch their configure activity (`appWidgetHost.startAppWidgetConfigureActivityForResult`), and only be added to the grid if the configure activity returns `RESULT_OK`.
4. **ID Leak on Cancellation:** If the user dismisses the picker, cancels the permission dialog, or cancels the configure activity, the allocated `appWidgetId` must be deleted via `appWidgetHost.deleteAppWidgetId(id)` to prevent zombie widget IDs.
5. **DataStore Serialization Integrity:** Deserializing stored `GridItem` instances containing widget IDs, packages, and types must not crash or reset the home grid.

---

### Task 1: Robust Json Serialization Configuration in StorageModule

**Files:**
- Modify: `app/src/main/java/tgo1014/gridlauncher/app/injection/StorageModule.kt:50-60`
- Test: `app/src/test/java/tgo1014/gridlauncher/domain/AddWidgetToGridUseCaseTest.kt`

**Interfaces:**
- Produces: `Json` instance with `ignoreUnknownKeys = true`, `encodeDefaults = true`, `coerceInputValues = true`, `isLenient = true`.

- [ ] **Step 1: Check existing `StorageModule.kt` implementation**

Verify line 56:
```kotlin
@Provides
@Singleton
fun providesJson(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}
```

- [ ] **Step 2: Update `StorageModule.kt`**

Replace `Json` factory call in `StorageModule.kt` to configure resilient JSON parsing flags so future or legacy schema variations in `GridItem` never trigger decoding exceptions.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tgo1014/gridlauncher/app/injection/StorageModule.kt
git commit -m "Configure Json with ignoreUnknownKeys and encodeDefaults in StorageModule"
```

---

### Task 2: Built-in Widget Picker Bottom Sheet (`WidgetPickerBottomSheet.kt`)

**Files:**
- Create: `app/src/main/java/tgo1014/gridlauncher/ui/composables/sheets/WidgetPickerBottomSheet.kt`

**Interfaces:**
- Consumes: `AppWidgetManager.getInstalledProviders()`, `PackageManager` for labels and icons.
- Produces:
  ```kotlin
  @Composable
  fun WidgetPickerBottomSheet(
      isShowing: Boolean,
      onDismiss: () -> Unit,
      onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
  )
  ```

- [ ] **Step 1: Define Widget Provider Item Representation**

Create a data model inside or alongside the composable:
```kotlin
data class WidgetItem(
    val info: AppWidgetProviderInfo,
    val appName: String,
    val widgetLabel: String,
    val spanX: Int,
    val spanY: Int,
)
```

- [ ] **Step 2: Implement Querying and Grouping of Installed Widgets**

Query `AppWidgetManager.getInstance(context).installedProviders`, group by application name / package, and extract readable labels, span dimensions, and icons.

- [ ] **Step 3: Implement `WidgetPickerBottomSheet` Composable**

Build a Jetpack Compose `ModalBottomSheet` displaying:
- A header title "Add widget".
- A scrollable list of widgets grouped by application.
- Each widget row displays:
  - App icon (or widget preview).
  - App name and specific widget label.
  - Dimension badge (e.g. `2x1`, `2x2`, `4x2`).
- Clicking a widget calls `onWidgetSelected(item.info)` and dismisses the sheet.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tgo1014/gridlauncher/ui/composables/sheets/WidgetPickerBottomSheet.kt
git commit -m "Add WidgetPickerBottomSheet to select installed widgets"
```

---

### Task 3: Integrate Widget Picker State into `HomeScreenViewModel`, `GridScreen`, and `HomeScreen`

**Files:**
- Modify: `app/src/main/java/tgo1014/gridlauncher/ui/home/HomeState.kt`
- Modify: `app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreenViewModel.kt`
- Modify: `app/src/main/java/tgo1014/gridlauncher/ui/home/GridScreen.kt`
- Modify: `app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `isWidgetPickerShowing: Boolean` in `HomeState`.
- Produces: `onShowWidgetPicker()`, `onDismissWidgetPicker()`, and `onWidgetSelected(info: AppWidgetProviderInfo)`.

- [ ] **Step 1: Update `HomeState.kt`**

Add `val isWidgetPickerShowing: Boolean = false` to `HomeState`.

- [ ] **Step 2: Update `HomeScreenViewModel.kt`**

Add helper methods:
```kotlin
fun showWidgetPicker() {
    _stateFlow.update { it.copy(isWidgetPickerShowing = true) }
}

fun dismissWidgetPicker() {
    _stateFlow.update { it.copy(isWidgetPickerShowing = false) }
}
```

- [ ] **Step 3: Update `GridScreen.kt` and `HomeScreen.kt`**

Include `WidgetPickerBottomSheet` in `GridScreenScreen`:
```kotlin
WidgetPickerBottomSheet(
    isShowing = state.isWidgetPickerShowing,
    onDismiss = onDismissWidgetPicker,
    onWidgetSelected = { info ->
        onDismissWidgetPicker()
        onWidgetSelected(info)
    }
)
```
Pass the `+` icon click to `onShowWidgetPicker()`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tgo1014/gridlauncher/ui/home/
git commit -m "Connect WidgetPickerBottomSheet to HomeState and HomeScreen"
```

---

### Task 4: Implement Explicit Permission & Binding Flow in `MainActivity`

**Files:**
- Modify: `app/src/main/java/tgo1014/gridlauncher/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `onWidgetSelected: (AppWidgetProviderInfo) -> Unit` from `HomeScreen`.
- Produces:
  - `bindAppWidgetIdIfAllowed` check.
  - `REQUEST_BIND_APPWIDGET` handling for `AppWidgetManager.ACTION_APPWIDGET_BIND`.
  - `REQUEST_CREATE_APPWIDGET` handling for `startAppWidgetConfigureActivityForResult`.
  - Automatic fallback widget span calculation.

- [ ] **Step 1: Add Request Code Constants**

```kotlin
companion object {
    private const val REQUEST_BIND_APPWIDGET = 2003
    private const val REQUEST_CREATE_APPWIDGET = 2002
}
```

- [ ] **Step 2: Implement `handleWidgetSelected(info: AppWidgetProviderInfo)`**

```kotlin
private var pendingWidgetInfo: AppWidgetProviderInfo? = null

private fun handleWidgetSelected(info: AppWidgetProviderInfo) {
    try {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (canBind) {
            checkConfigureAndAddWidget(appWidgetId, info)
        } else {
            pendingWidgetInfo = info
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            startActivityForResult(bindIntent, REQUEST_BIND_APPWIDGET)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

- [ ] **Step 3: Update `onActivityResult`**

Handle `REQUEST_BIND_APPWIDGET`:
```kotlin
REQUEST_BIND_APPWIDGET -> {
    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
    if (resultCode == RESULT_OK && appWidgetId != -1) {
        val info = pendingWidgetInfo ?: appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info != null) {
            checkConfigureAndAddWidget(appWidgetId, info)
        }
    } else {
        if (appWidgetId != -1) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }
    pendingWidgetInfo = null
}
```

Handle `REQUEST_CREATE_APPWIDGET`:
```kotlin
REQUEST_CREATE_APPWIDGET -> {
    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
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
    pendingWidgetInfo = null
}
```

- [ ] **Step 4: Connect `onWidgetSelected` to `MainActivity`**

Pass `onWidgetSelected = ::handleWidgetSelected` from `MainActivity` into `HomeScreen`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tgo1014/gridlauncher/ui/MainActivity.kt
git commit -m "Implement bindAppWidgetIdIfAllowed and ACTION_APPWIDGET_BIND permission flow"
```
