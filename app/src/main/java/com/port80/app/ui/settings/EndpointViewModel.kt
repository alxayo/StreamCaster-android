package com.port80.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.model.EndpointProfile
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
    private val profileRepository: EndpointProfileRepository
) : ViewModel() {

    /** All saved profiles, observed by the UI. */
    val profiles: StateFlow<List<EndpointProfile>> = profileRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Currently selected profile for editing. */
    private val _editingProfile = MutableStateFlow<EndpointProfile?>(null)
    val editingProfile: StateFlow<EndpointProfile?> = _editingProfile.asStateFlow()

    fun selectProfile(profile: EndpointProfile) {
        _editingProfile.value = profile
    }

    fun newProfile() {
        _editingProfile.value = EndpointProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            rtmpUrl = "",
            streamKey = ""
        )
    }

    fun saveProfile(profile: EndpointProfile) {
        viewModelScope.launch {
            profileRepository.save(profile)
            _editingProfile.value = null
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
