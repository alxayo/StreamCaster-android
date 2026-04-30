package com.port80.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.qr.QrEndpointImportParser
import com.port80.app.data.qr.QrEndpointParseResult
import com.port80.app.service.ActiveStreamStateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing RTMP endpoint profiles.
 * Handles CRUD operations for saved streaming destinations.
 */
@HiltViewModel
class EndpointViewModel @Inject constructor(
    private val profileRepository: EndpointProfileRepository,
    private val activeStreamStateProvider: ActiveStreamStateProvider
) : ViewModel() {

    /** All saved profiles, observed by the UI. */
    val profiles: StateFlow<List<EndpointProfile>> = profileRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Currently selected profile for editing. */
    private val _editingProfile = MutableStateFlow<EndpointProfile?>(null)
    val editingProfile: StateFlow<EndpointProfile?> = _editingProfile.asStateFlow()

    /** True when StreamingService owns the camera and QR scanning must wait. */
    val isStreamActive: StateFlow<Boolean> = activeStreamStateProvider.isStreamActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Dialog state for QR-import decisions that need a user answer. */
    private val _importDialogState = MutableStateFlow<EndpointImportDialogState>(EndpointImportDialogState.None)
    val importDialogState: StateFlow<EndpointImportDialogState> = _importDialogState.asStateFlow()

    /** Holds a parsed profile while the UI asks duplicate/default questions. */
    private var pendingImportedProfile: EndpointProfile? = null

    fun selectProfile(profile: EndpointProfile) {
        _editingProfile.value = profile
    }

    fun dismissEdit() {
        _editingProfile.value = null
    }

    fun newProfile() {
        _editingProfile.value = EndpointProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            url = "",
            streamKey = ""
        )
    }

    fun saveProfile(profile: EndpointProfile) {
        viewModelScope.launch {
            profileRepository.save(profile)
            if (profile.isDefault) {
                // The repository stores the default profile as a separate ID.
                // Saving the profile alone is not enough to mark it default.
                profileRepository.setDefault(profile.id)
            }
            _editingProfile.value = null
        }
    }

    /** Parse a raw QR result and move it into confirmation/editing UI. */
    fun onQrScanned(rawText: String) {
        if (activeStreamStateProvider.isStreamActive.value) {
            _importDialogState.value = EndpointImportDialogState.BlockedStreamActive
            return
        }

        when (val result = QrEndpointImportParser.parse(rawText)) {
            is QrEndpointParseResult.Invalid -> {
                _importDialogState.value = EndpointImportDialogState.InvalidPayload(result.reason)
            }
            is QrEndpointParseResult.Success -> {
                prepareImportedProfile(
                    result.candidate.toProfile(
                        id = UUID.randomUUID().toString(),
                        isDefault = result.candidate.requestedDefault
                    )
                )
            }
        }
    }

    /** Clear whichever QR-import dialog is currently visible. */
    fun dismissImportDialog() {
        _importDialogState.value = EndpointImportDialogState.None
        pendingImportedProfile = null
    }

    /** User approved updating an existing endpoint with scanned values. */
    fun confirmDuplicateUpdate() {
        val profile = pendingImportedProfile ?: return
        _importDialogState.value = EndpointImportDialogState.None
        if (profile.isDefault) {
            _importDialogState.value = EndpointImportDialogState.ConfirmDefault
        } else {
            _editingProfile.value = profile
            pendingImportedProfile = null
        }
    }

    /** User answered whether the imported profile should become the default. */
    fun confirmDefaultImport(applyAsDefault: Boolean) {
        val profile = pendingImportedProfile ?: return
        _importDialogState.value = EndpointImportDialogState.None
        pendingImportedProfile = null
        _editingProfile.value = profile.copy(isDefault = applyAsDefault)
    }

    private fun prepareImportedProfile(importedProfile: EndpointProfile) {
        val duplicate = findDuplicate(importedProfile)
        val profileForEditor = if (duplicate != null) {
            // Preserve identity/default status from the saved endpoint. The QR
            // code can update editable fields only after the user confirms.
            importedProfile.copy(id = duplicate.id, isDefault = duplicate.isDefault || importedProfile.isDefault)
        } else {
            importedProfile
        }

        pendingImportedProfile = profileForEditor
        _importDialogState.value = when {
            duplicate != null -> EndpointImportDialogState.ConfirmDuplicateUpdate(
                existingName = duplicate.name.ifBlank { "Unnamed profile" }
            )
            profileForEditor.isDefault -> EndpointImportDialogState.ConfirmDefault
            else -> {
                pendingImportedProfile = null
                _editingProfile.value = profileForEditor
                EndpointImportDialogState.None
            }
        }
    }

    private fun findDuplicate(importedProfile: EndpointProfile): EndpointProfile? {
        val importedKey = QrEndpointImportParser.duplicateKey(importedProfile)
        return profiles.value.firstOrNull { saved ->
            QrEndpointImportParser.duplicateKey(saved) == importedKey
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileRepository.delete(id)
            if (_editingProfile.value?.id == id) {
                _editingProfile.value = null
            }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch {
            profileRepository.setDefault(id)
        }
    }
}

/** Dialogs the endpoint screen may show while importing a scanned endpoint. */
sealed interface EndpointImportDialogState {
    data object None : EndpointImportDialogState
    data object BlockedStreamActive : EndpointImportDialogState
    data object ConfirmDefault : EndpointImportDialogState
    data class InvalidPayload(val reason: String) : EndpointImportDialogState
    data class ConfirmDuplicateUpdate(val existingName: String) : EndpointImportDialogState
}
