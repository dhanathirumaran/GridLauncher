package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.LazyTableScrollDirection
import eu.wewox.lazytable.LazyTableState
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.rememberLazyTableState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.app.Constants.gridColumns
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.modifyIf
import tgo1014.gridlauncher.ui.theme.plus

@Composable
fun TileLayout(
    grid: List<GridItem>,
    modifier: Modifier = Modifier,
    hazeState: HazeState = remember { HazeState() },
    columns: Int = gridColumns,
    tileSettings: TileSettings = TileSettings(),
    itemBeingEdited: GridItem? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isOnTop: (Boolean) -> Unit = {},
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
    onTilePositionChanged: (itemId: Int, targetX: Int, targetY: Int) -> Unit = { _, _, _ -> },
    onTileDimensionChanged: (itemId: Int, newWidth: Int, newHeight: Int) -> Unit = { _, _, _ -> },
    onShortcutRemoved: (item: GridItem) -> Unit = {},
    onAppUninstalled: (item: GridItem) -> Unit = {},
    onDismissEditMode: () -> Unit = {},
    footer: @Composable (Modifier) -> Unit = {},
    goToHome: Boolean = false,
    onHomeScrolled: () -> Unit = {},
) = BoxWithConstraints(
    modifier = modifier.modifyIf(itemBeingEdited != null) {
        pointerInput(Unit) {
            detectTapGestures {
                onDismissEditMode()
            }
        }
    }
) {
    val padding = 4.dp
    val gridItemSize = (this.maxWidth - (padding * 2)) / columns
    val density = LocalDensity.current
    val gridItemSizePx = with(density) { gridItemSize.toPx() }

    val tableState: LazyTableState = rememberLazyTableState()

    LaunchedEffect(goToHome) {
        if (goToHome) {
            runCatching {
                tableState.animateToCell(0, 0)
            }
            onHomeScrolled()
        }
    }

    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val extraBottom = if (itemBeingEdited != null) 96.dp else 0.dp
    val base = remember(systemBars, contentPadding, extraBottom) {
        systemBars + PaddingValues(padding) + contentPadding + PaddingValues(bottom = extraBottom)
    }

    val tileShape = remember(tileSettings.cornerRadius) {
        RoundedCornerShape(tileSettings.cornerRadius)
    }

    val tableDimensions = remember(gridItemSize) {
        lazyTableDimensions({ gridItemSize }, { gridItemSize })
    }

    val footerRow = remember(grid) {
        grid.maxOfOrNull { it.y + it.height } ?: 0
    }

    // Disable vertical scrolling on the table when in Edit Mode so drag/resize gestures have zero friction
    val currentScrollDirection = if (itemBeingEdited != null) {
        LazyTableScrollDirection.HORIZONTAL
    } else {
        LazyTableScrollDirection.VERTICAL
    }

    var isScrolling by remember { mutableStateOf(false) }
    var isSettled by remember { mutableStateOf(true) }

    // Scroll performance & 120 FPS optimization:
    // Observe tableState.minaBoxState.translate using snapshotFlow inside LaunchedEffect rather than
    // reading translate in composition scope. This prevents TileLayout from recomposing on every single
    // scroll pixel/frame, eliminating list stutter and frame drops.
    LaunchedEffect(tableState, itemBeingEdited) {
        if (itemBeingEdited != null) {
            isScrolling = false
            isSettled = false
            return@LaunchedEffect
        }
        var settleJob: kotlinx.coroutines.Job? = null
        var lastTranslate: eu.wewox.minabox.Translate? = null
        snapshotFlow { tableState.minaBoxState.translate }
            .collect { currentTranslate ->
                if (lastTranslate != null && currentTranslate != null &&
                    (lastTranslate?.x != currentTranslate.x || lastTranslate?.y != currentTranslate.y)
                ) {
                    if (!isScrolling) {
                        isScrolling = true
                        isSettled = false
                    }
                    settleJob?.cancel()
                    settleJob = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
                        kotlinx.coroutines.delay(400L)
                        isScrolling = false
                        isSettled = true
                    }
                }
                lastTranslate = currentTranslate
            }
    }

    // Live data snapshotting during scroll:
    // Freeze smart tile data and notification maps when scroll begins so continuous background
    // updates (sensor steps, media progress, battery wattage) do not invalidate or recompose
    // the grid during fling/scroll gestures. Throttle to 1 Hz during prolonged scrolls.
    val incomingSmartData = LocalSmartTileData.current
    val incomingNotifications = LocalAppNotifications.current

    val currentSmartData by rememberUpdatedState(incomingSmartData)
    val currentNotifications by rememberUpdatedState(incomingNotifications)

    var activeSmartData by remember { mutableStateOf(incomingSmartData) }
    var activeNotifications by remember { mutableStateOf(incomingNotifications) }

    LaunchedEffect(isScrolling) {
        if (!isScrolling) {
            activeSmartData = currentSmartData
            activeNotifications = currentNotifications
        } else {
            // While scrolling is active, snapshot is frozen. Sample at ~1 Hz if scroll is prolonged (>1s).
            while (true) {
                kotlinx.coroutines.delay(1000L)
                if (isScrolling) {
                    activeSmartData = currentSmartData
                    activeNotifications = currentNotifications
                }
            }
        }
    }

    LaunchedEffect(incomingSmartData, incomingNotifications) {
        if (!isScrolling) {
            activeSmartData = incomingSmartData
            activeNotifications = incomingNotifications
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        tgo1014.gridlauncher.ui.theme.LocalIsGridSettled provides isSettled,
        LocalSmartTileData provides activeSmartData,
        LocalAppNotifications provides activeNotifications
    ) {
        LazyTable(
            state = tableState,
            scrollDirection = currentScrollDirection,
            contentPadding = base,
            dimensions = tableDimensions,
        ) {
        items(
            items = grid,
            key = { it.id },
            contentType = { it.itemType },
            layoutInfo = { tile ->
                LazyTableItem(
                    column = tile.x,
                    row = tile.y,
                    columnsCount = tile.width,
                    rowsCount = tile.height
                )
            }
        ) { tileItem ->
            val isBeingEdited = tileItem.id != tgo1014.gridlauncher.domain.DynamicGridTransformer.MEDIA_TILE_ID && tileItem.id == itemBeingEdited?.id

            if (!isBeingEdited) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxSize()
                ) {
                    val allNotifications = LocalAppNotifications.current
                    val tileNotifications = if (tileItem.itemType == tgo1014.gridlauncher.ui.models.GridItemType.APP) {
                        allNotifications[tileItem.app.packageName]
                    } else null

                    GridTile(
                        item = tileItem,
                        tileSettings = tileSettings,
                        isEditMode = false,
                        liveNotifications = tileNotifications,
                        onItemClicked = { clicked ->
                            if (clicked.id == tgo1014.gridlauncher.domain.DynamicGridTransformer.MEDIA_TILE_ID) {
                                onItemClicked(clicked)
                            } else if (itemBeingEdited != null) {
                                if (clicked.id != itemBeingEdited.id) {
                                    onItemLongClicked(clicked)
                                }
                            } else {
                                onItemClicked(clicked)
                            }
                        },
                        onItemLongClicked = onItemLongClicked,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val currentTileItem by rememberUpdatedState(tileItem)
                val currentColumns by rememberUpdatedState(columns)
                var dragOffset by remember(tileItem.id) { mutableStateOf(Offset.Zero) }
                var isDragging by remember(tileItem.id) { mutableStateOf(false) }
                var resizeOffset by remember(tileItem.id) { mutableStateOf(Offset.Zero) }
                var isResizing by remember(tileItem.id) { mutableStateOf(false) }

                val deltaCols = if (gridItemSizePx > 0) (dragOffset.x / gridItemSizePx).roundToInt() else 0
                val deltaRows = if (gridItemSizePx > 0) (dragOffset.y / gridItemSizePx).roundToInt() else 0
                val targetCol = (tileItem.x + deltaCols).coerceIn(0, columns - tileItem.width)
                val targetRow = (tileItem.y + deltaRows).coerceAtLeast(0)

                val deltaW = if (gridItemSizePx > 0) (resizeOffset.x / gridItemSizePx).roundToInt() else 0
                val deltaH = if (gridItemSizePx > 0) (resizeOffset.y / gridItemSizePx).roundToInt() else 0
                val liveWidth = (tileItem.width + deltaW).coerceIn(1, columns - tileItem.x)
                val liveHeight = (tileItem.height + deltaH).coerceIn(1, 8)

                val tileShape = RoundedCornerShape(tileSettings.cornerRadius)

                // When resizing, dynamically size the container so the user visually sees the live resize
                val containerSizeModifier = if (isResizing) {
                    Modifier.requiredSize(
                        width = gridItemSize * liveWidth,
                        height = gridItemSize * liveHeight
                    )
                } else {
                    Modifier.fillMaxSize()
                }

                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .then(containerSizeModifier)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            shadowElevation = if (isDragging) 24f else 4f
                        }
                        .zIndex(if (isDragging || isResizing) 50f else 10f)
                ) {
                    GridTile(
                        item = tileItem,
                        tileSettings = tileSettings,
                        isEditMode = true,
                        onItemClicked = { clicked ->
                            if (itemBeingEdited != null) {
                                if (clicked.id != itemBeingEdited.id) {
                                    onItemLongClicked(clicked)
                                }
                            } else {
                                onItemClicked(clicked)
                            }
                        },
                        onItemLongClicked = onItemLongClicked,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Active Drag Overlay Body (covers the entire tile to capture touch down instantly)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, tileShape)
                            .clip(tileShape)
                            .pointerInput(tileItem.id) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        val finalDeltaCols = if (gridItemSizePx > 0) (dragOffset.x / gridItemSizePx).roundToInt() else 0
                                        val finalDeltaRows = if (gridItemSizePx > 0) (dragOffset.y / gridItemSizePx).roundToInt() else 0
                                        val item = currentTileItem
                                        val cols = currentColumns
                                        val finalCol = (item.x + finalDeltaCols).coerceIn(0, cols - item.width)
                                        val finalRow = (item.y + finalDeltaRows).coerceAtLeast(0)
                                        if (finalCol != item.x || finalRow != item.y) {
                                            onTilePositionChanged(item.id, finalCol, finalRow)
                                        }
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    }
                                )
                            }
                    )

                    // App Uninstall Button - only on apps
                    if (tileItem.itemType == tgo1014.gridlauncher.ui.models.GridItemType.APP) {
                        val alignment = if (tileItem.height == 1 && tileItem.width == 1) Alignment.TopStart else Alignment.TopEnd
                        val btnSize = if (tileItem.height == 1 && tileItem.width == 1) 28.dp else 36.dp
                        val iconSize = if (tileItem.height == 1 && tileItem.width == 1) 16.dp else 20.dp
                        Box(
                            modifier = Modifier
                                .align(alignment)
                                .padding(2.dp)
                                .size(btnSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { onAppUninstalled(tileItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Uninstall App",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    // Shortcut & Smart Tile Remove Button
                    if (tileItem.itemType == tgo1014.gridlauncher.ui.models.GridItemType.SHORTCUT ||
                        tileItem.itemType == tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE) {
                        val alignment = if (tileItem.height == 1 && tileItem.width == 1) Alignment.TopStart else Alignment.TopEnd
                        val btnSize = if (tileItem.height == 1 && tileItem.width == 1) 28.dp else 36.dp
                        val iconSize = if (tileItem.height == 1 && tileItem.width == 1) 16.dp else 20.dp
                        Box(
                            modifier = Modifier
                                .align(alignment)
                                .padding(2.dp)
                                .size(btnSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { onShortcutRemoved(tileItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Tile",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    // Corner Resize Handle (Bottom-Right) - generous touch target
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .pointerInput(tileItem.id) {
                                detectDragGestures(
                                    onDragStart = { isResizing = true },
                                    onDragEnd = {
                                        isResizing = false
                                        val finalDeltaW = if (gridItemSizePx > 0) (resizeOffset.x / gridItemSizePx).roundToInt() else 0
                                        val finalDeltaH = if (gridItemSizePx > 0) (resizeOffset.y / gridItemSizePx).roundToInt() else 0
                                        val item = currentTileItem
                                        val cols = currentColumns
                                        val finalW = (item.width + finalDeltaW).coerceIn(1, cols - item.x)
                                        val finalH = (item.height + finalDeltaH).coerceIn(1, 8)
                                        if (finalW != item.width || finalH != item.height) {
                                            onTileDimensionChanged(item.id, finalW, finalH)
                                        }
                                        resizeOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        isResizing = false
                                        resizeOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        resizeOffset += dragAmount
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val stroke = 2.dp.toPx()
                            val c = Color.White
                            drawLine(c, Offset(size.width * 0.75f, size.height), Offset(size.width, size.height * 0.75f), stroke)
                            drawLine(c, Offset(size.width * 0.45f, size.height), Offset(size.width, size.height * 0.45f), stroke)
                            drawLine(c, Offset(size.width * 0.15f, size.height), Offset(size.width, size.height * 0.15f), stroke)
                        }
                    }

                    // Live Pill Indicator Badge
                    if (isDragging) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "Row ${targetRow + 1}, Col ${targetCol + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isResizing) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "${liveWidth} × ${liveHeight}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        // Footer
        items(
            count = 1,
            key = { -9999 },
            layoutInfo = {
                LazyTableItem(
                    column = 0,
                    row = footerRow,
                    columnsCount = columns,
                    rowsCount = 1
                )
            },
            itemContent = { footer(Modifier) }
        )
    }
    }
}

@Composable
@Preview(showBackground = true)
private fun Preview() {
    val item1 = GridItem(app = App(name = "FooBar 1"), width = 4, height = 2, x = 1, y = 0)
    val item2 = GridItem(app = App(name = "FooBar 2"), width = 2, height = 2, x = 0, y = 1)
    val item3 = GridItem(app = App(name = "FooBar 3"), width = 2, height = 2, x = 1, y = 2)
    val item4 = GridItem(app = App(name = "FooBar 4"), width = 2, height = 2, x = 2, y = 3)
    val grid = listOf(item1, item2, item3, item4)
    TileLayout(
        grid = grid,
        isOnTop = {},
        columns = 8,
    )
}