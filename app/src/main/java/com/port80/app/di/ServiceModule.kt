package com.port80.app.di

import com.port80.app.service.ActiveStreamStateProvider
import com.port80.app.service.EncoderBridge
import com.port80.app.service.MutableActiveStreamStateProvider
import com.port80.app.service.NotificationController
import com.port80.app.service.ProtocolAwareBridgeFactory
import com.port80.app.service.StreamNotificationController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindNotificationController(
        impl: StreamNotificationController
    ): NotificationController

    @Binds
    @Singleton
    abstract fun bindEncoderBridgeFactory(
        impl: ProtocolAwareBridgeFactory
    ): EncoderBridge.Factory

    @Binds
    @Singleton
    abstract fun bindActiveStreamStateProvider(
        impl: MutableActiveStreamStateProvider
    ): ActiveStreamStateProvider
}
