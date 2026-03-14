package com.port80.app.di

import com.port80.app.data.EncryptedEndpointProfileRepository
import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.MetricsCollector
import com.port80.app.data.SimpleMetricsCollector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindEndpointProfileRepo(
        impl: EncryptedEndpointProfileRepository
    ): EndpointProfileRepository

    @Binds
    @Singleton
    abstract fun bindMetricsCollector(
        impl: SimpleMetricsCollector
    ): MetricsCollector
}
