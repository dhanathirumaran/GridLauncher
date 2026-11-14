package tgo1014.gridlauncher.ui.composables.sheets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.app.Constants

data class WidgetPickerItem(
    val info: AppWidgetProviderInfo,
    val appName: String,
    val widgetLabel: String,
    val spanX: Int,
    val spanY: Int,
    val icon: Drawable?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerBottomSheet(
    isShowing: Boolean,
    onDismiss: () -> Unit,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current.applicationContext
    var widgetItems by remember { mutableStateOf<List<WidgetPickerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(isShowing) {
        if (isShowing) {
            isLoading = true
            widgetItems = loadInstalledWidgets(context)
            isLoading = false
            sheetState.show()
        } else {
            sheetState.hide()
            searchQuery = ""
        }
    }

    if (isShowing) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add widget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search widgets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filtered = remember(widgetItems, searchQuery) {
                        if (searchQuery.isBlank()) {
                            widgetItems
                        } else {
                            val q = searchQuery.trim().lowercase()
                            widgetItems.filter {
                                it.appName.lowercase().contains(q) ||
                                        it.widgetLabel.lowercase().contains(q)
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Text(
                                text = if (widgetItems.isEmpty()) "No widgets found on device" else "No matching widgets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val grouped = remember(filtered) {
                            filtered.groupBy { it.appName }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            grouped.forEach { (appName, items) ->
                                item(key = "header_$appName", contentType = "header") {
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(
                                    items = items,
                                    key = { "${it.info.provider.flattenToString()}_${it.widgetLabel}_${it.spanX}x${it.spanY}" },
                                    contentType = { "widget_item" }
                                ) { widget ->
                                    WidgetRowItem(
                                        item = widget,
                                        onClick = { onWidgetSelected(widget.info) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRowItem(
    item: WidgetPickerItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val icon = item.icon
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.widgetLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "${item.spanX} × ${item.spanY}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private suspend fun loadInstalledWidgets(context: Context): List<WidgetPickerItem> =
    withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext emptyList()
        val pm = context.packageManager
        val providers = runCatching { appWidgetManager.installedProviders }.getOrNull().orEmpty()

        providers.mapNotNull { info ->
            try {
                val appInfo = runCatching { pm.getApplicationInfo(info.provider.packageName, 0) }.getOrNull()
                val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.provider.packageName
                val widgetLabel = info.loadLabel(pm).takeIf { !it.isNullOrBlank() } ?: appName
                val (spanX, spanY) = calculateSpan(info)
                val icon = runCatching { info.loadIcon(context, 0) ?: appInfo?.loadIcon(pm) }.getOrNull()

                WidgetPickerItem(
                    info = info,
                    appName = appName,
                    widgetLabel = widgetLabel,
                    spanX = spanX,
                    spanY = spanY,
                    icon = icon,
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.appName }, { it.widgetLabel }))
    }

private fun calculateSpan(info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return Pair(
                info.targetCellWidth.coerceIn(1, Constants.gridColumns),
                info.targetCellHeight.coerceAtLeast(1)
            )
        }
    }
    val spanX = ((info.minWidth + 30) / 70).coerceIn(1, Constants.gridColumns)
    val spanY = ((info.minHeight + 30) / 70).coerceIn(1, 8)
    return Pair(spanX, spanY)
}
