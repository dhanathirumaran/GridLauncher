package tgo1014.gridlauncher.ui.composables

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.LazyTableScrollDirection
import eu.wewox.lazytable.lazyTableDimensions
import kotlin.math.roundToInt
import tgo1014.gridlauncher.app.Constants.gridColumns
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.domain.usecases.wallpaper.AppHazeStyle
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.modifyIf
import tgo1014.gridlauncher.ui.theme.plus

@OptIn(ExperimentalHazeMaterialsApi::class)
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
    onWidgetUnpinned: (item: GridItem) -> Unit = {},
    onDismissEditMode: () -> Unit = {},
    footer: @Composable (Modifier) -> Unit = {},
) = BoxWithConstraints(
    modifier = modifier.pointerInput(itemBeingEdited) {
        if (itemBeingEdited != null) {
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
    var firstItemPosition: Float? by remember { mutableStateOf(null) }
    LaunchedIfTrueEffect(grid.isEmpty()) {
        isOnTop(true)
    }
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    var base by remember { mutableStateOf(PaddingValues()) }
    LaunchedEffect(itemBeingEdited) {
        val extraBottom = if (itemBeingEdited != null) 96.dp else 0.dp
        base = systemBars + PaddingValues(padding) + contentPadding + PaddingValues(bottom = extraBottom)
    }
    val hazeChildModifier = Modifier
        .clip(RoundedCornerShape(tileSettings.cornerRadius))
        .hazeChild(
            state = hazeState,
            style = AppHazeStyle,
        )
    LazyTable(
        scrollDirection = LazyTableScrollDirection.VERTICAL,
        contentPadding = base,
        dimensions = lazyTableDimensions({ gridItemSize }, { gridItemSize }),
    ) {
        items(
            items = grid,
            layoutInfo = { tile ->
                LazyTableItem(
                    column = tile.x,
                    row = tile.y,
                    columnsCount = tile.width,
                    rowsCount = tile.height
                )
            }
        ) { tileItem ->
            val isBeingEdited = tileItem.id == itemBeingEdited?.id

            var dragOffset by remember(tileItem.id, isBeingEdited) { mutableStateOf(Offset.Zero) }
            var isDragging by remember(tileItem.id, isBeingEdited) { mutableStateOf(false) }
            var resizeOffset by remember(tileItem.id, isBeingEdited) { mutableStateOf(Offset.Zero) }
            var isResizing by remember(tileItem.id, isBeingEdited) { mutableStateOf(false) }

            val deltaCols = if (gridItemSizePx > 0) (dragOffset.x / gridItemSizePx).roundToInt() else 0
            val deltaRows = if (gridItemSizePx > 0) (dragOffset.y / gridItemSizePx).roundToInt() else 0
            val targetCol = (tileItem.x + deltaCols).coerceIn(0, columns - tileItem.width)
            val targetRow = (tileItem.y + deltaRows).coerceAtLeast(0)

            val deltaW = if (gridItemSizePx > 0) (resizeOffset.x / gridItemSizePx).roundToInt() else 0
            val deltaH = if (gridItemSizePx > 0) (resizeOffset.y / gridItemSizePx).roundToInt() else 0
            val liveWidth = (tileItem.width + deltaW).coerceIn(1, columns - tileItem.x)
            val liveHeight = (tileItem.height + deltaH).coerceIn(1, 8)

            val tileShape = RoundedCornerShape(tileSettings.cornerRadius)

            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxSize()
                    .modifyIf(isBeingEdited) {
                        graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            shadowElevation = if (isDragging) 24f else 4f
                        }
                        .zIndex(if (isDragging || isResizing) 30f else 10f)
                    }
            ) {
                GridTile(
                    item = tileItem,
                    tileSettings = tileSettings,
                    isEditMode = isBeingEdited,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .modifyIf(tileItem == grid.firstOrNull()) {
                            onGloballyPositioned { coord ->
                                val y = coord.positionInWindow().y
                                if (firstItemPosition == null) {
                                    firstItemPosition = y
                                }
                                isOnTop(firstItemPosition == y)
                            }
                        }
                        .then(hazeChildModifier)
                )

                if (isBeingEdited) {
                    // Active Edit Mode Overlay
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
                                        val finalCol = (tileItem.x + finalDeltaCols).coerceIn(0, columns - tileItem.width)
                                        val finalRow = (tileItem.y + finalDeltaRows).coerceAtLeast(0)
                                        if (finalCol != tileItem.x || finalRow != tileItem.y) {
                                            onTilePositionChanged(tileItem.id, finalCol, finalRow)
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

                    // Widget Unpin Button (Top-Right) - only on widgets
                    if (tileItem.itemType == tgo1014.gridlauncher.ui.models.GridItemType.WIDGET) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { onWidgetUnpinned(tileItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unpin Widget",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Corner Resize Handle (Bottom-Right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .pointerInput(tileItem.id) {
                                detectDragGestures(
                                    onDragStart = { isResizing = true },
                                    onDragEnd = {
                                        isResizing = false
                                        val finalDeltaW = if (gridItemSizePx > 0) (resizeOffset.x / gridItemSizePx).roundToInt() else 0
                                        val finalDeltaH = if (gridItemSizePx > 0) (resizeOffset.y / gridItemSizePx).roundToInt() else 0
                                        val finalW = (tileItem.width + finalDeltaW).coerceIn(1, columns - tileItem.x)
                                        val finalH = (tileItem.height + finalDeltaH).coerceIn(1, 8)
                                        if (finalW != tileItem.width || finalH != tileItem.height) {
                                            onTileDimensionChanged(tileItem.id, finalW, finalH)
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
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Resize Tile",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Live Pill Badge
                    if (isDragging) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shadowElevation = 4.dp,
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
                            shadowElevation = 4.dp,
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
        val maxY = grid.maxOfOrNull { it.y } ?: 0
        val rowOffset = grid.filter { it.y == maxY }
            .fastMaxOfOrNull { it.height }
            ?: 0
        items(
            count = 1,
            layoutInfo = {
                LazyTableItem(
                    column = 0,
                    row = maxY + rowOffset,
                    columnsCount = columns,
                    rowsCount = 1
                )
            },
            itemContent = { footer(hazeChildModifier) }
        )
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