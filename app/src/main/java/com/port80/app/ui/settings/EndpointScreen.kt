package com.port80.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.model.SrtKeyLength
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.StreamProtocol
import com.port80.app.data.model.VideoCodec

/**
 * Screen for managing RTMP endpoint profiles (streaming destinations).
 * Shows a list of saved profiles and allows adding/editing/deleting.
 *
 * Each profile has:
 * - Name (e.g., "My YouTube Channel")
 * - RTMP URL (e.g., "rtmp://ingest.example.com/live")
 * - Stream key (hidden by default, shown on tap)
 * - Optional username/password
 * - Default toggle (which profile to use when starting a stream)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointScreen(
    viewModel: EndpointViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    // Collect the list of saved profiles and the currently-editing profile from the ViewModel.
    val profiles by viewModel.profiles.collectAsState()
    val editingProfile by viewModel.editingProfile.collectAsState()

    // Track which profile the user wants to delete (shows confirmation dialog).
    var profileToDelete by remember { mutableStateOf<EndpointProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streaming Endpoints") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        },
        // Floating button to create a new profile.
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newProfile() }) {
                Icon(Icons.Default.Add, contentDescription = "Add profile")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (profiles.isEmpty()) {
                // Empty state — tell the user what to do.
                EmptyState()
            } else {
                // Render a card for each saved profile.
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onEdit = { viewModel.selectProfile(profile) },
                        onDelete = { profileToDelete = profile },
                        onSetDefault = { viewModel.setDefault(profile.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Extra bottom padding so the FAB doesn't overlap the last card.
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // ── Edit Dialog ─────────────────────────────────────────────
    // When editingProfile is non-null the ViewModel wants us to show the editor.
    editingProfile?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onSave = { viewModel.saveProfile(it) },
            onDismiss = { viewModel.dismissEdit() }
        )
    }

    // ── Delete Confirmation Dialog ──────────────────────────────
    profileToDelete?.let { profile ->
        DeleteConfirmationDialog(
            profileName = profile.name.ifBlank { "Unnamed profile" },
            onConfirm = {
                viewModel.deleteProfile(profile.id)
                profileToDelete = null
            },
            onDismiss = { profileToDelete = null }
        )
    }
}

// ── Profile Card ────────────────────────────────────────────────

/**
 * A Material3 card showing a single endpoint profile.
 * Displays the profile name, a masked RTMP URL, and action icons.
 */
