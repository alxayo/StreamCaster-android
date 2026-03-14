package com.port80.app.di

import com.port80.app.camera.Camera2CapabilityQuery
import com.port80.app.camera.DeviceCapabilityQuery
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds [DeviceCapabilityQuery] to its Camera2-based implementation.
 *
 * Uses [@Binds] instead of [@Provides] because [Camera2CapabilityQuery] already has
 * an @Inject constructor — Hilt just needs to know which concrete class to use
 * when someone requests the [DeviceCapabilityQuery] interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindDeviceCapabilityQuery(
        impl: Camera2CapabilityQuery
    ): DeviceCapabilityQuery
}
