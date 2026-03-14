package com.port80.app.di

import com.port80.app.service.ExponentialBackoffReconnectPolicy
import com.port80.app.service.ReconnectPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamModule {

    @Provides
    @Singleton
    fun provideReconnectPolicy(): ReconnectPolicy {
        return ExponentialBackoffReconnectPolicy()
    }
}
