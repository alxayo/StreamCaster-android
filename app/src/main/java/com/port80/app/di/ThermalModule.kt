package com.port80.app.di

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.port80.app.thermal.BatteryTempMonitor
import com.port80.app.thermal.ThermalMonitor
import com.port80.app.thermal.ThermalStatusMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThermalModule {

    @Provides
    @Singleton
    fun provideThermalMonitor(
        @ApplicationContext context: Context,
        powerManager: PowerManager
    ): ThermalMonitor {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThermalStatusMonitor(powerManager)
        } else {
            BatteryTempMonitor(context)
        }
    }
}
