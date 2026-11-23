package io.github.rushiranpise.cameramod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rushiranpise.cameramod.settings.CameraFlag
import io.github.rushiranpise.cameramod.settings.FeatureTab
import io.github.rushiranpise.cameramod.settings.FlagCatalog
import io.github.rushiranpise.cameramod.settings.FlagCategory
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.ModSettings
import io.github.rushiranpise.cameramod.ui.CameraModTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraModTheme {
                StatusScreen()
            }
        }
    }
}

@Composable
private fun StatusScreen() {
    var showFlags by remember { mutableStateOf(false) }

    BackHandler(enabled = showFlags) { showFlags = false }

    if (showFlags) {
        FlagExplorerScreen(onBack = { showFlags = false })
    } else {
        MainSettingsScreen(onOpenFlags = { showFlags = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsScreen(onOpenFlags: () -> Unit) {
    val context = LocalContext.current
    var flags by remember { mutableStateOf(ModSettings.snapshot(context)) }
    var zoomCeiling by remember { mutableStateOf(ModSettings.zoomCeiling(context)) }
    var zoomPresets by remember { mutableStateOf(ModSettings.zoomPresets(context)) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(FeatureTab.VIDEO) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S25 CAMERA MOD",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "v1.1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "${formatZoom(zoomCeiling)}×",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Back-camera photo & video ceiling",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    ToggleRow(
                        feature = ModFeature.ZOOM,
                        checked = flags[ModFeature.ZOOM.key] ?: ModFeature.ZOOM.defaultEnabled,
                        onCheckedChange = { enabled ->
                            ModSettings.setEnabled(context, ModFeature.ZOOM, enabled)
                            flags = flags + (ModFeature.ZOOM.key to enabled)
                        },
                        onSettings = { showZoomDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FeatureTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            val tabFeatures = ModFeature.entries.filter { it.tab == selectedTab }
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    tabFeatures.forEachIndexed { index, feature ->
                        ToggleRow(
                            feature = feature,
                            checked = flags[feature.key] ?: feature.defaultEnabled,
                            onCheckedChange = { enabled ->
                                ModSettings.setEnabled(context, feature, enabled)
                                flags = flags + (feature.key to enabled)
                            },
                        )
                        if (index != tabFeatures.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                onClick = onOpenFlags,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Feature flags",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Browse all support flags and override them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Force stop Camera after changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showZoomDialog) {
        ZoomLimitDialog(
            current = zoomCeiling,
            currentPresets = zoomPresets,
            onDismiss = { showZoomDialog = false },
            onConfirm = { value, presets ->
                ModSettings.setZoomCeiling(context, value)
                ModSettings.setZoomPresets(context, presets)
                zoomCeiling = ModSettings.zoomCeiling(context)
                zoomPresets = ModSettings.zoomPresets(context)
                showZoomDialog = false
            },
        )
    }
}

private enum class FlagFilter(val label: String) {
    ALL("All"),
    STOCK_ON("On"),
    STOCK_OFF("Off"),
    OVERRIDDEN("Overridden"),
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FlagExplorerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { FlagCatalog.load(context) }
    var snapshot by remember { mutableStateOf(ModSettings.stockFlagSnapshot(context)) }
    var overrides by remember { mutableStateOf(ModSettings.flagOverrides(context)) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FlagCategory?>(null) }
    var filter by remember { mutableStateOf(FlagFilter.ALL) }
    var showResetDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(category, filter, query) {
        listState.scrollToItem(0)
    }

    val allFlags = remember(catalog, snapshot, overrides) {
        val names = sortedSetOf<String>()
        catalog.forEach { names += it.name }
        snapshot?.flags?.keys?.let { names += it }
        overrides.keys.let { names += it }
        names.map { CameraFlag(it, FlagCatalog.categorize(it), FlagCatalog.describe(it)) }
    }

    val filtered = remember(allFlags, query, category, filter, snapshot, overrides) {
        val needle = query.trim()
        allFlags.filter { flag ->
            val stockValue = snapshot?.flags?.get(flag.name)
            (category == null || flag.category == category) &&
                (needle.isEmpty() ||
                    flag.name.contains(needle, ignoreCase = true) ||
                    flag.summary.contains(needle, ignoreCase = true)) &&
                when (filter) {
                    FlagFilter.ALL -> true
                    FlagFilter.STOCK_ON -> stockValue == true
                    FlagFilter.STOCK_OFF -> stockValue == false
                    FlagFilter.OVERRIDDEN -> overrides.containsKey(flag.name)
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "Feature flags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshotSummary(snapshot),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (snapshot == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { showResetDialog = true },
                    enabled = overrides.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reset),
                        contentDescription = "Reset all overrides",
                    )
                }
                IconButton(
                    onClick = {
                        snapshot = ModSettings.stockFlagSnapshot(context)
                        overrides = ModSettings.flagOverrides(context)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "Refresh stock values",
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search flags") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Summaries are inferred from flag names, not official Samsung docs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(10.dp))
            PrimaryTabRow(
                selectedTabIndex = category?.let { it.ordinal + 1 } ?: 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = category == null,
                    onClick = { category = null },
                    text = { Text("All", fontSize = 12.sp) },
                )
                FlagCategory.entries.forEach { entry ->
                    Tab(
                        selected = category == entry,
                        onClick = { category = entry },
                        text = { Text(entry.label, fontSize = 12.sp) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                FlagFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label, fontSize = 12.sp) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.name }) { flag ->
                    val stockValue = snapshot?.flags?.get(flag.name)
                    val override = overrides[flag.name]
                    FlagRow(
                        flag = flag,
                        stock = stockValue,
                        override = override,
                        onCheckedChange = { checked ->
                            val newOverride = if (stockValue != null && checked == stockValue) {
                                null
                            } else {
                                checked
                            }
                            ModSettings.setFlagOverride(context, flag.name, newOverride)
                            overrides = if (newOverride == null) {
                                overrides - flag.name
                            } else {
                                overrides + (flag.name to newOverride)
                            }
                        },
                        onClearOverride = {
                            ModSettings.setFlagOverride(context, flag.name, null)
                            overrides = overrides - flag.name
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all overrides?") },
            text = {
                Text(
                    "Every flag returns to its stock device value. Zoom settings are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModSettings.clearFlagOverrides(context)
                        overrides = emptyMap()
                        showResetDialog = false
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun FlagRow(
    flag: CameraFlag,
    stock: Boolean?,
    override: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    onClearOverride: () -> Unit,
) {
    val effective = override ?: stock ?: false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = flag.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
            Text(
                text = flag.summary,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FlagBadge(
                    text = flag.category.label,
                    color = when (flag.category) {
                        FlagCategory.VIDEO -> MaterialTheme.colorScheme.tertiary
                        FlagCategory.CAMERA -> MaterialTheme.colorScheme.primary
                    },
                )
                when {
                    override != null -> FlagBadge(
                        text = if (override) "Override ON" else "Override OFF",
                        color = if (override) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )

                    stock != null -> FlagBadge(
                        text = if (stock) "Default · ON" else "Default · OFF",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> FlagBadge(
                        text = "No stock data",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (override != null) {
            IconButton(
                onClick = onClearOverride,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Clear override for ${flag.name}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            checked = effective,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun FlagBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun snapshotSummary(snapshot: ModSettings.StockFlagSnapshot?): String {
    if (snapshot == null) {
        return "No stock snapshot — open Camera once"
    }
    val onCount = snapshot.flags.count { it.value }
    val version = snapshot.cameraVersion ?: "unknown"
    return "Camera $version · $onCount of ${snapshot.flags.size} flags on"
}

@Composable
private fun ToggleRow(
    feature: ModFeature,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = feature.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onSettings != null) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_zoom_limit),
                    contentDescription = "Zoom limit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoomLimitDialog(
    current: Float,
    currentPresets: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Float, List<Int>) -> Unit,
) {
    var ceilingInput by remember { mutableStateOf(formatZoom(current)) }
    var presets by remember { mutableStateOf(currentPresets) }
    var presetInput by remember { mutableStateOf("") }

    val ceilingValue = ceilingInput.replace(',', '.').toFloatOrNull()
    val ceilingValid = ceilingValue != null &&
        ceilingValue >= ModSettings.MIN_ZOOM_CEILING &&
        ceilingValue <= ModSettings.MAX_ZOOM_CEILING

    val presetValue = presetInput.toIntOrNull()
    val presetValid = presetValue != null &&
        presetValue >= ModSettings.MIN_ZOOM_PRESET &&
        presetValue <= ModSettings.MAX_ZOOM_PRESET &&
        presetValue !in presets &&
        presets.size < ModSettings.MAX_ZOOM_PRESETS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zoom limit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = ceilingInput,
                    onValueChange = { ceilingInput = it },
                    singleLine = true,
                    suffix = { Text("×") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = presetInput,
                        onValueChange = { presetInput = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        suffix = { Text("×") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            presetValue?.let { value ->
                                presets = (presets + value).distinct().sorted()
                            }
                            presetInput = ""
                        },
                        enabled = presetValid,
                    ) {
                        Text("Add")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { value ->
                        InputChip(
                            selected = false,
                            onClick = { presets = presets - value },
                            label = { Text("$value×") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Remove $value×",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { ceilingValue?.let { onConfirm(it, presets) } },
                enabled = ceilingValid,
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatZoom(value: Float): String =
    if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