@Composable
private fun ProfileCard(
    profile: EndpointProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = if (profile.isDefault) {
            // Highlight the default profile with a tinted background.
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row: name + action buttons ───────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile name (or a placeholder if the user left it blank).
                Text(
                    text = profile.name.ifBlank { "Unnamed profile" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                // Default-star button
                IconButton(onClick = onSetDefault) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (profile.isDefault) "Default profile" else "Set as default",
                        tint = if (profile.isDefault) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Edit button
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete profile",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Masked URL + badges ─────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = maskUrl(profile.url),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // Protocol badge
                Text(
                    text = profile.protocol.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                // Codec badge (only if non-default)
                if (profile.videoCodec != VideoCodec.H264) {
                    Text(
                        text = profile.videoCodec.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Show "Default" badge if this is the active profile.
            if (profile.isDefault) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Edit Profile Dialog ─────────────────────────────────────────

/**
 * Full-screen-style dialog for editing or creating a profile.
 * Shows/hides fields based on the detected protocol (RTMP/RTMPS vs SRT).
 */
@Composable
private fun EditProfileDialog(
    profile: EndpointProfile,
    onSave: (EndpointProfile) -> Unit,
    onDismiss: () -> Unit
) {
    // Local state for each editable field. Initialized from the profile.
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var url by remember(profile.id) { mutableStateOf(profile.url) }
    var streamKey by remember(profile.id) { mutableStateOf(profile.streamKey) }
    var username by remember(profile.id) { mutableStateOf(profile.username ?: "") }
    var password by remember(profile.id) { mutableStateOf(profile.password ?: "") }

    // SRT-specific fields
    var srtPassphrase by remember(profile.id) { mutableStateOf(profile.srtPassphrase ?: "") }
    var srtKeyLength by remember(profile.id) { mutableStateOf(profile.srtKeyLength) }
    var srtLatencyMs by remember(profile.id) { mutableStateOf(profile.srtLatencyMs.toString()) }
    var srtMode by remember(profile.id) { mutableStateOf(profile.srtMode) }
    var srtStreamId by remember(profile.id) { mutableStateOf(profile.srtStreamId ?: "") }
    var videoCodec by remember(profile.id) { mutableStateOf(profile.videoCodec) }

    // Toggle to reveal/hide the stream key and password fields.
    var streamKeyVisible by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var passphraseVisible by remember { mutableStateOf(false) }

    val isNewProfile = profile.name.isBlank() && profile.url.isBlank()
    val detectedProtocol = StreamProtocol.fromUrl(url)
    val isSrt = detectedProtocol == StreamProtocol.SRT

    // Filter codecs: AV1 not available for SRT
    val availableCodecs = if (isSrt) {
        VideoCodec.entries.filter { it.supportsSrt() }
    } else {
        VideoCodec.entries.toList()
    }
    // Auto-correct codec if user switches to SRT with AV1 selected
    if (isSrt && !videoCodec.supportsSrt()) {
        videoCodec = VideoCodec.H264
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNewProfile) "New Endpoint" else "Edit Endpoint")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // ── Name ────────────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g., My YouTube Channel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Server URL ──────────────────────────────────
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = {
                        Text(
                            if (isSrt) "srt://ingest.example.com:9000"
                            else "rtmp://ingest.example.com/live"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Protocol badge
                if (url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Protocol: ${detectedProtocol.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Video Codec picker ──────────────────────────
                Text(
                    text = "Video Codec",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCodecs.forEach { codec ->
                        TextButton(
                            onClick = { videoCodec = codec },
                        ) {
                            Text(
                                text = codec.displayName(),
                                color = if (videoCodec == codec) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                if (videoCodec.isEnhancedRtmp() && !isSrt) {
                    Text(
                        text = "⚠ Enhanced RTMP — ensure your server supports ${videoCodec.displayName()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isSrt) {
                    // ── RTMP/RTMPS-specific fields ──────────────
                    OutlinedTextField(
                        value = streamKey,
                        onValueChange = { streamKey = it },
                        label = { Text("Stream Key") },
                        singleLine = true,
                        visualTransformation = if (streamKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { streamKeyVisible = !streamKeyVisible }) {
                                Text(if (streamKeyVisible) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Optional Authentication",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // ── SRT-specific fields ─────────────────────

                    // Passphrase (encrypted credential)
                    OutlinedTextField(
                        value = srtPassphrase,
                        onValueChange = { srtPassphrase = it },
                        label = { Text("Passphrase (optional)") },
                        placeholder = { Text("10–79 characters for AES encryption") },
                        singleLine = true,
                        visualTransformation = if (passphraseVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Text(if (passphraseVisible) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // AES key length (only shown when passphrase is set)
                    if (srtPassphrase.isNotBlank()) {
                        Text(
                            text = "Encryption Key Length",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SrtKeyLength.entries.forEach { keyLen ->
                                TextButton(onClick = { srtKeyLength = keyLen }) {
                                    Text(
                                        text = keyLen.displayName(),
                                        color = if (srtKeyLength == keyLen) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Latency
                    OutlinedTextField(
                        value = srtLatencyMs,
                        onValueChange = { srtLatencyMs = it },
                        label = { Text("Latency (ms)") },
                        placeholder = { Text("120") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // SRT Mode picker
                    Text(
                        text = "Connection Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SrtMode.entries.forEach { mode ->
                            TextButton(onClick = { srtMode = mode }) {
                                Text(
                                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (srtMode == mode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    if (srtMode != SrtMode.CALLER) {
                        Text(
                            text = "⚠ ${srtMode.name} mode is experimental in RootEncoder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stream ID
                    OutlinedTextField(
                        value = srtStreamId,
                        onValueChange = { srtStreamId = it },
                        label = { Text("Stream ID (optional)") },
                        placeholder = { Text("#!::m=publish,r=live/stream") },
                        supportingText = {
                            Text("SRT Access Control format. Defaults to publish mode if blank.")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        // Save button — constructs an updated profile from local state.
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name.trim(),
                            url = url.trim(),
                            streamKey = if (isSrt) "" else streamKey.trim(),
                            username = if (isSrt) null else username.trim().ifBlank { null },
                            password = if (isSrt) null else password.trim().ifBlank { null },
                            videoCodec = videoCodec,
                            srtPassphrase = if (isSrt) srtPassphrase.trim().ifBlank { null } else null,
                            srtKeyLength = if (isSrt) srtKeyLength else SrtKeyLength.AES_128,
                            srtLatencyMs = if (isSrt) (srtLatencyMs.toIntOrNull() ?: 120) else 120,
                            srtMode = if (isSrt) srtMode else SrtMode.CALLER,
                            srtStreamId = if (isSrt) srtStreamId.trim().ifBlank { null } else null,
                        )
                    )
                },
                // Disable Save unless required fields are filled.
                // SRT: only name + url required. RTMP: name + url + streamKey.
                enabled = name.isNotBlank() && url.isNotBlank() && (isSrt || streamKey.isNotBlank())
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Delete Confirmation Dialog ──────────────────────────────────

/** Simple confirmation dialog before deleting a profile. */
@Composable
private fun DeleteConfirmationDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile") },
        text = {
            Text("Are you sure you want to delete \"$profileName\"? This cannot be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Empty State ─────────────────────────────────────────────────

/** Shown when there are no saved profiles yet. */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Endpoints Configured",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add your first streaming destination.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────

/**
 * Masks a streaming URL for display in the profile list.
 * Shows the scheme and host but masks the path/params.
 */
private fun maskUrl(url: String): String {
    if (url.isBlank()) return ""
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return url

    val afterScheme = url.substring(schemeEnd + 3)
    // For SRT, mask query params (which may contain passphrase)
    val queryStart = afterScheme.indexOf('?')
    if (queryStart >= 0) {
        return url.substring(0, schemeEnd + 3 + queryStart) + "?****"
    }
    // For RTMP, mask path after host
    val pathStart = afterScheme.indexOf('/')
    return if (pathStart >= 0) {
        url.substring(0, schemeEnd + 3 + pathStart) + "/****"
    } else {
        url
    }
}
