package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.data.calendar.CalendarEventManager
import tgo1014.gridlauncher.data.notification.NotificationSender
import tgo1014.gridlauncher.data.phone.PhoneAppManager
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.AsyncImage
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import tgo1014.gridlauncher.ui.theme.flipRandomly
import tgo1014.gridlauncher.ui.theme.isPreview
import tgo1014.gridlauncher.ui.theme.modifyIf
import tgo1014.gridlauncher.ui.theme.tileEditMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    item: GridItem,
    modifier: Modifier = Modifier,
    tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false,
    liveNotifications: List<NotificationSender>? = null,
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
) {
    if (item.itemType == tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE) {
        val smartData = LocalSmartTileData.current
        SmartTile(
            item = item,
            modifier = modifier,
            tileSettings = tileSettings,
            isEditMode = isEditMode,
            flashlightOn = smartData.flashlightOn,
            flashlightAvailable = smartData.flashlightAvailable,
            stepCounterState = smartData.stepCounterState,
            batteryState = smartData.batteryState,
            currencyState = smartData.currencyState,
            weatherState = smartData.weatherState,
            calendarEvents = smartData.calendarEvents,
            isCalendarPermissionGranted = smartData.isCalendarPermissionGranted,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked
        )
        return
    }

    val isShortcut = item.itemType == tgo1014.gridlauncher.ui.models.GridItemType.SHORTCUT
    val itemName = if (isShortcut) item.shortcutLabel.orEmpty() else item.app.name
    val iconModel = if (isShortcut) item.shortcutIconPath else item.app.icon.iconFile
    val bgModel = if (isShortcut) null else item.app.icon.bgFile
    val isLightBg = if (isShortcut) false else item.app.icon.isLightBackground

    val shape = RoundedCornerShape(tileSettings.cornerRadius)
    val app = item.app
    val isSettled = tgo1014.gridlauncher.ui.theme.LocalIsGridSettled.current
    Box(
        modifier = Modifier
            .modifyIf(!isEditMode && tileSettings.isTileFlipEnabled && isSettled) {
                flipRandomly(isSettled)
            }
            .tileEditMode(isEditMode)
            .clip(shape)
            .combinedClickable(
                onClick = { onItemClicked(item) },
                onLongClick = { onItemLongClicked(item) }
            )
            .then(modifier)
    ) {
        if (!tileSettings.isTransparencyEnabled) {
            if (bgModel != null) {
                AsyncImage(
                    model = bgModel,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
        }
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer)
        val textColor = remember(
            tileSettings.isTransparencyEnabled,
            bgModel,
            isLightBg,
            onContainer,
            contentColor
        ) {
            when {
                tileSettings.isTransparencyEnabled -> Color.White
                bgModel == null -> onContainer
                isLightBg -> Color.Black
                else -> Color.White
            }
        }
        val effectiveNotifications = liveNotifications ?: if (!isShortcut) LocalAppNotifications.current[item.app.packageName] else null
        val hasLiveNotifications = !effectiveNotifications.isNullOrEmpty()
        val context = LocalContext.current
        val isCalendar = !isShortcut && CalendarEventManager.isCalendarApp(item.app.packageName, context)
        val hasMissedCall = hasLiveNotifications && effectiveNotifications.orEmpty().any { it.isMissedCall }

        if (item.width >= 4 && item.height >= 2 && isCalendar) {
            val smartData = LocalSmartTileData.current
            AppleCalendarContent(
                calendarEvents = smartData.calendarEvents,
                isPermissionGranted = smartData.isCalendarPermissionGranted,
                textColor = textColor,
                maxEvents = if (item.height >= 3) 4 else 2
            )
        } else if (item.width >= 4 && item.height >= 2 && hasMissedCall) {
            AppleMissedCallContent(
                iconModel = iconModel,
                liveNotifications = effectiveNotifications!!,
                textColor = textColor,
                isPreview = isPreview
            )
        } else if (item.width >= 4 && item.height >= 2 && hasLiveNotifications) {
            val horizontalPadding = (16 + (tileSettings.cornerRadius - 8).coerceAtLeast(0) * 0.2f).dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconModifier = Modifier.size(36.dp)
                if (isPreview) {
                    Image(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                } else {
                    AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val sendersToShow = effectiveNotifications!!.take(3)
                    sendersToShow.forEach { sender ->
                        val countText = if (sender.count > 1) " (${sender.count})" else ""
                        Text(
                            text = "${sender.name}$countText",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (effectiveNotifications.size > 3) {
                        Text(
                            text = "+${effectiveNotifications.size - 3} more",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else if (item.width >= 2 && item.height == 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val iconSizeModifier = Modifier.size(28.dp)
                if (isPreview) {
                    Image(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = iconSizeModifier,
                    )
                } else {
                    AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = iconSizeModifier,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val iconModifier = Modifier.align(Alignment.Center)
                if (isPreview) {
                    Image(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                } else {
                    AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                }
                if (item.width > 1 && item.height > 1 && !tileSettings.isAppLabelsHidden) {
                    Text(
                        text = itemName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(8.dp)
                            .padding(horizontal = (tileSettings.cornerRadius * 0.5).dp),
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
private fun PreviewEdit() = GridLauncherTheme {
    val gridCellSize = 100.dp
    val item = GridItem(app = App("Foobar"), width = 1)
    Box(
        Modifier
            .height(gridCellSize * item.height)
            .width(gridCellSize * item.width)
    ) {
        GridTile(item = item, isEditMode = true)
    }
}

@Composable
@Preview(wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
private fun PreviewSmallSquare() = GridLauncherTheme {
    val gridCellSize = 100.dp
    val item = GridItem(app = App("Foobar"), width = 1)
    Box(
        Modifier
            .height(gridCellSize * item.height)
            .width(gridCellSize * item.width)
    ) {
        GridTile(item = item)
    }

}

@Composable
@Preview(wallpaper = Wallpapers.YELLOW_DOMINATED_EXAMPLE)
private fun PreviewMediumSquare() = GridLauncherTheme {
    val gridCellSize = 100.dp
    val item = GridItem(app = App("Foobar"), width = 2)
    Box(
        Modifier
            .height(gridCellSize * item.height)
            .width(gridCellSize * item.width)
    ) {
        GridTile(item = item)
    }
}

@Composable
@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
private fun PreviewLargeRectangle() = GridLauncherTheme {
    val gridCellSize = 100.dp
    val item = GridItem(app = App("Foobar"), width = 4, height = 2)
    Box(
        Modifier
            .height(gridCellSize * item.height)
            .width(gridCellSize * item.width)
    ) {
        GridTile(item = item)
    }
}