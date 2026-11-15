# GridLauncher - Code Review Analysis & Technical Fix Plan

This document details the code review findings, architectural audits, and technical fix specifications for [dhanathirumaran/GridLauncher](https://github.com/dhanathirumaran/GridLauncher).

---

## 1. Executive Summary: Bug Origin Breakdown

The issues identified across the codebase fall into two distinct categories:

1. **Early Architectural Bugs:** Flaws present in the foundational grid engine, original sensor managers, backup rules, and system intents.
2. **New Feature Bugs:** Regressions and edge cases introduced during recent additions (Dynamic $8\times 2$ Live Music Smart Tile, Apple-style Calendar & Missed Call Live Tiles, and Live Notification badges).

| ID | Issue Description | Origin | Impact | Status |
| :--- | :--- | :--- | :--- | :--- |
| **B01** | **Layout corruption while media plays** | **New** (Media Tile) | Critical | Pending Fix |
| **B02** | **Media tile overlaps straddling tiles** | **New** (Media Tile) | High | Pending Fix |
| **B03** | **Media tile selectable in edit mode** | **New** (Media Tile) | Medium | Pending Fix |
| **B04** | **Wide Calendar tile hijacks app launch** | **New** (Calendar Tile) | High | Pending Fix |
| **B05** | **All-day events on wrong day in UTC+ zones** | **New** (Calendar Tile) | High | Pending Fix |
| **B06** | **"Missed calls" parsed as caller name** | **New** (Missed Call Tile) | Medium | Pending Fix |
| **B07** | **Any dialer notification triggers missed-call layout** | **New** (Missed Call Tile) | High | Pending Fix |
| **B08** | **Media listener never starts if permission granted post-launch** | **New** (Media Tile) | High | Pending Fix |
| **B09** | **Weather condition icons and codes mismatch** | **Early** (Smart Tile) | Medium | Pending Fix |
| **B10** | **Fragile weather network fetching & concurrency** | **Early** (Weather Manager) | Medium | Pending Fix |
| **B11** | **Step counter false "No Sensor" & missing midnight rollover** | **Early** (Step Counter) | High | Pending Fix |
| **B12** | **Stale calendar events & hardcoded 12h time** | **New** (Calendar Tile) | Medium | Pending Fix |
| **B13** | **Notification counts double & unthrottled sync** | **New** (Live Notifications) | High | Pending Fix |
| **B14** | **UpdateAppListUseCase marks setup done on failure** | **Early** (Grid Engine) | High | Pending Fix |
| **B15** | **Cloud backup restore drops smart tiles** | **Early** (Backup Config) | High | Pending Fix |
| **B16** | **Media player progress bar remains stationary** | **New** (Media Tile) | Medium | Pending Fix |
| **B17** | **Placeholder Search icon used for media & weather** | **New & Early** | Low | Pending Fix |
| **B18** | **Full grid recomposition on any notification update** | **New** (Live Notifications) | Medium | Pending Fix |
| **B19** | **Default launcher prompt marked asked before launch** | **Early** (MainActivity) | Medium | Pending Fix |

---

## 2. Deep Dive: New Feature Bugs (Recent Regressions)

### B01. Layout Corruption While Media is Playing
- **Origin:** **New** (Introduced with Dynamic $8\times 2$ Live Music Smart Tile).
- **Files:** [`DynamicGridTransformer.kt`](app/src/main/java/tgo1014/gridlauncher/domain/DynamicGridTransformer.kt), [`HomeScreenViewModel.kt`](app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreenViewModel.kt), [`TileLayout.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/TileLayout.kt)
- **Root Cause:**
  `DynamicGridTransformer.computeEffectiveGrid` adds `+2` to the `y` coordinate of every item below row 0 in memory for UI presentation. However, when the user drags, resizes, or removes a tile, `TileLayout` emits `targetY` from the *shifted* visual table. `HomeScreenViewModel.onTileEvent` passes this shifted `targetY` directly into `MoveTileUseCase`, `ResizeTileUseCase`, and `DeleteTileUseCase`. The database is permanently updated with shifted coordinates. When music playback ends, the tiles remain displaced by 2 rows or overwrite existing tiles.
- **Fix Steps:**
  1. In `HomeScreenViewModel`, map shifted coordinates back to base grid space before calling domain use cases:
     ```kotlin
     val unshiftedY = if (isMediaActive && targetY >= insertionY + 2) targetY - 2 else targetY
     ```
  2. Prevent any interaction with `MEDIA_TILE_ID` (`-999`) across move, resize, and remove events.
  3. Alternatively, lock tile repositioning in `TileLayout` while the ephemeral dynamic media tile is active.

### B02. Media Tile Overlaps Tiles that Straddle Insertion Seam
- **Origin:** **New** (Introduced with Dynamic $8\times 2$ Live Music Smart Tile).
- **File:** [`DynamicGridTransformer.kt`](app/src/main/java/tgo1014/gridlauncher/domain/DynamicGridTransformer.kt)
- **Root Cause:**
  `insertionY` is calculated as `row0Items.maxOfOrNull { it.y + it.height } ?: 1`. Only items with `y >= insertionY` are shifted down. If an item starts at $y=1$ and has height 2 (occupying $y \in [1, 2]$), and `insertionY = 2`, its `it.y < insertionY` condition puts it in `topItems`. The media tile inserts at $y=2$ with height 2 ($y \in [2, 3]$), overlapping row 2 of that item.
- **Fix Steps:**
  1. Find a clean horizontal cut line where no existing tile straddles the seam:
     ```kotlin
     var insertionY = 1
     while (baseGrid.any { it.y < insertionY && it.y + it.height > insertionY }) {
         insertionY++
     }
     ```
  2. Shift all items with `it.y >= insertionY` down by 2 rows.

### B03. Media Tile Selectable in Edit Mode
- **Origin:** **New** (Introduced with Dynamic $8\times 2$ Live Music Smart Tile).
- **Files:** [`HomeScreenViewModel.kt`](app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreenViewModel.kt), [`TileLayout.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/TileLayout.kt)
- **Root Cause:**
  `onGridItemLongClicked` correctly guards against `item.id == -999`, but `onGridItemClicked` in edit mode executes `_stateFlow.update { it.copy(itemBeingEdited = gridItem) }` unconditionally. Tapping the media tile enters edit mode on ID `-999`, displaying resize handles and delete buttons.
- **Fix Steps:**
  1. In `onGridItemClicked`, add `if (gridItem.id == DynamicGridTransformer.MEDIA_TILE_ID) return@launch`.
  2. In `TileLayout.kt`, suppress resize handles, uninstall buttons, and touch gestures if `tileItem.id == DynamicGridTransformer.MEDIA_TILE_ID`.

### B04. Wide Calendar Tile Hijacks Regular App Launch
- **Origin:** **New** (Introduced with Apple-style Calendar Tile).
- **Files:** [`HomeScreenViewModel.kt`](app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreenViewModel.kt), [`CalendarEventManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/calendar/CalendarEventManager.kt)
- **Root Cause:**
  `HomeScreenViewModel.onGridItemClicked` checks if an app tile is $\ge 4\times 2$ and `isCalendarApp(pkg)` without calendar permission, opening `ACTION_APPLICATION_DETAILS_SETTINGS` instead of launching the app. Furthermore, `isCalendarApp` uses `pkg.contains("calendar")`, intercepting any third-party app with "calendar" in its package name.
- **Fix Steps:**
  1. Always launch the app when an `APP` tile is tapped (`onOpenApp(gridItem.app)`). Never redirect regular app icons to settings.
  2. Only prompt for permissions when tapping the dedicated `SMART_TILE` (type `CALENDAR`).
  3. Replace loose `pkg.contains("calendar")` with a strict match against default system calendar and known dialers/calendars via Intent resolution (`CATEGORY_APP_CALENDAR`).

### B05. All-Day Events Shifted to Wrong Day in UTC+ Time Zones
- **Origin:** **New** (Introduced with Apple Calendar integration).
- **File:** [`CalendarEventManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/calendar/CalendarEventManager.kt)
- **Root Cause:**
  Android Calendar Provider stores all-day events (`ALL_DAY = 1`) in UTC milliseconds from midnight UTC. In UTC+ time zones (e.g. India at UTC+5:30), local midnight is 18:30 UTC of the previous day. Querying `CalendarContract.Instances` using local `startOfDay` to `endOfDay` causes all-day events for today to fall outside the window or appear on yesterday/tomorrow.
- **Fix Steps:**
  1. Expand the query window by 24 hours: `startOfDay - 86400000L` to `endOfDay + 86400000L`.
  2. For all-day events, convert `begin` into year and day-of-year using a UTC `Calendar` instance, and compare against the local current date:
     ```kotlin
     val isToday = if (allDay) {
         val eventUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = begin }
         val todayLocal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
         eventUtc.get(Calendar.YEAR) == todayLocal.get(Calendar.YEAR) &&
         eventUtc.get(Calendar.DAY_OF_YEAR) == todayLocal.get(Calendar.DAY_OF_YEAR)
     } else {
         begin in startOfDay..endOfDay || (begin <= startOfDay && end >= startOfDay)
     }
     ```

### B06. Notification Title "Missed calls" Displayed as Caller Name
- **Origin:** **New** (Introduced with Missed Call Tile).
- **File:** [`PhoneAppManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/phone/PhoneAppManager.kt)
- **Root Cause:**
  In `extractCallerName`, the condition `!title.isNullOrBlank() && titleLower != "missed call" && !titleLower.matches(Regex("\\d+ missed calls?"))` evaluates to `true` when the notification title is plural `"Missed calls"`. It therefore returns `"Missed calls"` as the caller's name.
- **Fix Steps:**
  1. Add explicit exclusion:
     ```kotlin
     if (titleLower in setOf("missed call", "missed calls") || titleLower.contains("missed call")) {
         // Fallback to text content for contact name
     }
     ```

### B07. Any Dialer Notification Triggers Missed-Call Layout
- **Origin:** **New** (Introduced with Missed Call Tile).
- **File:** [`GridTile.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/GridTile.kt)
- **Root Cause:**
  Line 137 uses `hasMissedCall = hasLiveNotifications && (isPhone || any isMissedCall)`. If the Phone app posts any notification (such as an ongoing call, voicemail, or carrier network prompt), `isPhone` causes `hasMissedCall` to evaluate to `true`.
- **Fix Steps:**
  1. Require an actual missed call flag:
     ```kotlin
     val hasMissedCall = hasLiveNotifications && liveNotifications.orEmpty().any { it.isMissedCall }
     ```

### B08. Media Listener Never Registers if Permission Granted Post-Launch
- **Origin:** **New** (Introduced with Live Media Player Tile).
- **File:** [`MediaPlaybackManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/smarttile/MediaPlaybackManager.kt)
- **Root Cause:**
  `registerListener()` is called once in `init`. If notification access is not yet granted, it throws a `SecurityException` and fails silently. When the user enables access in Settings and returns to GridLauncher, `registerListener()` is never retried until the process is restarted.
- **Fix Steps:**
  1. Add a retry flag `isListenerRegistered`.
  2. Call `registerListener()` inside `syncActiveSessions()`, `onListenerConnected()`, and `MainActivity.onResume()`.

### B12. Calendar Tile Data Goes Stale & Ignores 24h Clock
- **Origin:** **New** (Introduced with Calendar Tile).
- **File:** [`CalendarEventManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/calendar/CalendarEventManager.kt)
- **Root Cause:**
  1. Filter `if (end < now && !allDay) continue` only runs at query time. Finished events remain on screen indefinitely until manual refresh.
  2. There is no midnight rollover or `ACTION_TIME_TICK` broadcast listener.
  3. Precomputed `formattedTime` uses hardcoded `"h:mm a"`, ignoring the device's 24-hour setting.
- **Fix Steps:**
  1. Filter events dynamically in the UI or via a periodic ticker flow.
  2. Register a `BroadcastReceiver` for `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_DATE_CHANGED`, and `ACTION_TIMEZONE_CHANGED`.
  3. Format times using `android.text.format.DateFormat.getTimeFormat(context)`.

### B13. Notification Counts Double & Lack Throttling
- **Origin:** **New** (Introduced with Live Notifications).
- **File:** [`AppNotificationListenerService.kt`](app/src/main/java/tgo1014/gridlauncher/services/AppNotificationListenerService.kt)
- **Root Cause:**
  1. Notification group summary headers (`FLAG_GROUP_SUMMARY`) are counted alongside child notifications, causing messaging apps like WhatsApp/Telegram to report double counts.
  2. Every notification post/removal immediately triggers an unthrottled full sync.
  3. System alert ignore strings are hardcoded in English.
- **Fix Steps:**
  1. Check and skip group summaries:
     ```kotlin
     if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
     ```
  2. Debounce sync calls with a 100ms delay handler.
  3. Rely on `Notification.CATEGORY_MISSED_CALL` and localized checks; log exceptions instead of swallowing them.

### B16. Media Player Progress Bar Remains Stationary
- **Origin:** **New** (Introduced with Live Media Player Tile).
- **File:** [`LiveMediaTileContent.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/LiveMediaTileContent.kt)
- **Root Cause:**
  `positionMs` is updated only on `MediaController` playback state transitions. While music is actively playing, `positionMs` remains fixed at the initial snapshot without a ticker.
- **Fix Steps:**
  1. Implement a real-time elapsed ticker using `LaunchedEffect(mediaState.isPlaying, mediaState.positionMs)`:
     ```kotlin
     var currentPosition by remember(mediaState.positionMs, mediaState.isPlaying) {
         mutableLongStateOf(mediaState.positionMs)
     }
     LaunchedEffect(mediaState.isPlaying, mediaState.positionMs) {
         if (mediaState.isPlaying && mediaState.durationMs > 0) {
             val startWall = SystemClock.elapsedRealtime()
             val startPos = mediaState.positionMs
             while (isActive) {
                 delay(500)
                 currentPosition = (startPos + (SystemClock.elapsedRealtime() - startWall)).coerceAtMost(mediaState.durationMs)
             }
         }
     }
     ```

### B18. Global Tile Recomposition on Notification Change
- **Origin:** **New** (Introduced with Live Notifications).
- **Files:** [`GridTile.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/GridTile.kt), [`HomeScreen.kt`](app/src/main/java/tgo1014/gridlauncher/ui/home/HomeScreen.kt)
- **Root Cause:**
  `LocalAppNotifications` exposes a monolithic `Map<String, List<NotificationSender>>`. Because every `GridTile` observes `LocalAppNotifications.current`, a single notification update triggers a recomposition of all 30+ tiles.
- **Fix Steps:**
  1. Pass only the specific app's notification list into `GridTile` from `TileLayout`, or use `remember(pkg) { derivedStateOf { notificationsMap[pkg] } }`.

---

## 3. Deep Dive: Early Architectural Bugs

### B09. Weather Icons & Codes Mismatch
- **Origin:** **Early** (Present in initial SmartTile design).
- **Files:** [`SmartTile.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/SmartTile.kt), [`WeatherManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/smarttile/WeatherManager.kt)
- **Root Cause:**
  1. `SmartTile.kt` selects `WeatherCloud` only when `weatherCode >= 50`. Codes 1–3 ("Partly Cloudy") and 45/48 ("Foggy") incorrectly show `WeatherSun`.
  2. WMO weather codes 56, 57 (Freezing Drizzle), 66, 67 (Freezing Rain), and 85, 86 (Snow Showers) fall through to `"Sunny"`.
- **Fix Steps:**
  1. Map WMO codes correctly:
     - 0 $\rightarrow$ Clear / Sunny
     - 1, 2, 3 $\rightarrow$ Partly Cloudy (show Cloud icon)
     - 45, 48 $\rightarrow$ Foggy (show Cloud icon)
     - 51..67, 80..82 $\rightarrow$ Rain / Drizzle / Showers
     - 71..77, 85..86 $\rightarrow$ Snow Showers
     - 95..99 $\rightarrow$ Thunderstorm
  2. Replace `SmartTileIcons.Search` fallback with a dedicated weather location indicator.

### B10. Fragile Weather Network Fetching & Concurrency
- **Origin:** **Early** (WeatherManager rewrite).
- **File:** [`WeatherManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/smarttile/WeatherManager.kt)
- **Root Cause:**
  1. The 15-minute throttle check sits outside `fetchMutex.withLock`. Rapid parallel calls pass the check simultaneously.
  2. `HttpURLConnection` lacks a check on `responseCode == HTTP_OK` and lacks `conn.disconnect()` in a `finally` block.
  3. `fetchWeatherAsync()` is called in `init` before location permission is requested or granted.
- **Fix Steps:**
  1. Move the throttle check inside `fetchMutex.withLock`.
  2. Enforce HTTP 200 response check and `conn.disconnect()` in `finally`.
  3. Defer initial fetch until permissions are confirmed or cached coordinates exist.

### B11. Step Counter False "No Sensor" & Rollover Failure
- **Origin:** **Early** (StepCounterManager design).
- **File:** [`StepCounterManager.kt`](app/src/main/java/tgo1014/gridlauncher/data/smarttile/StepCounterManager.kt)
- **Root Cause:**
  1. `isSupported` is assigned `registered` (`Boolean`). If listener registration fails or has not yet completed, `isSupported` becomes `false`, falsely showing "No Sensor" on hardware with a valid step sensor.
  2. Day rollover occurs only inside `onSensorChanged`. If the user does not walk in the morning, yesterday's steps remain displayed.
  3. The sensor listener is never unregistered when the launcher is backgrounded.
- **Fix Steps:**
  1. Maintain `isSupported = (stepSensor != null)` independently of registration state.
  2. Register broadcast receivers for date/time changes to zero out steps at midnight.
  3. Unregister the sensor in `onStop` and register in `onStart`.

### B14. UpdateAppListUseCase Marks Setup Done on Failure
- **Origin:** **Early** (Grid initialization).
- **File:** [`UpdateAppListUseCase.kt`](app/src/main/java/tgo1014/gridlauncher/domain/usecases/UpdateAppListUseCase.kt)
- **Root Cause:**
  1. `SMART_TILES_INITIALIZED` and `CALENDAR_TILE_INITIALIZED` are set to `true` before `appsManager.setGrid(...)` completes. If writing to Room fails, smart tiles are never saved and never re-pinned.
  2. `GridItemType.WIDGET` tiles are stripped (`null`).
  3. `packageManager.getPackageInfo(pkg, 0)` keeps disabled or unlaunchable system apps on the grid.
- **Fix Steps:**
  1. Write SharedPreferences flags only after `appsManager.setGrid(...)` succeeds.
  2. Verify that packages have valid launcher activities via `getLaunchIntentForPackage(pkg) != null` before retaining them.
  3. Preserve widget item types.

### B15. Cloud Backup Restore Drops Smart Tiles
- **Origin:** **Early** (Backup configuration).
- **Files:** [`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml), [`UpdateAppListUseCase.kt`](app/src/main/java/tgo1014/gridlauncher/domain/usecases/UpdateAppListUseCase.kt)
- **Root Cause:**
  `SMART_TILES_PREFS.xml` is included in cloud backups. If the user restores their device and the grid database is empty or reset, `SMART_TILES_INITIALIZED` is restored as `true`, preventing initial smart tile population.
- **Fix Steps:**
  1. Add `<exclude domain="sharedpref" path="SMART_TILES_PREFS.xml" />` to backup XML configurations.
  2. In `UpdateAppListUseCase`, allow re-pinning if the current grid contains 0 smart tiles.

### B17. Placeholder Search Icon Used Across Tiles
- **Origin:** **New & Early**.
- **File:** [`SmartTile.kt`](app/src/main/java/tgo1014/gridlauncher/ui/composables/SmartTile.kt)
- **Root Cause:**
  `SmartTileIcons.Search` is used as a fallback for both `MEDIA_PLAYER` and weather `isLocationMissing`.
- **Fix Steps:**
  1. Use `SmartTileIcons.MusicNote` for `MEDIA_PLAYER`.
  2. Use a dedicated location disabled / cloud icon for weather without coordinates.

### B19. Default Launcher Prompt Marked Asked Before Attempt
- **Origin:** **Early** (MainActivity launcher prompt).
- **File:** [`MainActivity.kt`](app/src/main/java/tgo1014/gridlauncher/ui/MainActivity.kt)
- **Root Cause:**
  `HAS_ASKED_DEFAULT_LAUNCHER` is committed to SharedPreferences before calling `startActivity(Settings.ACTION_HOME_SETTINGS)`. If `startActivity` throws an exception, `runCatching` hides it and the prompt is never attempted again.
- **Fix Steps:**
  1. Set `HAS_ASKED_DEFAULT_LAUNCHER = true` only after `startActivity` succeeds.
  2. Provide fallback to `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` on Android 10+.

---

## 4. Implementation & Verification Roadmap

### Phase 1: High-Priority Dynamic Grid & Media Player Fixes (B01, B02, B03, B08, B16)
- [ ] Implement collision-free seam detection in `DynamicGridTransformer`.
- [ ] Add inverse coordinate translation in `HomeScreenViewModel.onTileEvent`.
- [ ] Add ID `-999` guards across click, long click, and drag handlers.
- [ ] Auto-retry `MediaPlaybackManager.registerListener()` on resume and notification connection.
- [ ] Add elapsed wall-clock ticker in `LiveMediaTileContent` for real-time progress bar movement.

### Phase 2: Live Notifications, Phone & Calendar Hardening (B04, B05, B06, B07, B12, B13, B18)
- [ ] Always launch calendar app on calendar app tile click.
- [ ] Fix UTC all-day event day comparison for UTC+ time zones.
- [ ] Fix "Missed calls" caller name parsing and dialer notification filtering.
- [ ] Ignore notification group summaries and debounce IPC updates.
- [ ] Scope notification observers to avoid full-grid recomposition.
- [ ] Register midnight and time-change broadcast receivers for calendar and step tiles.

### Phase 3: Sensor, Weather, Grid Storage & System Stability (B09, B10, B11, B14, B15, B17, B19)
- [ ] Fix WMO weather code mapping and move throttle inside mutex.
- [ ] Preserve `isSupported = (stepSensor != null)` in `StepCounterManager`.
- [ ] Commit setup prefs only after grid database writes succeed.
- [ ] Exclude `SMART_TILES_PREFS.xml` from cloud backups.
- [ ] Commit default launcher preference only after successful intent launch.
