package com.port80.app.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.port80.app.data.model.Resolution
import kotlin.math.roundToInt

/**
 * Settings screen for video configuration.
 *
 * Users can adjust resolution, frame rate, bitrate, and keyframe interval.
 * Only device-supported options are shown via [DeviceCapabilityQuery].
 *
 * Compose basics used here:
 * - `collectAsState()` converts a Kotlin StateFlow into Compose State so the
 *   UI recomposes (redraws) whenever the value changes.
 * - `remember { mutableStateOf(...) }` keeps local UI state (like whether a
 *   dropdown is expanded) across recompositions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    // Collect each setting from the ViewModel's StateFlow.
    // `by` delegates to .value so we can use the variable directly.
    val currentResolution by viewModel.resolution.collectAsState()
    val currentFps by viewModel.fps.collectAsState()
    val currentBitrate by viewModel.videoBitrateKbps.collectAsState()
    val currentKeyframeInterval by viewModel.keyframeIntervalSec.collectAsState()

    val supportedResolutions = viewModel.supportedResolutions
    val supportedFps = viewModel.supportedFpsForResolution(currentResolution)
    val maxBitrate = viewModel.maxVideoBitrateKbps

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Settings") },
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
            // ── Resolution ──────────────────────────────────────────

            SectionHeader("Resolution")

            ResolutionPicker(
                selected = currentResolution,
                options = supportedResolutions,
                onSelected = { viewModel.setResolution(it) }
            )

            // ── Frame Rate ──────────────────────────────────────────

            SectionHeader("Frame Rate")

            FpsPicker(
                selected = currentFps,
                options = supportedFps,
                onSelected = { viewModel.setFps(it) }
            )

            // ── Video Bitrate ───────────────────────────────────────

            SectionHeader("Video Bitrate")

            BitrateSlider(
                currentKbps = currentBitrate,
                minKbps = 500,
                maxKbps = maxBitrate,
                onChanged = { viewModel.setVideoBitrateKbps(it) }
            )

            // ── Keyframe Interval ───────────────────────────────────

            SectionHeader("Keyframe Interval")

            KeyframeIntervalSlider(
                currentSec = currentKeyframeInterval,
                onChanged = { viewModel.setKeyframeIntervalSec(it) }
            )
        }
    }
}

// ── Reusable composables ────────────────────────────────────────────

/** A styled section header used throughout settings screens. */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Dropdown picker for resolution.
 *
 * Uses [ExposedDropdownMenuBox] which is the Material3 way to build a
 * dropdown attached to a text field. `menuAnchor()` tells the menu where
 * to position itself relative to the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionPicker(
    selected: Resolution,
    options: List<Resolution>,
    onSelected: (Resolution) -> Unit
) {
    // `remember` keeps this boolean across recompositions.
    // `mutableStateOf` makes it observable — changing it triggers recomposition.
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Resolution") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { resolution ->
                DropdownMenuItem(
                    text = { Text("${resolution.label} (${resolution})") },
                    onClick = {
                        onSelected(resolution)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Dropdown picker for frame rate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsPicker(
    selected: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "$selected fps",
            onValueChange = {},
            readOnly = true,
            label = { Text("Frame rate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { fps ->
                DropdownMenuItem(
                    text = { Text("$fps fps") },
                    onClick = {
                        onSelected(fps)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Slider for video bitrate with a live label.
 *
 * [Slider] in Compose reports continuous float values. We round to the
 * nearest 100 kbps step for a cleaner UX.
 */
@Composable
private fun BitrateSlider(
    currentKbps: Int,
    minKbps: Int,
    maxKbps: Int,
    onChanged: (Int) -> Unit
) {
    Text(
        text = "${currentKbps} kbps",
        style = MaterialTheme.typography.bodyLarge
    )
    Slider(
        value = currentKbps.toFloat(),
        onValueChange = { raw ->
            // Snap to nearest 100 kbps for cleaner values.
            val snapped = (raw / 100f).roundToInt() * 100
            onChanged(snapped.coerceIn(minKbps, maxKbps))
        },
        valueRange = minKbps.toFloat()..maxKbps.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
    SliderLabels(left = "$minKbps kbps", right = "$maxKbps kbps")
}

/**
 * Slider for keyframe interval (1–5 seconds).
 *
 * `steps` parameter creates discrete snap points between the endpoints.
 * For range 1–5 with 3 steps we get stops at 1, 2, 3, 4, 5.
 */
@Composable
private fun KeyframeIntervalSlider(
    currentSec: Int,
    onChanged: (Int) -> Unit
) {
    Text(
        text = "$currentSec second${if (currentSec != 1) "s" else ""}",
        style = MaterialTheme.typography.bodyLarge
    )
    Slider(
        value = currentSec.toFloat(),
        onValueChange = { onChanged(it.roundToInt()) },
        valueRange = 1f..5f,
        steps = 3, // Creates discrete stops at 2, 3, 4 (endpoints are automatic)
        modifier = Modifier.fillMaxWidth()
    )
    SliderLabels(left = "1 s", right = "5 s")
}

/** Min/max labels displayed beneath a slider. */
@Composable
fun SliderLabels(left: String, right: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(text = left, style = MaterialTheme.typography.bodySmall)
        Text(text = right, style = MaterialTheme.typography.bodySmall)
    }
}
