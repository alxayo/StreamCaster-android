package com.port80.app.ui.settings

import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.model.EndpointProfile
import com.port80.app.service.ActiveStreamStateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the QR import decisions without involving Compose or CameraX. */
@OptIn(ExperimentalCoroutinesApi::class)
class EndpointViewModelQrImportTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // ViewModel uses viewModelScope, which needs a Main dispatcher in JVM tests.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan is blocked while service owns camera`() = runTest(dispatcher) {
        val viewModel = EndpointViewModel(
            profileRepository = FakeEndpointProfileRepository(),
            activeStreamStateProvider = FakeActiveStreamStateProvider(active = true)
        )

        viewModel.onQrScanned("rtmp://host/live/key")

        assertEquals(EndpointImportDialogState.BlockedStreamActive, viewModel.importDialogState.value)
    }

    @Test
    fun `duplicate scan asks before updating existing endpoint`() = runTest(dispatcher) {
        val existing = EndpointProfile(
            id = "existing-id",
            name = "Existing",
            url = "rtmp://host/live",
            streamKey = "key"
        )
        val viewModel = EndpointViewModel(
            profileRepository = FakeEndpointProfileRepository(listOf(existing)),
            activeStreamStateProvider = FakeActiveStreamStateProvider(active = false)
        )
        advanceUntilIdle()

        viewModel.onQrScanned("rtmp://host/live/key")

        assertTrue(viewModel.importDialogState.value is EndpointImportDialogState.ConfirmDuplicateUpdate)
        viewModel.confirmDuplicateUpdate()
        assertEquals("existing-id", viewModel.editingProfile.value?.id)
    }

    @Test
    fun `default request requires confirmation before save sets default`() = runTest(dispatcher) {
        val repository = FakeEndpointProfileRepository()
        val viewModel = EndpointViewModel(
            profileRepository = repository,
            activeStreamStateProvider = FakeActiveStreamStateProvider(active = false)
        )
        val rawJson = """
            {"v":1,"name":"Default QR","url":"rtmp://host/live","streamKey":"key","isDefault":true}
        """.trimIndent()

        viewModel.onQrScanned(rawJson)
        assertEquals(EndpointImportDialogState.ConfirmDefault, viewModel.importDialogState.value)

        viewModel.confirmDefaultImport(applyAsDefault = true)
        val profile = requireNotNull(viewModel.editingProfile.value)
        viewModel.saveProfile(profile)
        advanceUntilIdle()

        assertEquals(profile.id, repository.defaultId)
    }

    /** Minimal in-memory repository that behaves like the encrypted repo for tests. */
    private class FakeEndpointProfileRepository(
        initialProfiles: List<EndpointProfile> = emptyList()
    ) : EndpointProfileRepository {
        private val profilesFlow = MutableStateFlow(initialProfiles)
        var defaultId: String? = initialProfiles.firstOrNull { it.isDefault }?.id

        override fun getAll(): Flow<List<EndpointProfile>> = profilesFlow

        override suspend fun getById(id: String): EndpointProfile? =
            profilesFlow.value.firstOrNull { it.id == id }

        override suspend fun getDefault(): EndpointProfile? =
            defaultId?.let { getById(it) }

        override suspend fun save(profile: EndpointProfile) {
            profilesFlow.value = profilesFlow.value
                .filterNot { it.id == profile.id }
                .plus(profile)
        }

        override suspend fun delete(id: String) {
            profilesFlow.value = profilesFlow.value.filterNot { it.id == id }
            if (defaultId == id) defaultId = null
        }

        override suspend fun setDefault(id: String) {
            defaultId = id
        }

        override suspend fun isKeystoreAvailable(): Boolean = true
    }

    /** Test double for the service-owned active camera flag. */
    private class FakeActiveStreamStateProvider(active: Boolean) : ActiveStreamStateProvider {
        override val isStreamActive: StateFlow<Boolean> = MutableStateFlow(active)
    }
}