package com.port80.app.ui.settings

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

/**
 * Settings screen for general app configuration.
 *
 * Includes adaptive bitrate toggle, default camera, orientation,
 * battery thresholds, and local recording. This is the "everything
 * else" screen for settings that aren't video or audio encoding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val abrEnabled by viewModel.abrEnabled.collectAsState()
    val defaultCameraId by viewModel.defaultCameraId.collectAsState()
    val orientationLocked by viewModel.orientationLocked.collectAsState()
    val preferredOrientation by viewModel.preferredOrientation.collectAsState()
    val lowBattery by viewModel.lowBatteryThreshold.collectAsState()
    val criticalBattery by viewModel.criticalBatteryThreshold.collectAsState()
    val recordingEnabled by viewModel.localRecordingEnabled.collectAsState()

    val hasFront = viewModel.hasFrontCamera
    val hasBack = viewModel.hasBackCamera

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Streaming ───────────────────────────────────────────

            SectionHeader("Streaming")

            ToggleItem(
                title = "Adaptive Bitrate",
                description = "Automatically adjust bitrate based on network conditions",
                checked = abrEnabled,
                onCheckedChange = { viewModel.setAbrEnabled(it) }
            )

            // ── Camera ──────────────────────────────────────────────

            SectionHeader("Camera")

            CameraPicker(
                selectedId = defaultCameraId,
                hasFront = hasFront,
                hasBack = hasBack,
                onSelected = { viewModel.setDefaultCameraId(it) }
            )

            // ── Orientation ─────────────────────────────────────────

            SectionHeader("Orientation")

            ToggleItem(
                title = "Lock Orientation",
                description = "Prevent screen rotation while streaming",
                checked = orientationLocked,
                onCheckedChange = { viewModel.setOrientationLocked(it) }
            )

            // Only show preferred orientation when locking is enabled.
            if (orientationLocked) {
                OrientationPicker(
                    selected = preferredOrientation,
                    onSelected = { viewModel.setPreferredOrientation(it) }
                )
            }

            // ── Battery ─────────────────────────────────────────────

            SectionHeader("Battery")

            BatteryThresholdSlider(
                label = "Low Battery Warning",
                description = "Show a warning when battery drops below this level",
                currentPercent = lowBattery,
                minPercent = 3,
                maxPercent = 30,
                onChanged = { viewModel.setLowBatteryThreshold(it) }
            )

            BatteryThresholdSlider(
                label = "Critical Battery Stop",
                description = "Automatically stop streaming at this battery level",
                currentPercent = criticalBattery,
                minPercent = 1,
                maxPercent = 15,
                onChanged = { viewModel.setCriticalBatteryThreshold(it) }
            )

            // ── Recording ───────────────────────────────────────────

            SectionHeader("Recording")

            ToggleItem(
                title = "Local Recording",
                description = "Save a local copy of the stream to device storage",
                checked = recordingEnabled,
                onCheckedChange = { viewModel.setLocalRecordingEnabled(it) }
            )
        }
    }
}

// ── Private composables ─────────────────────────────────────────────

/**
 * A standard toggle row using Material3 [ListItem].
 *
 * [ListItem] is a pre-built layout that handles headline text,
 * supporting text, and trailing content with correct padding and
 * typography. We reuse it for every boolean setting.
 */
@Composable
private fun ToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

/** Dropdown for selecting the default camera (front or back). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraPicker(
    selectedId: String,
    hasFront: Boolean,
    hasBack: Boolean,
    onSelected: (String) -> Unit
) {
    // Build the list of available cameras based on device hardware.
    val options = buildList {
        if (hasBack) add("0" to "Back Camera")
        if (hasFront) add("1" to "Front Camera")
    }

    // If only one camera exists, show it as a non-interactive label.
    if (options.size <= 1) {
        ListItem(
            headlineContent = { Text("Default Camera") },
            supportingContent = {
                Text(options.firstOrNull()?.second ?: "No camera available")
            }
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "Back Camera"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Default Camera") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Dropdown for selecting the preferred screen orientation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrientationPicker(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    // Map Android's ActivityInfo orientation constants to human labels.
    val options = listOf(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "Landscape",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "Portrait",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED to "Auto"
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Auto"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Preferred Orientation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * A slider for battery percentage thresholds with a label.
 *
 * The [Slider] emits continuous float values. We round to the nearest
 * integer and clamp to the allowed range before saving.
 */
@Composable
private fun BatteryThresholdSlider(
    label: String,
    description: String,
    currentPercent: Int,
    minPercent: Int,
    maxPercent: Int,
    onChanged: (Int) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) }
    )
    Text(
        text = "$currentPercent%",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp)
    )
    Slider(
        value = currentPercent.toFloat(),
        onValueChange = { onChanged(it.roundToInt().coerceIn(minPercent, maxPercent)) },
        valueRange = minPercent.toFloat()..maxPercent.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
    SliderLabels(
        left = "$minPercent%",
        right = "$maxPercent%"
    )
}
