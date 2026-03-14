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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

/**
 * Settings screen for audio configuration.
 *
 * Users can adjust audio bitrate, sample rate, and stereo/mono.
 * Changes are persisted immediately through the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    // collectAsState() bridges Kotlin coroutine Flows into Compose's
    // snapshot state system. When the Flow emits a new value, any
    // composable reading this state will recompose.
    val currentBitrate by viewModel.audioBitrateKbps.collectAsState()
    val currentSampleRate by viewModel.audioSampleRate.collectAsState()
    val currentStereo by viewModel.stereo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Settings") },
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
            // ── Audio Bitrate ───────────────────────────────────────

            SectionHeader("Audio Bitrate")

            AudioBitratePicker(
                selected = currentBitrate,
                onSelected = { viewModel.setAudioBitrateKbps(it) }
            )

            // ── Sample Rate ─────────────────────────────────────────

            SectionHeader("Sample Rate")

            SampleRatePicker(
                selected = currentSampleRate,
                onSelected = { viewModel.setAudioSampleRate(it) }
            )

            // ── Channel Mode ────────────────────────────────────────

            SectionHeader("Channel Mode")

            // ListItem is a Material3 component for standard list rows.
            // It handles padding, typography, and alignment automatically.
            ListItem(
                headlineContent = { Text("Stereo") },
                supportingContent = {
                    Text(
                        if (currentStereo) "Two-channel audio"
                        else "Single-channel (mono) audio"
                    )
                },
                trailingContent = {
                    // Switch represents a boolean toggle.
                    // `checked` is the current state, `onCheckedChange` fires
                    // when the user taps it. Compose re-renders the switch
                    // once the new value flows back through the StateFlow.
                    Switch(
                        checked = currentStereo,
                        onCheckedChange = { viewModel.setStereo(it) }
                    )
                }
            )
        }
    }
}

// ── Private composables ─────────────────────────────────────────────

/** Dropdown for audio bitrate with common broadcast presets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioBitratePicker(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(64, 96, 128, 192)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "$selected kbps",
            onValueChange = {},
            readOnly = true,
            label = { Text("Bitrate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { kbps ->
                DropdownMenuItem(
                    text = { Text("$kbps kbps") },
                    onClick = {
                        onSelected(kbps)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Dropdown for audio sample rate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleRatePicker(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(44_100, 48_000)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "${selected / 1000} kHz",
            onValueChange = {},
            readOnly = true,
            label = { Text("Sample rate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { rate ->
                DropdownMenuItem(
                    text = {
                        Text("${rate / 1000} kHz (${rate} Hz)")
                    },
                    onClick = {
                        onSelected(rate)
                        expanded = false
                    }
                )
            }
        }
    }
}
