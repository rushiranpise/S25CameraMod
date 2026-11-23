package io.github.rushiranpise.cameramod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rushiranpise.cameramod.settings.FeatureTab
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
                    text = "v1.0.1",
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
